#!/usr/bin/env bash
set -euo pipefail

REPO="cleancoders/c3kit-jig"
INSTALL_DIR="${C3KIT_BIN_DIR:-$HOME/.c3kit/bin}"
BIN_NAME="c3kit-jig"
ASSUME_YES="${C3KIT_YES:-0}"

for arg in "$@"; do
  case "$arg" in
    -y|--yes) ASSUME_YES=1 ;;
    -h|--help)
      cat <<EOF
c3kit-jig installer

Usage: install.sh [-y|--yes]

Env:
  C3KIT_BIN_DIR   Install dir (default: \$HOME/.c3kit/bin)
  C3KIT_YES=1     Skip confirmation prompt
  C3KIT_LOCAL_BB  Install from this local uberscript instead of GitHub release
EOF
      exit 0
      ;;
  esac
done

# >>> testable >>>
err()  { printf '\033[31m✗ %s\033[0m\n' "$*" >&2; }
info() { printf '\033[34m▸ %s\033[0m\n' "$*"; }
ok()   { printf '\033[32m✓ %s\033[0m\n' "$*"; }
dim()  { printf '\033[2m%s\033[0m\n' "$*"; }
bold() { printf '\033[1m%s\033[0m\n' "$*"; }

# sha256 of a file, portable across Linux (sha256sum) and macOS (shasum).
sha256() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | cut -d' ' -f1
  elif command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$1" | cut -d' ' -f1
  else
    err "neither 'sha256sum' nor 'shasum' on PATH — cannot verify downloads"
    exit 1
  fi
}

# Every temp dir handed out below, so cleanup can sweep them. Each function
# still drops its own dir via `trap … RETURN`, which covers normal and failing
# returns; this registry is the backstop for the paths RETURN never sees — a
# function that exits the script outright (sha256 with no digest tool) and a
# Ctrl-C mid-download.
TEMP_DIRS=()

# Creates a temp dir and returns it in $REPLY — deliberately not on stdout,
# because `d="$(make_temp_dir)"` would run the registration in a subshell and
# the parent's TEMP_DIRS would stay empty, silently disabling cleanup.
make_temp_dir() {
  # Explicit template: BSD mktemp ignores $TMPDIR without one, and the c3kit-jig
  # prefix makes any dir that does survive obvious in /tmp.
  REPLY="$(mktemp -d "${TMPDIR:-/tmp}/c3kit-jig.XXXXXX")" || return 1
  TEMP_DIRS+=("$REPLY")
}

cleanup() {
  local d
  # ${arr+…} guard: bash 3.2 (macOS) treats an empty array as unbound under -u.
  for d in ${TEMP_DIRS+"${TEMP_DIRS[@]}"}; do
    if [[ -n "$d" && -d "$d" ]]; then rm -rf "$d"; fi
  done
  TEMP_DIRS=()
}

trap 'cleanup' EXIT
trap 'cleanup; exit 130' INT
trap 'cleanup; exit 143' TERM

# First file named $2 under $1. Avoids `find … | head -1`: head exits on the
# first line, find then dies of EPIPE, and pipefail turns that into a failed
# assignment — the same defect class as the tag_name pipeline below.
find_first() {
  local hits
  hits="$(find "$1" -type f -name "$2")" || return 1
  [[ -n "$hits" ]] || return 1
  printf '%s\n' "${hits%%$'\n'*}"
}

# Download $1 to $2, then refuse it unless its sha256 equals $3. Returns
# non-zero rather than exiting so each caller picks its own policy: fatal for
# the CLI and babashka, best-effort for the optional gum UI.
# An empty $3 is a hard failure — a checksums file we could not parse must not
# read as "nothing to compare, so it passes".
fetch_verified() {
  local url="$1" dest="$2" expected="$3" actual
  if ! curl -fsSL "$url" -o "$dest"; then
    err "download failed: $url"
    return 1
  fi
  if [[ -z "$expected" ]]; then
    err "no expected sha256 for $url — refusing to trust the download"
    return 1
  fi
  actual="$(sha256 "$dest")"
  if [[ "$actual" != "$expected" ]]; then
    err "checksum mismatch for $url — refusing to use it"
    err "  expected: $expected"
    err "  actual:   $actual"
    return 1
  fi
}

# tag_name from the GitHub "latest release" payload at $1.
# Reads the whole body into a variable before parsing: piping curl straight into
# `grep -m1` lets grep exit on the first match, curl then dies of EPIPE (56),
# and pipefail turns that into a silent "no release found" — but only for
# payloads large enough to block on write, which made it look intermittent.
latest_tag() {
  local body
  body="$(curl -fsSL "$1")" || return 1
  printf '%s\n' "$body" \
    | sed -n 's/.*"tag_name"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' \
    | awk 'NR == 1 { print }'
}

# Digest of file $2 as recorded in a `<sha256>  <filename>` checksums file $1.
# Matches the filename field exactly: a substring match would happily return
# the digest of gum_X_Linux_x86_64.tar.gz.sbom.json for the tarball itself.
checksum_for() {
  awk -v want="$2" '$2 == want { print $1; found = 1 } END { exit !found }' "$1"
}
# <<< testable <<<

# ─── detect phase (no side effects) ──────────────────────────────────────────

uname_s="$(uname -s)"
case "$uname_s" in
  Darwin|Linux) ;;
  *) err "Unsupported OS: $uname_s (only macOS / Linux / WSL)"; exit 1 ;;
esac

if ! command -v git >/dev/null 2>&1; then
  err "git is required. See https://git-scm.com/downloads"
  exit 1
fi

have_bb=0;   bb_path=""
have_java=0; java_path=""
have_gum=0;  gum_path=""
on_path=0

command -v bb   >/dev/null 2>&1 && { have_bb=1;   bb_path="$(command -v bb)"; }
command -v java >/dev/null 2>&1 && { have_java=1; java_path="$(command -v java)"; }
command -v gum  >/dev/null 2>&1 && { have_gum=1;  gum_path="$(command -v gum)"; }

case ":$PATH:" in
  *":$INSTALL_DIR:"*) on_path=1 ;;
esac

PROFILE=""
case "${SHELL:-}" in
  */zsh)  PROFILE="$HOME/.zshrc" ;;
  */bash) PROFILE="$HOME/.bashrc" ;;
  */fish) PROFILE="$HOME/.config/fish/config.fish" ;;
esac

if [[ -n "${C3KIT_LOCAL_BB:-}" ]]; then
  if [[ ! -f "$C3KIT_LOCAL_BB" ]]; then
    err "C3KIT_LOCAL_BB set but file not found: $C3KIT_LOCAL_BB"
    exit 1
  fi
  source_desc="local build: $C3KIT_LOCAL_BB"
else
  source_desc="latest GitHub release of $REPO"
fi

# ─── plan ────────────────────────────────────────────────────────────────────

echo
bold "c3kit-jig installer"
echo
echo "  OS:           $uname_s ($(uname -m))"
echo "  Install dir:  $INSTALL_DIR"
echo "  Source:       $source_desc"
echo
echo "Detected:"
if [[ $have_bb -eq 1 ]];   then echo "  ✓ babashka  ($bb_path)"; else echo "  ✗ babashka  (will install via official one-liner)"; fi
if [[ $have_java -eq 1 ]]; then echo "  ✓ java      ($java_path)"; else echo "  ⚠ java      (not found — needed for projects you scaffold, not the CLI)"; fi
if [[ $have_gum -eq 1 ]];  then echo "  ✓ gum       ($gum_path)"; else echo "  ✗ gum       (will install to $INSTALL_DIR/gum — feature checkbox UI; fallback if skipped)"; fi
echo
echo "Will perform:"
echo "  • Install $BIN_NAME → $INSTALL_DIR/$BIN_NAME"
[[ $have_bb -eq 0 ]]  && echo "  • Install babashka (bb) system-wide via https://raw.githubusercontent.com/babashka/babashka/master/install"
[[ $have_gum -eq 0 ]] && echo "  • Install gum → $INSTALL_DIR/gum (latest from charmbracelet/gum releases)"
if [[ $on_path -eq 0 ]]; then
  if [[ -n "$PROFILE" ]]; then
    echo "  • Append 'export PATH=\"$INSTALL_DIR:\$PATH\"' to $PROFILE"
  else
    echo "  • (Unknown shell — you'll need to add $INSTALL_DIR to PATH manually)"
  fi
else
  echo "  • $INSTALL_DIR already on PATH — no profile edit"
fi
echo

if [[ "$ASSUME_YES" != "1" ]]; then
  if [[ -r /dev/tty ]]; then
    printf 'Proceed? [y/N] '
    read -r reply </dev/tty || reply=""
  else
    err "Non-interactive shell and C3KIT_YES not set. Re-run with C3KIT_YES=1 or '-y' to confirm."
    exit 1
  fi
  case "$reply" in
    y|Y|yes|YES) ;;
    *) info "aborted"; exit 0 ;;
  esac
fi

# ─── execute phase ───────────────────────────────────────────────────────────

mkdir -p "$INSTALL_DIR"

# The official babashka installer, pinned to a commit and checksum-verified
# before it runs. Fetching `master` and piping straight into bash would execute
# whatever the server returns — a repointed branch or a compromised CDN gets
# arbitrary code on the user's machine. The commit pin makes the content
# immutable; the sha256 is a second gate in case raw.githubusercontent lies.
# To bump: fetch the new commit's `install`, run `shasum -a 256` on it, and
# update both constants together.
BB_INSTALLER_COMMIT="ac3a84703645d1549b1af5cb58423735c404f59c"  # 2025-12-06
BB_INSTALLER_SHA256="04952b6ead3cd23ae297d44d8ab7b6162708a35d00c07971b6a72448a500fdeb"

install_babashka() {
  local tmp script
  make_temp_dir || return 1
  tmp="$REPLY"
  trap 'rm -rf "$tmp"' RETURN
  script="$tmp/install-babashka"
  fetch_verified "https://raw.githubusercontent.com/babashka/babashka/${BB_INSTALLER_COMMIT}/install" \
                 "$script" "$BB_INSTALLER_SHA256" || return 1
  bash "$script"
}

if [[ $have_bb -eq 0 ]]; then
  info "installing babashka via official installer"
  install_babashka || { err "cannot continue without babashka"; exit 1; }
  ok "babashka installed"
fi

# >>> testable >>>
install_gum() {
  local os arch m tag ver file base tmp bin expected
  case "$uname_s" in
    Darwin) os="Darwin" ;;
    Linux)  os="Linux" ;;
    *) info "skip gum: unsupported OS"; return 1 ;;
  esac
  m="$(uname -m)"
  case "$m" in
    x86_64|amd64)  arch="x86_64" ;;
    arm64|aarch64) arch="arm64" ;;
    *) info "skip gum: unsupported arch '$m'"; return 1 ;;
  esac
  if ! command -v tar >/dev/null 2>&1; then
    info "skip gum: 'tar' not on PATH"; return 1
  fi
  info "looking up latest gum release"
  tag="$(latest_tag "https://api.github.com/repos/charmbracelet/gum/releases/latest")" || return 1
  if [[ -z "$tag" ]]; then
    info "(could not determine the latest gum release; CLI will use fallback UI)"; return 1
  fi
  ver="${tag#v}"
  file="gum_${ver}_${os}_${arch}.tar.gz"
  base="https://github.com/charmbracelet/gum/releases/download/${tag}"
  make_temp_dir || return 1
  tmp="$REPLY"
  trap 'rm -rf "$tmp"' RETURN
  info "downloading gum $tag for ${os}/${arch}"
  # Check the tarball against the release's own checksums.txt. Same origin as
  # the tarball, so this is not a defense against a compromised release — it
  # catches truncated/corrupted downloads and a single swapped asset. Signature
  # verification would need cosign, which we cannot assume is installed.
  if ! curl -fsSL "$base/checksums.txt" -o "$tmp/checksums.txt"; then
    info "(gum checksums.txt unavailable; CLI will use fallback UI)"; return 1
  fi
  expected="$(checksum_for "$tmp/checksums.txt" "$file" || true)"
  if ! fetch_verified "$base/$file" "$tmp/gum.tgz" "$expected"; then
    info "(gum download failed verification; CLI will use fallback UI)"; return 1
  fi
  (cd "$tmp" && tar -xzf gum.tgz) || { info "(gum extract failed)"; return 1; }
  if ! bin="$(find_first "$tmp" gum)"; then
    info "(gum binary not found in archive)"; return 1
  fi
  install -m 0755 "$bin" "$INSTALL_DIR/gum"
  ok "installed gum $tag → $INSTALL_DIR/gum"
}
# <<< testable <<<

if [[ $have_gum -eq 0 ]]; then
  install_gum || info "continuing without gum — CLI will use the numbered fallback UI"
fi

# >>> testable >>>
# Install the released uberscript, verified against the `<name>.bb.sha256`
# asset that release.yml publishes alongside it. Sets TAG for the caller.
# Downloads to a temp dir first so a rejected artifact never lands in
# $INSTALL_DIR, and fails closed when the digest asset is missing.
install_c3kit_jig() {
  local tmp expected
  info "looking up latest $BIN_NAME release"
  TAG="$(latest_tag "https://api.github.com/repos/$REPO/releases/latest")" || return 1
  if [[ -z "$TAG" ]]; then
    err "could not determine the latest $BIN_NAME release"
    return 1
  fi
  local base="https://github.com/$REPO/releases/download/$TAG"
  make_temp_dir || return 1
  tmp="$REPLY"
  trap 'rm -rf "$tmp"' RETURN
  info "downloading $BIN_NAME $TAG"
  if ! curl -fsSL "$base/$BIN_NAME.bb.sha256" -o "$tmp/expected.sha256"; then
    err "no published sha256 for $BIN_NAME $TAG — refusing to install unverified"
    return 1
  fi
  # release.yml writes a bare digest (`sha256sum | cut -d' ' -f1`), but tolerate
  # the `<digest>  <filename>` form in case that ever changes.
  expected="$(tr -d '\r' < "$tmp/expected.sha256" | awk 'NR == 1 { print $1 }')"
  fetch_verified "$base/$BIN_NAME.bb" "$tmp/$BIN_NAME.bb" "$expected" || return 1
  install -m 0755 "$tmp/$BIN_NAME.bb" "$INSTALL_DIR/$BIN_NAME"
}
# <<< testable <<<

if [[ -n "${C3KIT_LOCAL_BB:-}" ]]; then
  info "installing $BIN_NAME from local build: $C3KIT_LOCAL_BB"
  install -m 0755 "$C3KIT_LOCAL_BB" "$INSTALL_DIR/$BIN_NAME"
  TAG="(local)"
else
  install_c3kit_jig || { err "install aborted"; exit 1; }
fi
ok "installed $BIN_NAME $TAG → $INSTALL_DIR/$BIN_NAME"

if [[ $on_path -eq 0 ]]; then
  if [[ -n "$PROFILE" ]]; then
    LINE="export PATH=\"$INSTALL_DIR:\$PATH\""
    if grep -qxF "$LINE" "$PROFILE" 2>/dev/null; then
      ok "PATH already configured in $PROFILE"
    else
      echo "$LINE" >> "$PROFILE"
      ok "added PATH export to $PROFILE"
      info "open a new shell or run: source $PROFILE"
    fi
  else
    info "add $INSTALL_DIR to your PATH manually"
  fi
fi

echo
"$INSTALL_DIR/$BIN_NAME" --version
ok "done"
