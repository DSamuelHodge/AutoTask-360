#!/data/data/com.termux/files/usr/bin/bash
export HOME=/data/data/com.termux/files/home
export PREFIX=/data/data/com.termux/files/usr
export PATH="$PREFIX/bin:$PATH"
chmod 755 "$HOME/autotask/watch.sh" 2>/dev/null || true
if tmux has-session -t at-watch 2>/dev/null; then
  tmux kill-session -t at-watch
fi
tmux new-session -d -s at-watch "$HOME/autotask/watch.sh"
sleep 2
tmux ls
echo '---LOG---'
tail -n 15 "$HOME/autotask/watch.log" 2>/dev/null || echo no-log-yet
