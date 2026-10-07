#!/usr/bin/env bash
set -Eeuo pipefail

# Keep Steam's bundled libraries out of the host compositor and Waydroid.
unset LD_PRELOAD LD_LIBRARY_PATH
export XDG_RUNTIME_DIR="${XDG_RUNTIME_DIR:-/run/user/$(id -u)}"
export DBUS_SESSION_BUS_ADDRESS="${DBUS_SESSION_BUS_ADDRESS:-unix:path=$XDG_RUNTIME_DIR/bus}"

state_dir="${XDG_STATE_HOME:-$HOME/.local/state}/abyssme"
socket_name="wayland-abyssme"
mkdir -p "$state_dir"

if [[ "${1:-}" != --session ]]; then
    exec >>"$state_dir/launcher.log" 2>&1
    date -Is
    exec 9>"$XDG_RUNTIME_DIR/abyssme-waydroid.lock"
    flock -n 9 || exit 0
    if waydroid status | grep -q 'Session:.*RUNNING'; then
        echo 'Another Waydroid session is running. Close it before starting AbyssME.'
        command -v notify-send >/dev/null && notify-send AbyssME 'Close the running Waydroid session first.' || true
        exit 1
    fi

    export DISPLAY="${DISPLAY:-:0}"
    unset WAYLAND_DISPLAY
    exec weston --backend=x11 --shell=kiosk-shell.so \
        --socket="$socket_name" --width=1920 --height=1080 --fullscreen \
        --no-config --idle-time=0 --log="$state_dir/weston.log" \
        -- "$(readlink -f "$0")" --session
fi

export WAYLAND_DISPLAY="$socket_name"
session_pid=
cleanup() {
    trap - EXIT INT TERM HUP
    timeout 15 waydroid session stop || true
    if [[ -n "$session_pid" ]]; then
        kill "$session_pid" 2>/dev/null || true
        wait "$session_pid" 2>/dev/null || true
    fi
}
trap cleanup EXIT
trap 'exit 0' INT TERM HUP

waydroid session start >"$state_dir/session.log" 2>&1 &
session_pid=$!
ready=false
for ((attempt = 0; attempt < 90; attempt++)); do
    kill -0 "$session_pid" 2>/dev/null || exit 1
    if [[ "$(timeout 3 waydroid prop get sys.boot_completed 2>/dev/null || true)" == 1 ]]; then
        ready=true
        break
    fi
    sleep 1
done
if [[ "$ready" != true ]]; then
    echo 'Waydroid did not finish booting; see session.log.'
    exit 1
fi

waydroid app launch io.github.eriark.abyssme
wait "$session_pid"
