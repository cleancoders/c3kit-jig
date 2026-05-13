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

mkdir -p "$INSTALL_DIR"

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
