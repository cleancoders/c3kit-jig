#!/usr/bin/env bash
set -euo pipefail

REPO="cleancoders/c3kit-starter"
INSTALL_DIR="${C3KIT_BIN_DIR:-$HOME/.c3kit/bin}"
BIN_NAME="c3kit-create"

err() { printf '\033[31m✗ %s\033[0m\n' "$*" >&2; }
info() { printf '\033[34m▸ %s\033[0m\n' "$*"; }
ok() { printf '\033[32m✓ %s\033[0m\n' "$*"; }

uname_s="$(uname -s)"
case "$uname_s" in
  Darwin|Linux) ;;
  *) err "Unsupported OS: $uname_s (only macOS / Linux / WSL)"; exit 1 ;;
esac

if ! command -v git >/dev/null 2>&1; then
  err "git is required. See https://git-scm.com/downloads"
  exit 1
fi

if ! command -v bb >/dev/null 2>&1; then
  info "babashka not found — installing via official one-liner"
  bash <(curl -fsSL https://raw.githubusercontent.com/babashka/babashka/master/install)
fi

if ! command -v java >/dev/null 2>&1; then
  err "(warning) java not found — needed for the projects you'll scaffold, not for the CLI"
fi

install_gum() {
  local os arch m tag ver url tmp
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
  local bin
  bin="$(find "$tmp" -type f -name gum | head -n1)"
  if [[ -z "$bin" ]]; then
    info "(gum binary not found in archive)"; return 1
  fi
  install -m 0755 "$bin" "$INSTALL_DIR/gum"
  ok "installed gum $tag → $INSTALL_DIR/gum"
}

mkdir -p "$INSTALL_DIR"

if ! command -v gum >/dev/null 2>&1; then
  info "gum not found — recommended for the feature checkbox UI (CLI falls back to a numbered yn loop without it)"
  install_gum || true
fi

info "downloading latest c3kit-create"
LATEST_URL="https://api.github.com/repos/$REPO/releases/latest"
TAG=$(curl -fsSL "$LATEST_URL" | grep -m1 'tag_name' | cut -d'"' -f4)
DL_URL="https://github.com/$REPO/releases/download/$TAG/$BIN_NAME.bb"
curl -fsSL "$DL_URL" -o "$INSTALL_DIR/$BIN_NAME"
chmod +x "$INSTALL_DIR/$BIN_NAME"

# Ensure on PATH idempotently
case ":$PATH:" in
  *":$INSTALL_DIR:"*) ;;
  *)
    PROFILE=""
    case "${SHELL:-}" in
      */zsh)  PROFILE="$HOME/.zshrc" ;;
      */bash) PROFILE="$HOME/.bashrc" ;;
      */fish) PROFILE="$HOME/.config/fish/config.fish" ;;
    esac
    if [[ -n "$PROFILE" ]]; then
      LINE="export PATH=\"$INSTALL_DIR:\$PATH\""
      grep -qxF "$LINE" "$PROFILE" 2>/dev/null || echo "$LINE" >> "$PROFILE"
      info "added PATH export to $PROFILE — open a new shell or 'source $PROFILE'"
    else
      info "add $INSTALL_DIR to your PATH manually"
    fi
    ;;
esac

ok "installed $BIN_NAME $TAG → $INSTALL_DIR/$BIN_NAME"
"$INSTALL_DIR/$BIN_NAME" --version
