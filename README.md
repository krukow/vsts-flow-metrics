# vsts-flow-metrics
Tool for visualizing flow metrics from Visual Studio Team Services data.

Inspired by [Kanban Metrics in Practice](https://www.infoq.com/presentations/kanban-metrics-sky) by [Mattia Battiston](https://www.linkedin.com/in/mattiabattiston/).

The idea is to support most if not all of the metrics discussed above, as well as more we discover to be useful. Right now only a limited set of the metrics are supported. 

# Setup

To use `vsts-flow-metrics` you can download a release and run it from the command prompt, you can build from source or you can run it in interactive mode using a Clojure REPL (Read Eval Print Loop). The latter is more powerful, but also requires knowledge of Clojure.

You must have [Java installed](https://java.com/en/download/) to use `vsts-flow-metrics`.

On OS X, I have:

```bash
export JAVA_HOME=`/usr/libexec/java_home`
export PATH=$JAVA_HOME/bin:$PATH
```

## Download and run a released build

1. Download a release from the [releases page](https://github.com/krukow/vsts-flow-metrics/releases)
2. Unzip the `.zip` archive, e.g., `unzip flow-metrics-1.0.0.zip` it contains a bash shell script (`./flow-metrics`) which invokes `java -jar target/flow-metrics.jar`. If your system doesn't support bash you can simply run this command instead. 


## Build from source
You need to install [leiningen](https://leiningen.org/) and have a Java SDK to build from source (i.e., ensure that both `lein` and `javac` are on your shell path).

Clone this repo: `git clone git@github.com:krukow/vsts-flow-metrics.git`

Change dir: `cd vsts-flow-metrics`

Build: `./build.sh`

Run CLI after build: `./flow-metrics`

### Clojure REPL
Use a Clojure REPL in your development environment or `lein repl` for a basic REPL (should be enough for the examples in the docs folder).

## Configuration
You must create a VSTS personal access token for access to your VSTS project. The token must have permissions "Work item search (read)" and "Work item (read)".

Then set the following environment variables.

```bash
$ export VSTS_ACCESS_TOKEN=<SECRET_ACCESS_TOKEN>
$ export VSTS_INSTANCE=msmobilecenter.visualstudio.com
$ export VSTS_PROJECT=Mobile-Center
```
(On OS X with bash).

Finally, you can and should override settings in default configuration by specifying:

```bash
$ export VSTS_FLOW_CONFIG=<path-to-config.json>
```

To see configuration options:

```
$ ./flow-metrics show-config
```

For example:
```bash
cat example-config-override.json
{"project":"My-Project",
 "cycle-time": {"field": "System.BoardColumn"},
 "flow-efficiency" : {
     "active-states" : [ "Active" ],
     "blocked-states" : [ "Blocked", "In Review", "Deploying" ]}}

$ VSTS_FLOW_CONFIG=example-config-override.json ./flow-metrics show-config
{
  "project" : "My-Project",
  "cycle-time" : {
    "from-state" : "Active",
    "to-state" : "Closed",
    "field" : "System.BoardColumn",
    ...
  }
  "flow-efficiency" : {
    "active-states" : [ "Active" ],
    "blocked-states" : [ "Blocked", "In Review", "Deploying" ],
    ...
    }
}
```
To use the tool for what you want, you'll almost certainly need to use configuration overrides.

# Usage and How-To
For usage and how-to instructions, see the [doc](/doc) folder.

# License
Copyright © 2018 Karl Krukow

Distributed under the MIT License.
