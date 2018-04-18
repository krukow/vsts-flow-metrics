set -e

cd $(dirname $0)
PROJECT_DIR=$(pwd)
PROJECT_NAME=$(basename $PROJECT_DIR)

echo "Building..."

./build.sh

echo "Running smoke tests..."

./smoke-test.sh

echo "Releasing project '$PROJECT_NAME' in $PROJECT_DIR version: $1"

rm -f flow-metrics-$1.zip
zip -r -X flow-metrics-$1.zip flow-metrics target/flow-metrics.jar wiql doc project.clj

echo "Created flow-metrics-$1.zip"
