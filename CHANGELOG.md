# Change Log
All notable changes to this project will be documented in this file. This change log follows the conventions of [keepachangelog.com](http://keepachangelog.com/).

## [v1.0.8]
### Added
- Support for `aggregate-flow-efficiency` which aggregates flow efficiency of children to produce flow efficiency for parent.

- Support for work item relation queries and easy query on all children of a work item.

- Support for "batch" operations that run multiple commands in one invocation. This supports simple data-driven scripts and saves execution time.


### Changes
- Fixes https://github.com/krukow/vsts-flow-metrics/issues/11 (cli tool grabs focus when executing).

- Fix small bug in charts when custom options are specified.

- Better documentation.

- Updated change log retroactively from release notes :)

Key PRs:
* https://github.com/krukow/vsts-flow-metrics/pull/18
* https://github.com/krukow/vsts-flow-metrics/pull/17


## [v1.0.7]
### Added
- support for --csv out.csv for cycle-time and flow-efficiency
- Batch operations support -- see #16

## [v1.0.6]
### Changes
- Fixes #15

## [v1.0.5]
### Changes
- Initial experimental PR support: pull-request-cycle-time and pull-request-responsiveness (feedback wanted)
- Doc improvements (Table of content)
- The release .zip packages wiql samples, docs and project.clj

## [v1.0.4]
### Changes
- Fixes #9 : config override caused failure in chart rendering
- Improve docs to clarify use of Project vs Area Path
- Allow VSTS_INSTANCE to be configured in config overrides

## [v1.0.3]
### Changes
- Make use of cache-changes optional. While still recommended for performance reasons to cache WIQL query results, the tool now supports providing a wiql query directly instead of only cache files. See Pull Request: #10

## [v1.0.2]
### Added 
- VSTS field selection, e.g. "cycle-time": {"field": "System.BoardColumn"}: "System.BoardColumn" or (default) "System.State"

- Support Doing/Done columns in Kanban boards

- Correct default states for flow-efficiency

- More robust algorithm for computing time intervals spent in state

- Better documentation
