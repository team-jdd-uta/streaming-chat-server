#!/bin/bash
echo "Starting server on port 8080..."
nohup java -jar build/libs/demo1-0.0.1-SNAPSHOT.jar --server.port=8080 > server1.log 2>&1 &
echo "Server on port 8080 started. PID: $!. Log: server1.log"

echo "Starting server on port 8081..."
nohup java -jar build/libs/demo1-0.0.1-SNAPSHOT.jar --server.port=8081 > server2.log 2>&1 &
echo "Server on port 8081 started. PID: $!. Log: server2.log"