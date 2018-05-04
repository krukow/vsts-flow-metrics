(ns vsts-flow-metrics.cli
  (:require [clojure.tools.cli :as cli]
            [clojure.java.io :as io]
            [cheshire.core :as json]
            [vsts-flow-metrics.core :as core]
            [vsts-flow-metrics.storage :as storage]
            [vsts-flow-metrics.charts :as charts]
            [vsts-flow-metrics.config :as cfg]
            [vsts-flow-metrics.csv :as csv]
            [clojure.string :as string]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [vsts-flow-metrics.api :as api])
  (:gen-class :main true))



(defn- dump-usage
  [cli-summary]
  (binding [*out* *err*]
    (println "USAGE:")
    (println cli-summary)
    (println "\nTools:
    show-config                              - Prints current configuration in JSON format (overrides specified with VSTS_FLOW_CONFIG=my-overrides.json).

    cache-work-item-changes <wiql-path>      - Queries work items specified in .wiql file: <wiql-path>. Saves results in a cache folder. Note: a VSTS project must be specified in the config.

    cycle-time <cache/wiql>                  - Cycle times for a set of work-items defined by a .wiql file or a .json cache at path <cache/wiql>. Use the --chart option to save a chart. Or use the --csv <out.csv> to save data to a file in .csv format. Prints JSON format to stdout if no options specified.

    time-in-state <cache/wiql>               - Times in states for a set of work-items defined by a .wiql file or a .json cache at path. Use the --chart option to save a chart.

    flow-efficiency <cache/wiql>             - Flow efficiency for a set of work-items defined by a .wiql file or a .json cache at path. Use the --chart option to save a chart. Or use the --csv <out.csv> to save data to a file in .csv format. Prints JSON format to stdout if no options specified.

    responsiveness <cache/wiql>              - Responsiveness for a set of work-items defined by a .wiql file or a .json cache at path. Use the --chart option to save a chart.

    lead-time-distribution <cache/wiql>      - Lead time distribution for a set of work-items defined by a .wiql file or a .json cache at path. Use the --chart option to save a chart.

    historic-queues <wiql-template>          - Queues over time for <wiql-template>, a wiql template (see e.g., wiql/features-as-of-template.wiql). Use the --chart option to save a chart.

    pull-request-cycle-time                  - Cycle time for pull requests (optionally, use configuration to target a specific team). Use the --chart option to save a chart.

    pull-request-responsiveness              - Time to first vote in pull request reviews for a team. Team is configured using the configuration: :pull-requests :team-name. Use the --chart option to save a chart.

    batch <batch.json>                       - Run a batch of commands specified in the <batch.json> file. This can be more performant as you avoid runtime startup costs for each command. See documentation for more information about the format of the <batch.json> file.
    ")))

(def cli-options
  [["-h" "--help"  "Prints usage"]
   ["-c" "--chart FILENAME" "Saves a chart instead of printing data to stdout (.svg, .png, ...). Note that .pdf generation is very slow for some reason for .svg is preferred for vector graphs."
    :parse-fn (fn [fn]
                (when (string/blank? fn)
                  (println "Chart filename must be specified")
                  (System/exit 1))
                (io/file fn))]
   ["-s" "--csv FILENAME" "Save data in a file in comma separated values format (.csv)."
    :parse-fn (fn [fn]
                (when (string/blank? fn)
                  (println ".csv filename must be specified")
                  (System/exit 1))
                (io/file fn))]])

(defn- check-file-exists!
  [file]
  (when-not (and file (.exists file))
    (println "You must specify an existing file:" (str file))
    (System/exit 1)))


(defn csv-not-supported! []
  (throw (RuntimeException. "--csv option not yet supported for this tool")))

(defn print-result
  [res]
  (println (json/generate-string res {:pretty true})))

(defn show-config
  []
  (println (json/generate-string (cfg/config) {:pretty true})))

(defn cache-work-item-changes
  [_ args]
  (let [[wiql-file-path] args]
    (when (nil? wiql-file-path)
      (println "You must specify a path to a WIQL file.")
      (System/exit 1))
    (when-not (.exists (io/file wiql-file-path))
      (println "WIQL File does not exist:" wiql-file-path)
      (throw (RuntimeException. (str "File does not exist:" wiql-file-path))))
    (when-not (.isDirectory (io/file "cache"))
      (println "Please create a cache directory named: ./cache")
      (throw (RuntimeException. (str "No cache directory: ./cache exists"))))

    (println "Querying and caching changes to work items in " wiql-file-path)
    (println "Note: this may take some time depending on the number of work items in the result..." )
    (println "Saved work item state changes in " (storage/cache-changes wiql-file-path))))

(defn load-state-changes
  "Can either take a cached .json file or an uncached .wiql query"
  [file-path]
  (let [target-file (io/file file-path)
        lower-case-basename (.toLowerCase (.getName target-file))]
    (cond
      (.endsWith lower-case-basename ".json") ;; assume cache
      (storage/load-state-changes-from-cache target-file)

      (.endsWith lower-case-basename ".wiql") ;; assume WIQL
      (storage/work-item-state-changes target-file (:project (cfg/config)))

      :else
      (throw (RuntimeException. (str "File: " lower-case-basename " should have a .json / .wiql extension. Use .json for cached work item changes and .wiql for WIQL query files."))))))


(defn cycle-time [options args]
  (let [[cache-or-wiql-file-path] args]
    (when (nil? cache-or-wiql-file-path)
      (println "You must specify a path to a cache or wiql file.")
      (System/exit 1))
    (when-not (.exists (io/file cache-or-wiql-file-path))
      (println "File does not exist:" cache-or-wiql-file-path)
      (throw (RuntimeException. (str "File does not exist:" cache-or-wiql-file-path))))

    (let [cycle-times (-> (load-state-changes cache-or-wiql-file-path)
                          core/intervals-in-state
                          core/cycle-times)]
      (when (:csv options)
        (csv/write-fn-to-file csv/cycle-times
                              cycle-times
                              (:csv options)))
      (when (:chart options)
        (charts/view-cycle-time cycle-times
                                (charts/default-chart-options :cycle-time)
                                (:chart options)))
      (when (and (nil? (:csv options))
                 (nil? (:chart options)))
        (print-result cycle-times)))))

(defn time-in-state [options args]
  (let [[cache-or-wiql-file-path] args]
    (when (nil? cache-or-wiql-file-path)
      (println "You must specify a path to a cache or wiql file.")
      (System/exit 1))
    (when-not (.exists (io/file cache-or-wiql-file-path))
      (println "File does not exist:" cache-or-wiql-file-path)
      (throw (RuntimeException. (str "File does not exist:" cache-or-wiql-file-path))))

    (let [times-in-states (-> (load-state-changes cache-or-wiql-file-path)
                              core/intervals-in-state
                              core/days-spent-in-state)]
      (cond
        (:csv options)
        (csv-not-supported!)

        (:chart options)
        (charts/view-time-in-state times-in-states
                                   (charts/default-chart-options :time-in-state)
                                   (:chart options))
        :else
        (print-result times-in-states)))))

(defn responsiveness
  [options args]
  (let [[cache-or-wiql-file-path] args]
    (when (nil? cache-or-wiql-file-path)
      (println "You must specify a path to a cache or wiql file.")
      (System/exit 1))
    (when-not (.exists (io/file cache-or-wiql-file-path))
      (println "File does not exist:" cache-or-wiql-file-path)
      (throw (RuntimeException. (str "File does not exist:" cache-or-wiql-file-path))))

    (let [responsiveness (-> (load-state-changes cache-or-wiql-file-path)
                             core/intervals-in-state
                             core/responsiveness)]
      (cond
        (:csv options)
        (csv-not-supported!)

        (:chart options)
        (charts/view-responsiveness responsiveness
                                   (charts/default-chart-options :responsiveness)
                                   (:chart options))
        :else
        (print-result
         (core/map-values :in-days responsiveness))))))

(defn flow-efficiency [options args]
  (let [[cache-or-wiql-file-path] args]
    (when (nil? cache-or-wiql-file-path)
      (println "You must specify a path to a cache or wiql file.")
      (System/exit 1))
    (when-not (.exists (io/file cache-or-wiql-file-path))
      (println "File does not exist:" cache-or-wiql-file-path)
      (throw (RuntimeException. (str "File does not exist:" cache-or-wiql-file-path))))

    (let [flow-efficiency (-> (load-state-changes cache-or-wiql-file-path)
                              core/intervals-in-state
                              core/flow-efficiency)]
      (when (:csv options)
        (csv/write-fn-to-file csv/flow-efficiency
                              flow-efficiency
                              (:csv options)))

      (when (:chart options)
        (charts/view-flow-efficiency flow-efficiency
                                     (charts/default-chart-options :flow-efficiency)
                                     (:chart options)))

      (when (and (nil? (:csv options))
                 (nil? (:chart options)))
        (print-result flow-efficiency)))))

(defn lead-time-distribution
  [options args]
  (let [[cache-or-wiql-file-path] args]
    (when (nil? cache-or-wiql-file-path)
      (println "You must specify a path to a cache or wiql file.")
      (System/exit 1))
    (when-not (.exists (io/file cache-or-wiql-file-path))
      (println "File does not exist:" cache-or-wiql-file-path)
      (throw (RuntimeException. (str "File does not exist:" cache-or-wiql-file-path))))

    (let [lead-time-distribution (-> (load-state-changes cache-or-wiql-file-path)
                                     core/intervals-in-state
                                     core/lead-time-distribution)]
      (if (:chart options)
        (charts/view-lead-time-distribution
         lead-time-distribution
         (charts/default-chart-options :lead-time-distribution)
         (:chart options))
        (print-result lead-time-distribution)))))


(defn historic-queues
  [options args]
  (let [[template-file-path] args]
    (when (nil? template-file-path)
      (println "You must specify a path to a wiql template file.")
      (System/exit 1))
    (when-not (.exists (io/file template-file-path))
      (println "File does not exist:" template-file-path)
      (throw (RuntimeException. (str "File does not exist:" template-file-path))))

    (let [times (core/interesting-times (:historic-queues (cfg/config)))
          as-of (storage/work-items-as-of template-file-path times)
          state-distribution (core/work-items-state-distribution as-of)]
      (if (:chart options)
        (charts/view-historic-queues
         state-distribution
         (charts/default-chart-options :historic-queues)
         (:chart options))
        (print-result state-distribution)))))

(defn pull-request-cycle-time
  [options args]
  (let [closed-after-s (get-in (cfg/config) [:pull-request-cycle-time :closed-after])
        closed-after (f/parse (f/formatter :date) closed-after-s)
        configured-team-name (get-in (cfg/config) [:pull-requests :team-name])
        pr-unit (keyword (get-in (cfg/config) [:pull-request-cycle-time :cycle-time-unit]))
        team (if configured-team-name
               (api/get-team-by-name (cfg/vsts-instance) (cfg/vsts-project) configured-team-name))
        repo (api/get-repository-by-name (cfg/vsts-instance) (cfg/vsts-project)
                                         (get-in (cfg/config) [:pull-requests :repository]))

        pull-requests (api/get-pull-requests (cfg/vsts-instance) (cfg/vsts-project) repo "completed" team)
        filtered-prs (filter #(t/after? (:closedDate %) closed-after) pull-requests)
        pr-cycle-time (core/pull-requests-cycle-time filtered-prs)]

    (when-not (contains? #{:days :hours :mins} pr-unit)
      (throw (RuntimeException. "Configuration :pull-request-cycle-time :cycle-time-unit must be days, hours or mins")))

    (if (:chart options)
      (charts/view-pull-request-cycle-time
       (core/map-values #(get % pr-unit 0.0) pr-cycle-time)
       (charts/default-chart-options :pull-request-cycle-time)
       (:chart options))
      (print-result (core/map-values pr-unit pr-cycle-time)))))

(defn pull-request-responsiveness
  [options args]
  (let [closed-after-s (get-in (cfg/config) [:pull-request-responsiveness :closed-after])
        closed-after (f/parse (f/formatter :date) closed-after-s)
        configured-team-name (get-in (cfg/config) [:pull-requests :team-name])
        pr-unit (keyword (get-in (cfg/config) [:pull-request-responsiveness :responsiveness-time-unit]))
        team (if configured-team-name
               (api/get-team-by-name (cfg/vsts-instance) (cfg/vsts-project) configured-team-name))
        repo (api/get-repository-by-name (cfg/vsts-instance) (cfg/vsts-project)
                                         (get-in (cfg/config) [:pull-requests :repository]))

        pull-requests (api/get-pull-requests (cfg/vsts-instance) (cfg/vsts-project) repo "completed" team)
        active-pull-requests (api/get-pull-requests (cfg/vsts-instance) (cfg/vsts-project) repo "active" team)
        filtered-prs (concat (filter #(t/after? (:closedDate %) closed-after) pull-requests)
                             active-pull-requests)
        pr-responsiveness (core/pull-requests-first-vote-responsiveness filtered-prs team)]

    (when-not (contains? #{:days :hours :mins} pr-unit)
      (throw (RuntimeException. "Configuration :pull-request-responsiveness :responsiveness-time-unit must be days, hours or mins")))

    (if (:chart options)
      (charts/view-pull-request-cycle-time
       pr-responsiveness
       (charts/default-chart-options :pull-request-responsiveness)
       (:chart options))
      (print-result pr-responsiveness))))

(defn batch
  [options args]
  (let [batch-file (first args)]
    (when (nil? batch-file)
      (println "You must specify a path to a batch file in JSON format")
      (System/exit 1))
    (when-not (.exists (io/file batch-file))
      (println "File does not exist:" batch-file)
      (System/exit 1))
    (let [batch-data (json/parse-string (slurp (io/file batch-file)) true)]

      (doseq [{:keys [tool args options config] :as op} batch-data]
        (when-not (= #{:tool :args :options :config}
                     (into #{} (keys op)))
                  (throw (RuntimeException.
                          (str "Expect exactly: tool, args, options and config as keys in each operation. Found: " (keys op)))))
        (println "Running: " tool " with args: " args " options: " options " and config override: " config)
        (let [tool-fn (ns-resolve 'vsts-flow-metrics.cli (symbol tool))]
          (binding [cfg/*config-override* config]
            (tool-fn options args)))))))

(defn -main
  [& args]
  (let [{:keys [options arguments summary errors]}
        (cli/parse-opts args cli-options)]
    (cond
      errors (binding [*out* *err*]
               (println "** Failed to parse command line options:" errors)
               (dump-usage summary)
               (System/exit 1))
      (:help options) (do
                        (dump-usage summary)
                        (System/exit 0)))


    (when-not (or (string/blank? (first arguments))
                  (= "show-config" (first arguments)))
      (when (string/blank? (cfg/access-token))
        (println "You must specify environment variable:" "VSTS_ACCESS_TOKEN")
        (System/exit 1))

      (when (string/blank? (:name (cfg/vsts-instance)))
        (println "You must specify environment variable:" "VSTS_INSTANCE" " (for example export VSTS_INSTANCE=msmobilecenter.visualstudio.com)")
        (System/exit 1)))

    ;; Launch selected tool
    (try
      (case (first arguments)
        "show-config" (show-config)
        "cache-work-item-changes" (cache-work-item-changes options (rest arguments))
        "cycle-time" (cycle-time options (rest arguments))
        "time-in-state" (time-in-state options (rest arguments))
        "flow-efficiency" (flow-efficiency options (rest arguments))
        "responsiveness" (responsiveness options (rest arguments))
        "lead-time-distribution" (lead-time-distribution options (rest arguments))
        "historic-queues" (historic-queues options (rest arguments))
        "pull-request-cycle-time" (pull-request-cycle-time options (rest arguments))
        "pull-request-responsiveness" (pull-request-responsiveness options (rest arguments))
        "batch" (batch options (rest arguments))
        (binding [*out* *err*]
          (when (first arguments)
            (println "** No such tool: " (first arguments)))
          (dump-usage summary)
          (System/exit 1)))
      (catch Exception e
        (do
          (println e "Uncaught throwable")
          (if (System/getenv "DEBUG") (.printStackTrace e))
          (System/exit 27))))
    (System/exit 0)))
