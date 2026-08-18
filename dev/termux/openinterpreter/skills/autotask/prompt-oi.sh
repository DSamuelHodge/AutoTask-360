#!/data/data/com.termux/files/usr/bin/bash
export HOME=/data/data/com.termux/files/home
export PREFIX=/data/data/com.termux/files/usr
export PATH="$PREFIX/bin:$PATH"
tmux load-buffer -b watchprompt "$HOME/autotask/watch-prompt.txt"
tmux paste-buffer -b watchprompt -t oi
tmux send-keys -t oi Enter
echo prompted
tmux ls
