#!/bin/bash
set -x
set -e

echo "Warning this requires private data: cache/sample.json"

cp ./resources/sample-data.json cache/sample.json
echo "If you don't have this please generate your own data for smoke tests"
echo "Running smoke tests"

./flow-metrics show-config

./flow-metrics flow-efficiency cache/sample.json

./flow-metrics time-in-state cache/sample.json

./flow-metrics cycle-time cache/sample.json --chart sample.svg

./flow-metrics cycle-time cache/sample.json

./flow-metrics responsiveness cache/sample.json

./flow-metrics lead-time-distribution cache/sample.json

./flow-metrics aggregate-flow-efficiency cache/sample-relations.json

./flow-metrics batch sample-batch.json

echo "Warning slower commands (uses API)"
./flow-metrics historic-queues wiql/features-as-of-template.wiql

./flow-metrics pull-request-cycle-time

./flow-metrics pull-request-responsiveness
