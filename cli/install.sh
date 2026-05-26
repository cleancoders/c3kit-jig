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

err()  { printf '\033[31m✗ %s\033[0m\n' "$*" >&2; }
info() { printf '\033[34m▸ %s\033[0m\n' "$*"; }
ok()   { printf '\033[32m✓ %s\033[0m\n' "$*"; }
dim()  { printf '\033[2m%s\033[0m\n' "$*"; }
bold() { printf '\033[1m%s\033[0m\n' "$*"; }

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

if [[ $have_bb -eq 0 ]]; then
  info "installing babashka via official installer"
  bash <(curl -fsSL https://raw.githubusercontent.com/babashka/babashka/master/install)
  ok "babashka installed"
fi

install_gum() {
  local os arch m tag ver url tmp bin
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
  tag=$(curl -fsSL "https://api.github.com/repos/charmbracelet/gum/releases/latest" \
          | grep -m1 '"tag_name"' | cut -d'"' -f4) || return 1
  ver="${tag#v}"
  url="https://github.com/charmbracelet/gum/releases/download/${tag}/gum_${ver}_${os}_${arch}.tar.gz"
  tmp="$(mktemp -d)"
  trap 'rm -rf "$tmp"' RETURN
  info "downloading gum $tag for ${os}/${arch}"
  if ! curl -fsSL "$url" -o "$tmp/gum.tgz"; then
    info "(gum download failed; CLI will use fallback UI)"; return 1
  fi
  (cd "$tmp" && tar -xzf gum.tgz) || { info "(gum extract failed)"; return 1; }
  bin="$(find "$tmp" -type f -name gum | head -n1)"
  if [[ -z "$bin" ]]; then
    info "(gum binary not found in archive)"; return 1
  fi
  install -m 0755 "$bin" "$INSTALL_DIR/gum"
  ok "installed gum $tag → $INSTALL_DIR/gum"
}

if [[ $have_gum -eq 0 ]]; then
  install_gum || info "continuing without gum — CLI will use the numbered fallback UI"
fi

if [[ -n "${C3KIT_LOCAL_BB:-}" ]]; then
  info "installing $BIN_NAME from local build: $C3KIT_LOCAL_BB"
  install -m 0755 "$C3KIT_LOCAL_BB" "$INSTALL_DIR/$BIN_NAME"
  TAG="(local)"
else
  info "looking up latest $BIN_NAME release"
  LATEST_URL="https://api.github.com/repos/$REPO/releases/latest"
  TAG=$(curl -fsSL "$LATEST_URL" | grep -m1 'tag_name' | cut -d'"' -f4)
  DL_URL="https://github.com/$REPO/releases/download/$TAG/$BIN_NAME.bb"
  info "downloading $BIN_NAME $TAG"
  curl -fsSL "$DL_URL" -o "$INSTALL_DIR/$BIN_NAME"
  chmod +x "$INSTALL_DIR/$BIN_NAME"
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
