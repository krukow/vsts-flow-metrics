#!/bin/bash
set -e

cd $(dirname $0)
PROJECT_DIR=$(pwd)
PROJECT_NAME=$(basename $PROJECT_DIR)

echo "Fetching latest release"
release_url=$(curl -s https://api.github.com/repos/krukow/vsts-flow-metrics/releases/latest | grep browser_download_url | cut -d '"' -f 4)
echo $release_url

curl $release_url -L -o flow-metrics-latest.zip

echo "Unzipping flow-metrics-latest.zip"
unzip flow-metrics-latest.zip

echo "Sanity checking: show-config"
./flow-metrics show-config
