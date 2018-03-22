# vsts-flow-metrics
Tool for visualizing flow metrics from Visual Studio Team Services data.

# Usage

## Configuration

```bash
$ export VSTS_ACCESS_TOKEN=<SECRET_ACCESS_TOKEN>
$ export VSTS_INSTANCE=msmobilecenter.visualstudio.com
$ export VSTS_PROJECT=Mobile-Center
```

Optionally, override settings in default configuration by specifying
```bash
$ export VSTS_FLOW_CONFIG=<path-to-config.json>
```

To see configuration options:

```
$ ./flow-metrics show-config
```

### Loading and caching historic change data
In Clojure REPL:
```clojure
(in-ns 'vsts-flow-metrics.core)

;; cache and save all updates to features closed the last 30 days
(def features-closed-30d
        (storage/load-state-changes-from-cache
          (storage/cache-changes "wiql/closed-features-30d.wiql")))

;; in the future you can just look in cache and find the timestamped json file, e.g.,
(def features-closed-30d
        (storage/load-state-changes-from-cache
          "cache/2018-03-22T03:18-closed-features-30d.wiql.json"))

;; compute the time intervals that the features where in each state
(def features-closed-30d-ints (intervals-in-state features-closed-30d))

;; or using ->
(->> "cache/2018-03-22T03:18-closed-features-30d.wiql.json"
     storage/load-state-changes-from-cache
     intervals-in-state
     (take 2)
     clojure.pprint/pprint)
```
Command line interface:

```bash
$ ./flow-metrics cache-work-item-changes wiql/closed-features-30d.wiql

Saved work item state changes in /Users/krukow/code/vsts-flow-metrics/cache/2018-03-22T12:38-closed-features-30d.wiql.json
```

### Cycle time

In Clojure REPL
```clojure
(->  "cache/2018-03-22T03:18-closed-features-30d.wiql.json"
     storage/load-state-changes-from-cache
     intervals-in-state
     cycle-times
     ;;visualize cycle time
     ;;see more options at https://github.com/hypirion/clj-xchart
     charts/view-cycle-time)
```
Command line interface:
```bash
./flow-metrics cycle-time cache/2018-03-22T04:34-closed-features-30d.wiql.json --chart features-closed-30d-2018-03-22.svg
```
or, for example,
```
./flow-metrics cycle-time cache/2018-03-22T04:34-closed-features-30d.wiql.json --chart features-closed-30d-2018-03-22.png
```

### Time spent in state
Clojure REPL
```clojure
(->  "cache/2018-03-22T03:18-closed-features-30d.wiql.json"
     storage/load-state-changes-from-cache
     intervals-in-state
     days-spent-in-state
     clojure.pprint/pprint)

(->  "cache/2018-03-22T03:18-closed-features-30d.wiql.json"
     storage/load-state-changes-from-cache
     intervals-in-state
     days-spent-in-state
     ;;visualize cycle time
     ;;see more options at https://github.com/hypirion/clj-xchart
     (charts/view-time-in-state (charts/default-chart-options :time-in-state) (io/file "time.svg")))
```
Command line interface:
```bash
./flow-metrics time-in-state cache/2018-03-22T04:34-closed-features-30d.wiql.json --chart features-closed-30d-time-in-state-2018-03-22.svg
```

# License
Copyright © 2018 Karl Krukow

Distributed under the MIT License.
