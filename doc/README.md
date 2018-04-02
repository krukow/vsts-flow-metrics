# Interfaces
You can use this library from a [Clojure REPL](https://clojure.org/reference/repl_and_main),
or you can use the CLI tool (`./flow-metrics`). To set up the tool, look at the main [README.md](https://github.com/krukow/vsts-flow-metrics).

The REPL-based approach is much more powerful, but the CLI tool combined with the configuration file (described above) will do for the primary use-cases.

The following examples below show both the REPL based interface and the CLI.

## Concepts
Most of the functionality is accessed by first performing a VSTS Work Item Query Language query (a "WIQL" query) . This computes the target set of work items for which you want to compute one or more metrics.

For example, a WIQL query for [features closed in the last 30 days](/wiql/closed-features-30d.wiql) might be a set of work items that you'd want to compute the `cycle-time` metric for.

Since the tool will query not only the work items, but also all state changes made, it typically makes many calls to the VSTS REST API (which unfortunately doesn't support batch operations for fetching work item changes). For that reason, we've built in a caching function. Most of the metrics will be computed off of a cached set of work items with their entire history of state changes.

The next section shows how to compute a set of work items, including all state changes, and cache the result on the file system.

## `vsts-flow-metrics` functionality
Most of the `vsts-flow-metrics` tool behaviour is configured using a configuration file. You can show the current configuration by running

`./flow-metrics show-config` or calling `(vsts-flow-metrics.config/config)` in a Clojure REPL. The configuration is defined by merging the [default configuration](https://github.com/krukow/vsts-flow-metrics/blob/master/src/vsts_flow_metrics/config.clj#L59) with an optional override configuration file specified by environment variable `VSTS_FLOW_CONFIG`.

For example `{"project":"Mobile-Center"}` is a default setting which should be changed except for unlikely event of targeting a VSTS project named "Mobile-Center". For more override examples, see below.

### Loading and caching historic change data
Make sure you `VSTS_FLOW_CONFIG` specifies the correct "project" and that you have a WIQL query that targets the set of work items you want to cache changes for. Here's an [example WIQL file](https://github.com/krukow/vsts-flow-metrics/blob/master/wiql/closed-features-30d.wiql).

Command line interface:
```
$ ./flow-metrics cache-work-item-changes wiql/closed-features-30d.wiql
Querying and caching changes to work items in  wiql/closed-features-30d.wiql
Note: this may take some time depending on the number of work items in the result...
Saved work item state changes in  /Users/krukow/code/vsts-flow-metrics/cache/2018-04-02T19:19-closed-features-30d.wiql.json
```

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
### Cycle time metric
Cycle-time computes time to transition from one state (`from-state`) to another (`to-state`) for a set of work items. The `from-state` defaults to "Active" and the `to-state` defaults to "Closed". The `from-state` and `to-state` can be controlled using the configuration file. For example, with a configuration file
```
{"project":"Mobile-Center", 
 "cycle-time": {
     "from-state": "Ready for Work", 
     "to-state":   "Closed"}}
```

you'd compute work item cycle times from state "Ready for Work" to "Closed".

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
```
The key is the work item id, and the value is the cycle time in days.

You can create a chart of the cycle times using:
```
./flow-metrics cycle-time cache/2018-03-22T04:34-closed-features-30d.wiql.json --chart features-closed-30d-2018-03-22.svg
```
or, for example,
```
./flow-metrics cycle-time cache/2018-03-22T04:34-closed-features-30d.wiql.json --chart features-closed-30d-2018-03-22.png
```

It looks like this:




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
