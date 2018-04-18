# Interfaces
You can use this library from a [Clojure REPL](https://clojure.org/reference/repl_and_main),
or you can use the CLI tool (`./flow-metrics`). To set up the tool, look at the main [README.md](https://github.com/krukow/vsts-flow-metrics).

The REPL-based approach is much more powerful, but the CLI tool combined with the configuration file (described above) will do for the primary use-cases.

The following examples below show both the REPL based interface and the CLI.

Table of contents
=================

<!--ts-->
* [Interfaces](#interfaces)
      * [Concepts](#concepts)
         * [Caching work item changes](#caching-work-item-changes)
      * [vsts-flow-metrics functionality](#vsts-flow-metrics-functionality)
         * [Loading and caching historic change data](#loading-and-caching-historic-change-data)
         * [Cycle time metric](#cycle-time-metric)
         * [Time spent in state](#time-spent-in-state)
         * [Flow efficiency](#flow-efficiency)
         * [Responsiveness](#responsiveness)
         * [Lead time distribution](#lead-time-distribution)
         * [Historic queues](#historic-queues)

<!--te-->

## Concepts
Most of the functionality is accessed by first performing a VSTS Work Item Query Language query (a "WIQL" query) . This computes the target set of work items for which you want to compute one or more metrics.

For example, a WIQL query for [features closed in the last 30 days](/wiql/closed-features-30d.wiql) might be a set of work items that you'd want to compute the `cycle-time` metric for.

If you want to zoom in on a specific team/area-path use something like this WIQL clause

```
...
WHERE
        [System.TeamProject] = @project
        AND [System.AreaPath] UNDER "Mobile-Center\My-Team"
        AND [System.WorkItemType] IN ("User Story")
        AND [Microsoft.VSTS.Common.ClosedDate] >= @Today - 30
        AND [System.State] IN ("Closed")
```

### Caching work item changes
Since the tool will query not only the work items, but also all state changes made, it typically makes many calls to the VSTS REST API (which unfortunately doesn't support batch operations for fetching work item changes). For that reason, we've built in a caching function. Most of the metrics can be computed off of a cached set of work items with their entire history of state changes.

The next section shows how to compute a set of work items, including all state changes, and cache the result on the file system.

Alternatively, if you're just trying the tool or only computing a single metric off a set of work items, you can skip the caching step and provide an input .wiql file instead.

## `vsts-flow-metrics` functionality
Most of the `vsts-flow-metrics` tool behaviour is configured using a configuration file. You can show the current configuration by running

`./flow-metrics show-config` or calling `(vsts-flow-metrics.config/config)` in a Clojure REPL. The configuration is defined by merging the [default configuration](https://github.com/krukow/vsts-flow-metrics/blob/master/src/vsts_flow_metrics/config.clj) with an optional override configuration file specified by environment variable `VSTS_FLOW_CONFIG`.

For override examples, see below.

### Loading and caching historic change data
Make sure you have an environment variable `VSTS_FLOW_CONFIG` specifying a path to a JSON file. The config file should at least define the correct "project". In addition you must have a WIQL query that targets the set of work items you want to cache changes for. Here's an [example WIQL file](https://github.com/krukow/vsts-flow-metrics/blob/master/wiql/closed-features-30d.wiql).

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
;; Remember: ensure the environment variables are set in the REPL env
;; VSTS_ACCESS_TOKEN, VSTS_INSTANCE, VSTS_PROJECT
(def features-closed-30d
        (storage/load-state-changes-from-cache
          (storage/cache-changes "wiql/closed-features-30d.wiql")))

;; in the future you can load changes from cache:
(def features-closed-30d
        (storage/load-state-changes-from-cache
          "cache/2018-03-22T03:18-closed-features-30d.wiql.json"))

;; compute the time-intervals spent in each state
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

## Alternatively: don't use cache
./flow-metrics cycle-time wiql/closed-features-30d.wiql
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

[An example looks like this](https://s3-eu-west-1.amazonaws.com/flow-metrics-examples/features-closed-30d-2018-04-02T19-19.svg) (to view, your browser must support .svg format).

Alternatively, if you're not using a cache you can do:
```
./flow-metrics cycle-time wiql/closed-features-30d.wiql --chart closed.svg
```

Clojure REPL
```clojure
(->>  "cache/2018-04-06T07:11-closed-features-30d.wiql.json"
      storage/load-state-changes-from-cache
      intervals-in-state
      cycle-times
      (take 10)
      clojure.pprint/pprint)

(->  "wiql/closed-features-30d.wiql"
       (storage/work-item-state-changes (:project (cfg/config)))
       intervals-in-state
       cycle-times
       ;; cycle time in state
       ;;see more options at https://github.com/hypirion/clj-xchart
       (charts/view-cycle-time (charts/default-chart-options :cycle-time)))
```

### Time spent in state
The `time-in-state` tool computes how much time in days each work item spent in each state. 

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

### Flow efficiency
The `flow-efficiency` tool computes how much time was spent in "active" states relative to "blocked" states. Think of "active" states as "in development" and "blocked" states as "waiting for X" (e.g. waiting for deployment, waiting for design, waiting for review). Flow efficiency tells you something about how much time work items spend in queues waiting for people relative to time spent having someone actively working on them. Most kanban-inspired processes prefer to optimize flow-efficiency over resource utilization (i.e. prefer to keep work flowing over keeping everyone busy all the time).

Use the config file to specify which states you consider 'active' states and which you consider blocked 'blocked'. The default is:

```
{"flow-efficiency" : {
    "active-states" : [ "Active" ],
    "blocked-states" : [ "Blocked" ]}}
```

Before computing flow efficiency, it's a good idea to inspect `time-in-state` to see which states to consider "blocked" and which to consider "active" on the data set you're computing metrics for.

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

### Responsiveness 
The `responsiveness` tool computes how long work items take to transition from one state (`from-state`, defaults to "Ready for Work") to another (`to-state`, defaults to "Active"). Responsiveness computes how long time work is 'stuck' in a state, or how "responsive" the team processing `from-state` is.

See `show-config` to see configuration. Override `from-state` and `to-state` to change target states for responsiveness.

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

### Lead time distribution
Lead time distribution or cycle-time distribution computes how many work items to 0, 1, 2, 3, ... days to "complete". More technically, how many items transitioned from `from-state` to `to-state` in 0, 1, 2, ... days. See `show-config` to see configuration. Override `from-state` and `to-state` to change target states for `lead-time-distribution`.

CLI:
```
./flow-metrics lead-time-distribution cache/2018-04-02T19:19-closed-features-30d.wiql.json --chart lead-time-distribution-features-closed-30d-2018-04-02T19:19.svg
```

```
./flow-metrics lead-time-distribution cache/2018-04-02T19:19-closed-features-30d.wiql.json
{
  "0" : 1, //1 item completed in 0 days
  "7" : 1, //1 item completed in 7 days
  "20" : 1,
  "27" : 3, // 3 items completed in 27 days
  "1" : 1,
  "15" : 2,
  "21" : 2,
  ...
}
```

### Historic queues
The `historic-queues` tool is a bit different than the other tools. It is not based on a set of work items and their state transitions. Instead, it computes at certain points in time, how many work items where in which state. 

The `historic-queues` tool uses a WIQL template, e.g.: 
```
SELECT
        [System.Id],
        [System.Rev],
        [System.WorkItemType],
        [System.Title],
        [System.State],
        [System.AreaPath],
        [System.IterationPath],
        [System.Description],
        [System.AssignedTo],
        [Microsoft.VSTS.Common.ClosedDate],
        [System.Tags]
FROM workitems
WHERE
        [System.TeamProject] = @project
        AND [System.AreaPath] UNDER "Mobile-Center"
        AND [System.WorkItemType] IN ("Feature")
        AND [System.State] <> "Closed"
        AND [System.State] <> "Cancelled"
ASOF   '%s'

ORDER BY [System.ChangedDate] DESC
```

It evaluates the query at various points in time in the past (the `ASOF` date). How far back in time and how may samples to query is determined by the configuration:

```
 {"historic-queues" : {
    "ago" : 30,
    "step" : 3}}
```

This configuration combined with the above WIQL template samples for each 3 days in the last 30 days how many features where in each state.

Note: this doesn't use a cached set of changes and so the computation is slower as it needs to make many calls to the VSTS API.

CLI:
```
./flow-metrics historic-queues wiql/features-as-of-template.wiql
{
  "2018-04-02T20:27:24.611Z" : {
    "Ready for Work" : 20,
    "Need More Info" : 6,
    "New" : 129,
    "Ready For Triage" : 8,
    "Active" : 33,
    "Closed" : 1,
    "Blocked" : 2
  },
  "2018-03-30T20:27:24.611Z" : {
    "Need More Info" : 4,
    "New" : 131,
    "Closed" : 6,
    "Active" : 30,
    "Blocked" : 2,
    "Cancelled" : 6,
    "Ready for Work" : 16,
    "Ready For Triage" : 20,
    "Ready for Triage" : 1
  },
```

To chart:
```
./flow-metrics historic-queues wiql/features-as-of-template.wiql --chart as-of-30days-step3.svg
```