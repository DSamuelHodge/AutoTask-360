#!/data/data/com.termux/files/usr/bin/bash
# Stay on AutoTask watch (127.0.0.1:8787). Reconnects. Appends facts to watch.log.
# Seatbelt: hrlite watch-line collapses consecutive same-type events
# (kind+body.type) and keeps every kind=run. Not catalog compression.
# Missing binary -> raw stream. Does not bind 8787; watch stays here.
set -u
HOME="${HOME:-/data/data/com.termux/files/home}"
PREFIX="${PREFIX:-/data/data/com.termux/files/usr}"
export PATH="$PREFIX/bin:$PATH"
DIR="$HOME/autotask"
LOG="$DIR/watch.log"
ERR="$DIR/watch.err"
URL="http://127.0.0.1:8787/v1/watch/stream"
HRLITE="$HOME/autotask/bin/hrlite"
mkdir -p "$DIR"
command -v termux-wake-lock >/dev/null 2>&1 && termux-wake-lock || true
echo "$(date -Iseconds) watch start $URL" >>"$LOG"
while true; do
  if ! curl -sf --max-time 2 http://127.0.0.1:8787/v1/status >/dev/null; then
    echo "$(date -Iseconds) 8787 down, retry" >>"$LOG"
    sleep 3
    continue
  fi
  if [ -x "$HRLITE" ]; then
    curl -sS -N --retry 0 "$URL" 2>>"$ERR" | "$HRLITE" watch-line >>"$LOG" || true
  else
    curl -sS -N --retry 0 "$URL" >>"$LOG" 2>>"$ERR" || true
  fi
  echo "$(date -Iseconds) stream dropped, reconnect" >>"$LOG"
  sleep 2
done
