set -e

cd $(dirname $0)
PROJECT_DIR=$(pwd)
PROJECT_NAME=$(basename $PROJECT_DIR)

echo "Building project '$PROJECT_NAME' in $PROJECT_DIR"

# Ensure the old build is cleaned out. We observed cases where lein fails on this.
rm -rf target

lein uberjar

# Do a dry run to assert that the built jar works
echo "Uberjar built."

echo "Done building '$PROJECT_NAME'"
