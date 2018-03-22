# vsts-flow-metrics
Tool for visualizing flow metrics from Visual Studio Team Services data.

# Usage

TODO
## Configuration

```
➜  vsts-flow-metrics git:(master) ✗ export ACCESS_TOKEN=<SECRET_ACCESS_TOKEN>
➜  vsts-flow-metrics git:(master) ✗ export VSTS_INSTANCE=msmobilecenter.visualstudio.com
```

## REPL


```clojure
(in-ns 'vsts-flow-metrics.core)

;; cache and save all updates to features closed the last 30 days
(def features-closed-30d (storage/load-state-changes-from-cache
                           (storage/cache-changes "wiql/closed-features-30d.wiql" "Mobile-Center")))

;; in the future you can just look in cache and find the timestamped json file, e.g.,
(def features-closed-30d
    (storage/load-state-changes-from-cache
        "2018-03-22T03:18-closed-features-30d.wiql.json"))

;; compute the time intervals that the features where in each state
(def features-closed-30d-ints (intervals-in-state features-closed-30d))

(clojure.pprint/pprint (first features-closed-30d-ints))

```
# License
Copyright © 2018 Karl Krukow

Distributed under the MIT License.
