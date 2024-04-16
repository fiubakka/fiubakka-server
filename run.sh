#!/bin/bash

DEFAULT_AKKA_PORT=25520
DEFAULT_PLAYER_ACCEPTER_PORT=2020

show_help() {
    echo "Usage: $0 [AKKA_PORT] [PLAYER_ACCEPTER_PORT] [--debug, -d] [--help, -h]"
    echo "ATTENTION! Run without port arguments for first node execution!"
    echo "  AKKA_PORT: Port for Akka communication (default: $DEFAULT_AKKA_PORT)"
    echo "  PLAYER_ACCEPTER_PORT: Port for player accepter (default: $DEFAULT_PLAYER_ACCEPTER_PORT)"
    echo "  --debug: Enable JVM debug mode on port 5005"
    echo "  --help, -h: Show this help message"
    exit 0
}

ports=() # For removing -h or -d options from the command args

while [[ $# -gt 0 ]]; do
    case "$1" in
        --help | -h)
            show_help
            ;;
        --debug | -d)
            debug_option="-jvm-debug 5005"
            ;;
        *)
            ports+=("$1")
            ;;
    esac
    shift # Remove option from command args
done

set -- "${ports[@]}"

akka_port=${1:-$DEFAULT_AKKA_PORT}
player_accepter_port=${2:-$DEFAULT_PLAYER_ACCEPTER_PORT}

check_port() {
    local port_to_check=$1
    if netstat -tuln | grep ":$port_to_check " > /dev/null; then
        echo "Port $port_to_check is in use."
        exit 1
    fi
}

check_port "$akka_port"
check_port "$player_accepter_port"

sbt \
  -Dakka.cluster.seed-nodes.0=akka://fiubakka-server@127.0.0.1:$DEFAULT_AKKA_PORT \
  -Dakka.remote.artery.canonical.port=$akka_port \
  -Dakka.remote.artery.bind.port=$akka_port \
  -Dgame.player-accepter.port=$player_accepter_port \
  $debug_option \
  run
