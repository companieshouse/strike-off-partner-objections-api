#!/bin/bash
#
# Start script for strike-off-partner-objections-api

PORT=50297

exec java -jar -Dserver.port="${PORT}" "strike-off-partner-objections-api.jar"