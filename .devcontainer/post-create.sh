#!/usr/bin/env bash
set -euo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YEL='\033[0;33m'
NC='\033[0m'

log_info()    { echo -e "${BLUE}[INFO]${NC} $1"; }
log_success() { echo -e "${GREEN}[OK]${NC} $1"; }
log_warn()    { echo -e "${YEL}[WARN]${NC} $1"; }
log_error()   { echo -e "${RED}[ERROR]${NC} $1"; }

echo
echo "=================================================="
echo "  Vaccination Domain POC - Dev Environment"
echo "=================================================="
echo

# --- AI CLIs (idempotent) --------------------------------------------------

if command -v claude >/dev/null 2>&1; then
  log_info "Claude Code already present"
else
  log_info "Installing Claude Code..."
  curl -fsSL https://claude.ai/install.sh | bash
  log_success "Claude Code installed"
fi

if command -v gemini >/dev/null 2>&1; then
  log_info "Gemini CLI already present"
else
  log_info "Installing Gemini CLI..."
  npm install -g @google/gemini-cli
  log_success "Gemini CLI installed"
fi

if command -v codex >/dev/null 2>&1; then
  log_info "Codex CLI already present"
else
  log_info "Installing Codex CLI..."
  npm install -g @openai/codex
  log_success "Codex CLI installed"
fi

# --- SDKMAN + Kotlin -------------------------------------------------------
# The java:1 feature installs SDKMAN system-wide at /usr/local/sdkman
# (group-writable to vscode). It does not add a shell rc snippet — we do
# that here so `sdk` is available in interactive shells.

SDKMAN_INIT="/usr/local/sdkman/bin/sdkman-init.sh"
if [ ! -s "$SDKMAN_INIT" ]; then
  log_error "SDKMAN missing at $SDKMAN_INIT (expected from java:1 feature)"
  exit 1
fi

SDKMAN_SNIPPET='export SDKMAN_DIR="/usr/local/sdkman"
[[ -s "/usr/local/sdkman/bin/sdkman-init.sh" ]] && source "/usr/local/sdkman/bin/sdkman-init.sh"'
for rc in "$HOME/.bashrc" "$HOME/.zshrc"; do
  touch "$rc"
  if ! grep -q "sdkman-init.sh" "$rc"; then
    printf '\n# SDKMAN\n%s\n' "$SDKMAN_SNIPPET" >> "$rc"
  fi
done

export SDKMAN_DIR="/usr/local/sdkman"
# SDKMAN's init + install scripts reference unguarded vars (ZSH_VERSION,
# positional params, etc.), so they trip `set -u`. Drop nounset for the
# whole SDKMAN section, then restore.
set +u
# shellcheck disable=SC1091
source "$SDKMAN_INIT"

# Pin Kotlin to a known-good version so the README claim stays accurate
# across rebuilds. Bump intentionally, not via "latest".
KOTLIN_VERSION="2.3.21"
if [ -d "$SDKMAN_DIR/candidates/kotlin/$KOTLIN_VERSION" ]; then
  log_info "Kotlin $KOTLIN_VERSION already installed via SDKMAN"
else
  log_info "Installing Kotlin $KOTLIN_VERSION via SDKMAN..."
  sdk install kotlin "$KOTLIN_VERSION" < /dev/null || log_error "Kotlin install failed"
fi
set -u

# --- Summary ---------------------------------------------------------------

echo
echo "=================================================="
echo "  Toolchain"
echo "=================================================="
printf "  Java:    %s\n"  "$(java -version 2>&1 | head -1)"
printf "  Maven:   %s\n"  "$(mvn -v 2>&1 | head -1)"
printf "  Gradle:  %s\n"  "$(gradle -v 2>&1 | grep -m1 '^Gradle ' || echo 'not found')"
printf "  Kotlin:  %s\n"  "$(kotlin -version 2>&1 | head -1 || echo 'not found')"
printf "  Node:    %s\n"  "$(node --version 2>&1)"
printf "  Python:  %s\n"  "$(python --version 2>&1)"
printf "  Docker:  %s\n"  "$(docker --version 2>&1 || echo 'host daemon not reachable')"
printf "  gh:      %s\n"  "$(gh --version 2>&1 | head -1)"
printf "  Claude:  %s\n"  "$(claude --version 2>&1 | head -1 || echo 'not found')"
printf "  Gemini:  %s\n"  "$(gemini --version 2>&1 | head -1 || echo 'not found')"
printf "  Codex:   %s\n"  "$(codex --version 2>&1 | head -1 || echo 'not found')"
echo
log_info "AI CLI first-time auth:"
echo "  - Claude:  run 'claude login' or set ANTHROPIC_API_KEY"
echo "  - Gemini:  run 'gemini' (OAuth flow) or set GEMINI_API_KEY"
echo "  - Codex:   set OPENAI_API_KEY in your shell"
echo
