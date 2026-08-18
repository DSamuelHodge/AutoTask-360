#!/data/data/com.termux/files/usr/bin/bash
# Stay on AutoTask watch (127.0.0.1:8787). Reconnects. Appends facts to watch.log.
set -u
HOME="${HOME:-/data/data/com.termux/files/home}"
PREFIX="${PREFIX:-/data/data/com.termux/files/usr}"
export PATH="$PREFIX/bin:$PATH"
DIR="$HOME/autotask"
LOG="$DIR/watch.log"
ERR="$DIR/watch.err"
URL="http://127.0.0.1:8787/v1/watch/stream"
mkdir -p "$DIR"
command -v termux-wake-lock >/dev/null 2>&1 && termux-wake-lock || true
echo "$(date -Iseconds) watch start $URL" >>"$LOG"
while true; do
  if ! curl -sf --max-time 2 http://127.0.0.1:8787/v1/status >/dev/null; then
    echo "$(date -Iseconds) 8787 down, retry" >>"$LOG"
    sleep 3
    continue
  fi
  curl -sS -N --retry 0 "$URL" >>"$LOG" 2>>"$ERR" || true
  echo "$(date -Iseconds) stream dropped, reconnect" >>"$LOG"
  sleep 2
done
