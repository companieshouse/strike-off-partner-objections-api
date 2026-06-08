#!/bin/bash
#
# Start script for strike-off-partner-objections-api

PORT=8080

exec java -Dserver.port="${PORT}" -jar "strike-off-partner-objections-api.jar"
