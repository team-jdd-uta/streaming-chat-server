#!/bin/bash
echo "Stopping servers..."

# Kill process on port 8080
echo "Stopping server on port 8080..."
lsof -i :8080 | grep LISTEN | awk '{print $2}' | xargs kill -9 2>/dev/null
if [ $? -eq 0 ]; then
    echo "Server on port 8080 stopped."
else
    echo "No server running on port 8080."
fi

# Kill process on port 8081
echo "Stopping server on port 8081..."
lsof -i :8081 | grep LISTEN | awk '{print $2}' | xargs kill -9 2>/dev/null
if [ $? -eq 0 ]; then
    echo "Server on port 8081 stopped."
else
    echo "No server running on port 8081."
fi

echo "All servers stopped."
