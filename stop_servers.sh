#!/bin/bash

set -euo pipefail

stop_port() {
  local port="$1"
  local pids

  pids=$(lsof -ti tcp:"$port" || true)
  if [[ -z "$pids" ]]; then
    echo "No process found on port $port."
    return
  fi

  echo "Stopping server on port $port (PID: $pids)..."
  kill $pids
}

stop_port 8080
stop_port 8081
