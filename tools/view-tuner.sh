#!/bin/sh
# Live tuning of the cockpit viewmodel (CockpitHands). The game stays in the cockpit and re-shoots
# run/screenshots/ap_view_*.png whenever asked; edit run/view/tuning.json between shots.
#   tools/view-tuner.sh start   launch (background), wait for the first set of shots
#   tools/view-tuner.sh shoot   reload run/view/tuning.json and re-shoot, wait until done
#   tools/view-tuner.sh stop    quit the game (kills its PID if it doesn't), stop the Gradle daemons
#   tools/view-tuner.sh once    start, shoot once, always stop (use this from scripts/agents: nothing is left running)
# The game also quits by itself after 10 minutes without a shot request.
set -e
cd "$(dirname "$0")/.."
export JAVA_HOME="$(brew --prefix openjdk@25)/libexec/openjdk.jdk/Contents/Home"
V=run/view
wait_done() { i=0; while [ ! -f $V/done ] && [ $i -lt 120 ]; do sleep 2; i=$((i+1)); done; [ -f $V/done ] && echo "shots ready" || echo "timed out (see .scratch/view.log)"; }
case "$1" in
  start)
    mkdir -p $V .scratch; rm -f $V/done $V/shoot $V/quit $V/pid
    nohup caffeinate -d -u -i ./gradlew runAutopilotView > .scratch/view.log 2>&1 &
    wait_done ;;
  shoot)
    rm -f $V/done; touch $V/shoot; wait_done ;;
  stop)
    touch $V/quit; i=0; while [ -f $V/pid ] && [ $i -lt 15 ]; do sleep 2; i=$((i+1)); done
    if [ -f $V/pid ]; then kill "$(cat $V/pid)" 2>/dev/null || true; rm -f $V/pid; fi
    rm -f $V/quit; ./gradlew --stop -q >/dev/null 2>&1 || true
    pgrep -fl "fml.modFolders=powerarmor" >/dev/null && echo "WARNING: a game client is still running" || echo "stopped" ;;
  once)
    trap '"$0" stop' EXIT
    "$0" start ;;
  *) echo "usage: $0 start|shoot|stop|once"; exit 1 ;;
esac
