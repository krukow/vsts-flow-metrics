#!/usr/bin/env bash

# Find clj executable
set +e
CLJ_CMD=$(type -p clj)
set -e
if [[ ! -n "$CLJ_CMD" ]]; then
    >&2 echo "Couldn't find Clojure CLI (the 'clj' command). "
    >&2 echo "Please install: https://clojure.org/guides/getting_started"
    exit 1
fi
cd $(dirname $0)
PROJECT_DIR=$(pwd)
PROJECT_NAME=$(basename $PROJECT_DIR)

rm -fr target
echo "Building project '$PROJECT_NAME' in $PROJECT_DIR"

clj -A:uberjar -Srepro -Sforce
version=$(clj -e '(-> "deps.edn" slurp clojure.edn/read-string (get-in [:aliases :uberjar :main-opts]) last symbol)')
ln -s target/vsts-flow-metrics-$version-standalone.jar target/flow-metrics.jar
echo "Done building '$PROJECT_NAME'"
