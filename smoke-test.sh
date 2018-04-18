#!/bin/bash
set -x
set -e

echo "Warning this requires private data: cache/2018-04-06T07:02-closed-stories-15d.wiql.json"
echo "Warning this requires private data: cache/2018-04-06T07:11-closed-features-30d.wiql.json"

echo "If you don't have this please generate your own data for smoke tests"
echo "Running smoke tests"

./flow-metrics show-config

./flow-metrics flow-efficiency cache/2018-04-06T07:02-closed-stories-15d.wiql.json

./flow-metrics time-in-state cache/2018-04-06T07:11-closed-features-30d.wiql.json

./flow-metrics cycle-time cache/2018-04-06T07:11-closed-features-30d.wiql.json --chart features-closed-30d-2018-04-06.svg

./flow-metrics cycle-time cache/2018-04-06T07:11-closed-features-30d.wiql.json

./flow-metrics responsiveness cache/2018-04-06T07:11-closed-features-30d.wiql.json

./flow-metrics lead-time-distribution cache/2018-04-06T07:02-closed-stories-15d.wiql.json

echo "Warning slower commands (uses API)"
./flow-metrics historic-queues wiql/features-as-of-template.wiql

./flow-metrics pull-request-cycle-time

./flow-metrics pull-request-responsiveness
