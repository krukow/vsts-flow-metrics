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
    * [Work items query spec](#work-items-query-spec)
    * [Cycle time metric](#cycle-time-metric)
    * [Time spent in state](#time-spent-in-state)
    * [Flow efficiency](#flow-efficiency)
    * [Aggregate flow efficiency](#aggregate-flow-efficiency)
    * [Responsiveness](#responsiveness)
    * [Lead time distribution](#lead-time-distribution)
    * [Historic queues](#historic-queues)
    * [Pull Request Cycle Time](#pull-request-cycle-time)
    * [Pull Request Responsiveness](#pull-request-responsiveness)
    * [Batch operations](#batch-operations)

<!--te-->

## Concepts
Most of the functionality is accessed by first performing a VSTS Work Item Query Language query (a "WIQL" query).
This computes the target set of work items for which you want to compute one or more metrics.

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

There are also tools that compute data, metrics and visualizations on non-work item data, e.g., pull requests, commits, pull request reviews, etc. Those are documented separately below.

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

### Work items query spec
Many of the tools take a type of input that we call a work item query spec. Currently there are 3 types of work item query specs:

1. `.json` files: these must be cache files generated by the `cache-work-item-changes` command above (or equivalent). Example: `2018-05-14T07:11-ALL-appcenter-closed-stories-15d.json`.
2. `.wiql` files: these are files storing a raw `.wiql` query. Example: `wiql/closed-features-30d.wiql`.
3. work item id: this is simply an integer corresponding to a VSTS work item in your configured instance and project. This is a simple way of querying all children of a particular work item, say all the stories that are children of a features. Example: `33617`.
4. VSTS Query id: [TODO: Not implemented yet]. This type of query spec uses an existing VSTS query which must already be defined in the VSTS project.

Note that `.wiql` files can either be queries that return work items or work item relations. The simplest queries just return a list of work items. Queries that return work item relations can express things like "find all the user stories that are a child of a feature that was closed in the last 30 days".

Note: that the tool currently only supports work item relation queries for relations of type: `"System.LinkTypes.Hierarchy-Forward"` (which means parent-child relationships). Only the `aggregate-flow-efficiency` tool currently supports work item relation queries.

The `work-item-id` type of spec is simply a shorthand convenience: under the hood it generates a WIQL query of type work-item-relation which finds all the children of the specified work item.

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

You can generate a .csv file using the `--csv out.csv` option.

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

Use the config file to specify which states you consider 'active' states and which you consider 'blocked'. The default is:

```
{"flow-efficiency" : {
    "active-states" : [ "Active" ],
    "blocked-states" : [ "Blocked", "In Review" ]}}
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

You can generate a .csv file using the `--csv out.csv` option.

### Aggregate flow efficiency
Aggregate flow efficiency is intended to be used with "parent" work items that consist of a number of child work items (think of the "parent" as a feature and the "children" as user stories). Similarly to `flow-efficiency`, the `aggregate-flow-efficiency` tool computes how much time was spent in "active" states relative to "blocked" states. However the way it's computed is different.

Instead of looking at the state transitions of the parent, instead, we aggregate the flow efficiencies of each of the children into a flow efficiency metric for the parent. Technically, we compute the sum of the active states of the children, and then devide that by the sum of "total time" of the children (where total time for each child is 'active' plus 'blocked' which often matches the cycle-time).

Aggregates are useful for "parent" items because sometimes there may be activity on a single "child" of a feature but, say, 5 of the other "children" are blocked. With regular flow efficiency we don't capture the fact that a large part of the feature was blocked. Aggregate flow efficiency tries to capture this by looking at the proportion of 'active' time of all the "children" to the proportion of all 'blocked' + 'active'.

Use the config file to specify which states you consider 'active' states and which you consider 'blocked' for the "children". The default is:

```
{"aggregate-flow-efficiency" : {
    "active-states" : [ "Active" ],
    "blocked-states" : [ "Blocked", "In Review" ]}}
```

Command line interface:
```bash
./flow-metrics aggregate-flow-efficiency 33617 --csv 33617-agg-flow-eff-2018-05-17.csv
```

The `aggregate-flow-efficiency` tool supports only relational WIQL queries. See the file `wiql/ALL-closed-features-relations-30d.wiql` for an example.

Clojure REPL:
```clojure
(->> "cache/2018-05-17T08:18-ALL-closed-features-relations-30d.wiql.json"
                            (storage/load-state-changes-from-cache)
                            intervals-in-state
                            aggregate-flow-efficiency
                            (take 2)
                            clojure.pprint/pprint)
;; ([:37796
;;   {:active 8.25,
;;    :blocked 1.8749999999999998,
;;    :flow-efficiency 0.8148148148148148}]
;;  [:36885
;;   {:active 21.333333333333332,
;;    :blocked 6.083333333333334,
;;    :flow-efficiency 0.7781155015197568}])
```

You can generate a .csv file using the `--csv out.csv` option.

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

### Pull Request Cycle Time
The `pull-request-cycle-time` tool works of a set of pull requests for a VSTS project and repository. We define pull request (PR) cycle time as the time from the PR is created until it is completed (i.e. merged). We only consider PRs that are completed for cycle time.

The `pull-request-cycle-time` tool uses the configuration system to filter down the pull requests. The key configuration options are:

```
$ ./flow-metrics show-config
{
  "instance" : "msmobilecenter.visualstudio.com",
  "project" : "Mobile-Center",
  "pull-requests" : {
    "repository" : "appcenter",
    "team-name" : "Kasper-Team"
  },
  "pull-request-cycle-time" : {
    "closed-after" : "2018-04-01",
    "cycle-time-unit" : "hours",
    ...
  }   
```
This should be read as follows: find pull requests "completed" after "2018-04-01" in the "appcenter" repo in the "msmobilecenter" instance where "Kasper-Team" is assigned as reviewers. Compute the cycle time (in hours) for each of those PRs.

Note: this doesn't use a cached set of changes and so the computation is slower as it needs to make many calls to the VSTS API.

CLI:
```
./flow-metrics pull-request-cycle-time
{
  "11641" : 3.55,
  "11529" : 19.866666666666667,
  "11187" : 3.1166666666666667,
  "11364" : 19.233333333333334,
  "11815" : 11.233333333333333,
  "11693" : 0.3333333333333333,
  "11702" : 70.03333333333333,
  "11770" : 0.6833333333333333,
  "11588" : 21.483333333333334,
  "11533" : 67.36666666666666,
  "11608" : 10.983333333333333,
  "10928" : 150.63333333333333,
  "11432" : 94.26666666666667,
  "11524" : 0.8833333333333333,
  "11523" : 0.5166666666666667,
  "11700" : 92.4,
  "11584" : 0.25,
  "10925" : 151.68333333333334
}
```

To chart:
```
$ ./flow-metrics pull-request-cycle-time --chart pr-cycle-time-kasper-team-apr-1.svg
```

### Pull Request Responsiveness
The `pull-request-responsiveness` tool is a bit complicated. It's intended to compute how fast a VSTS team responds with review feedback on pull requests assigned to their team. It computes for a set of pull requests, the time to first review vote cast by a specific team. 

We define the responsiveness by a team on a pull request as the time from the team is assigned as a reviewer until any member of that team has provided feedback by casting a vote ("Approve", "Approve with suggestions" "Wait for author" "Reject").

The `pull-request-responsiveness` tool uses the configuration system to filter down the pull requests. The key configuration options are:

```
$ ./flow-metrics show-config
{
  "instance" : "msmobilecenter.visualstudio.com",
  "project" : "Mobile-Center",
  "pull-requests" : {
    "repository" : "appcenter",
    "team-name" : "Kasper-Team"
  },
  "pull-request-responsiveness" : {
    "closed-after" : "2018-04-01",
    "responsiveness-time-unit" : "hours",
    ...
  }  
```
This should be read as follows: find pull requests "completed" after "2018-04-01" or which are currently active in the "appcenter" repo in the "msmobilecenter" instance where "Kasper-Team" is assigned as reviewers. Compute the responsiveness (in hours) for each of those PRs for "Kasper-Team".

Note: this doesn't use a cached set of changes and so the computation is slower as it needs to make many calls to the VSTS API. This is especially slow since it needs to crawl all PR threads as well as find membership of users for the specified team.

CLI:
```
$ ./flow-metrics pull-request-responsiveness

{
  "11641" : 0,
  "11529" : 3,
  "11187" : 0,
  "11364" : 4,
  "11815" : 0,
  "11693" : -1.0,
  "11702" : 68,
  "11770" : -1.0,
  "11821" : 18,
  "11588" : -1.0,
  "11533" : 66,
  "11608" : 10,
  "10928" : 0,
  "11432" : 67,
  "11524" : 0,
  "11523" : 0,
  "11700" : 66,
  "11584" : 0,
  "10925" : 0
}
```
Note that -1 means the PR has not yet received any reviews by the team.

To chart:
```
$ ./flow-metrics pull-request-responsiveness --chart pr-responsiveness-kasper-team-apr-1.svg
```

### Batch operations
The `batch` tool allows you to run several commands in one invocation of the cli tool. You provide the batch tool with a simple .json file which must be an array of commands you want to run. Each command has the format

```json
[
  {
      "tool": "cycle-time", // the tool to run
      "args": [ //args to supply
        "cache/example.json"
      ],
      "options": { // any -- options e.g. csv
        "csv": "out.csv"
      },
      "config": { // any config overrides for this command only
        "pull-requests": {
          "repository": "appcenter",
          "team-name": "Dennis-Team"
        }
      }
  },
  ...
]
```
The example above is equivalent of running

```
$ VSTS_CONFIG=override.json ./flow-metrics cycle-time cache/example.json --csv out.csv
```
where `VSTS_CONFIG` references a file with the contents of the config object above.

The batch tool will run each of these commands in sequence.