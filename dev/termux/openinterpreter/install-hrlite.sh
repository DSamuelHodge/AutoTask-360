#!/data/data/com.termux/files/usr/bin/bash
# Copy the hrlite shim + compiled binary + watch.sh from shared storage
# into Termux home. Run this once inside Termux after an adb push of
# /sdcard/Download/hrlite.real (and friends).
set -eu
HOME="${HOME:-/data/data/com.termux/files/home}"
SRC="${1:-/sdcard/Download}"
mkdir -p "$HOME/autotask/bin"
cp "$SRC/hrlite.shim" "$HOME/autotask/bin/hrlite"
cp "$SRC/hrlite.real" "$HOME/autotask/bin/hrlite.real"
cp "$SRC/watch.sh" "$HOME/autotask/watch.sh"
chmod 755 "$HOME/autotask/bin/hrlite" "$HOME/autotask/bin/hrlite.real" "$HOME/autotask/watch.sh"
# Smoke: shim must exec the real binary.
ver="$("$HOME/autotask/bin/hrlite" --version || true)"
echo "installed $ver at $HOME/autotask/bin/hrlite"
if tmux has-session -t at-watch 2>/dev/null; then
  tmux kill-session -t at-watch
  tmux new-session -d -s at-watch "$HOME/autotask/watch.sh"
  echo "restarted tmux session at-watch"
fi
