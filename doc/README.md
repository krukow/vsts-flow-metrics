## Interfaces
You can use this library from a [Clojure REPL](https://clojure.org/reference/repl_and_main),
or you can build and use the CLI tool (`./flow-metrics`). 

The REPL-based approach is much more powerful, but the CLI tool combined with the configuration file (described above) will do for the primary use-cases.

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

;; or using ->>
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
./flow-metrics cycle-time cache/2018-03-22T04:34-closed-features-30d.wiql.json
{
  "33126" : 28.916666666666668,
  "31330" : 31.458333333333332,
  "33717" : 9.125,
  "31464" : 19.375,
  ...
}

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
     ;;visualize time in state
     ;;see more options at https://github.com/hypirion/clj-xchart
     (charts/view-time-in-state (charts/default-chart-options :time-in-state) (io/file "time.svg")))
```
Command line interface:
```bash
./flow-metrics time-in-state cache/2018-03-22T04:34-closed-features-30d.wiql.json --chart features-closed-30d-time-in-state-2018-03-22.svg

./flow-metrics time-in-state cache/2018-03-22T04:34-closed-features-30d.wiql.json
{
  "33126" : {
    "New" : 0.7916666666666666,
    "Active" : 27.75,
    "Ready For Triage" : 1.0416666666666667,
    "Ready For Work" : 0.0,
    "Closed" : 21.75
  },
  ...
}
```

### Flow efficiency
Clojure REPL:
```clojure
(->  "cache/2018-03-22T03:18-closed-features-30d.wiql.json"
     storage/load-state-changes-from-cache
     intervals-in-state
     flow-efficiency
     ;;visualize flow efficiency
     ;;see more options at https://github.com/hypirion/clj-xchart
     (charts/view-flow-efficiency (charts/default-chart-options :flow-efficiency) (io/file "eff.svg")))
```
Command line interface:
```bash
./flow-metrics flow-efficiency cache/2018-03-22T04:34-closed-features-30d.wiql.json --chart features-closed-30d-flow-eff-2018-03-22.svg

./flow-metrics flow-efficiency cache/2018-03-22T04:34-closed-features-30d.wiql.json
{
  "33504" : {
    "active" : 16.791666666666668,
    "blocked" : 14.166666666666666,
    "flow-efficiency" : 0.5423956931359354
  },
...
}
```
### Responsiveness 
See `show-config` to see configuration. Override `from-state` and `to-state` to change target states for responsiveness.

Clojure REPL:
```clojure 
(->>  "cache/2018-03-22T03:18-closed-features-30d.wiql.json"
      storage/load-state-changes-from-cache
      intervals-in-state
      responsiveness
      (take 2)
      clojure.pprint/pprint)

(->  "cache/2018-03-22T03:18-closed-features-30d.wiql.json"
     storage/load-state-changes-from-cache
     intervals-in-state
     responsiveness
     ;;visualize responsiveness
     ;;see more options at https://github.com/hypirion/clj-xchart
     (charts/view-responsiveness (charts/default-chart-options :responsiveness)))
```

Command line interface:
```bash
./flow-metrics responsiveness cache/2018-03-22T04:34-closed-features-30d.wiql.json
{
  "31330" : 6.25,
  "31464" : 21.375,
  "33716" : null,
  "32327" : 4.583333333333333,
  "30339" : 13.708333333333334,
  ...
}

./flow-metrics responsiveness cache/2018-03-22T04:34-closed-features-30d.wiql.json --chart features-resp-30d-time-in-state-2018-03-22.svg
```
