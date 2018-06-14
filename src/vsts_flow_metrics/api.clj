(ns vsts-flow-metrics.api
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [clj-http.client :as client]
            [vsts-flow-metrics.config :as cfg]
            [vsts-flow-metrics.pull-requests :as pull-requests]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [cheshire.core :as json]))


(defn- work-items-url
  ([instance ids]
   (str "https://" (:name instance)
        "/DefaultCollection/_apis/wit/workitems?api-version=3.0-preview&ids=" (string/join "," ids)
        "&$expand=relations&ErrorPolicy=omit"))
  ([instance ids as-of]
   (str "https://" (:name instance)
        "/DefaultCollection/_apis/wit/workitems?api-version=3.0-preview&ids=" (string/join "," ids)
        "&asOf=" (f/unparse (f/formatter :date-time) as-of)
        "&$expand=relations&ErrorPolicy=omit")))

(defn- work-item-updates-url [instance id]
  (str "https://" (:name instance)
       "/_apis/wit/workitems/" id "/updates?api-version=3.0-preview"))


(defn- wiql-url [instance project]
  (str "https://" (:name instance)
       "/DefaultCollection/" project "/_apis/wit/wiql?api-version=3.0-preview"))


(defn- repositories-url
  [instance project]
  (str "https://" (:name instance) "/" project "/_apis/git/repositories?api-version=4.1-preview"))

(defn- teams-url
  ([instance]
   (str "https://" (:name instance)
        "/_apis/teams?api-version=4.1-preview.2")))

(defn- team-url
  ([instance project team-name]
   (str "https://" (:name instance)
        "/_apis/projects/" project "/teams/" team-name "?api-version=4.1")))


(defn- groups-url [instance]
  (let [instance-name (:name instance)
        account-name (first (clojure.string/split instance-name #"\."))]
    (str "https://" account-name ".vssps.visualstudio.com"
       "/_apis/graph/groups?api-version=4.1-preview.1")))

;;GET https://{accountName}.vsrm.visualstudio.com/{project}/_apis/release/definitions?api-version=4.1-preview.3
(defn- release-definitions-url
  [instance project]
  (let [instance-name (:name instance)
        account-name (first (clojure.string/split instance-name #"\."))]
    (str "https://" account-name ".vsrm.visualstudio.com"
         "/" project
         "/_apis/release/definitions?api-version=4.1-preview.3")))

(defn- release-definition-url [instance project id]
  (let [instance-name (:name instance)
        account-name (first (clojure.string/split instance-name #"\."))]
    (str "https://" account-name ".vsrm.visualstudio.com"
         "/" project
         "/_apis/release/definitions" "/" id "?api-version=4.1-preview.3")))

;;GET https://{accountName}.vsrm.visualstudio.com/{project}/_apis/release/releases?definitionId={definitionId}&releaseCount={releaseCount}&api-version=4.1-preview.6
(defn- release-definition-summary-url
  [instance project definition-id]
  (let [instance-name (:name instance)
        account-name (first (clojure.string/split instance-name #"\."))]
    (str "https://" account-name ".vsrm.visualstudio.com"
         "/" project
         "/_apis/release/releases?definitionId=" definition-id
         "&releaseCount=1"
         "&api-version=4.1-preview.6")))

;GET https://{accountName}.visualstudio.com/_apis/projects/{projectId}/teams/{teamId}/members?api-version=4.1

(defn- team-members-url
  ([instance project team]
   (str "https://" (:name instance)
        "/_apis/projects/" project "/teams/" (:id team) "/members"
        "?api-version=4.1")))


;; GET https://{instance}/DefaultCollection/{project}/{project}/_apis/git/pullRequests?api-version={version}[&status={string}&creatorId={GUID}&reviewerId={GUID}&sourceRefName={string}&targetRefName={string}&$top={integer}&$skip={integer}]

;; GET https://{accountName}.visualstudio.com/{project}/_apis/git/repositories/{repositoryId}/pullrequests?searchCriteria.includeLinks={searchCriteria.includeLinks}&searchCriteria.sourceRefName={searchCriteria.sourceRefName}&searchCriteria.sourceRepositoryId={searchCriteria.sourceRepositoryId}&searchCriteria.targetRefName={searchCriteria.targetRefName}&searchCriteria.status={searchCriteria.status}&searchCriteria.reviewerId={searchCriteria.reviewerId}&searchCriteria.creatorId={searchCriteria.creatorId}&searchCriteria.repositoryId={searchCriteria.repositoryId}&maxCommentLength={maxCommentLength}&$skip={$skip}&$top={$top}&api-version=4.1-preview

(defn- all-pull-requests-url [instance project repo status]
  (str "https://" (:name instance)
       "/DefaultCollection/" project "/_apis/git/pullRequests?$top=1000"
       "&searchCriteria.repositoryId=" (:id repo)
       "&searchCriteria.status=" status
       "&api-version=4.1"))

(defn- team-pull-requests-url [instance project repo pr-status team-id]
  (str "https://" (:name instance)
       "/DefaultCollection/" project
       "/_apis/git/pullRequests?$top=1000"
       "&searchCriteria.repositoryId=" (:id repo)
       "&searchCriteria.status=" pr-status
       "&searchCriteria.reviewerId=" team-id
       "&api-version=4.1"))


;GET https://{accountName}.visualstudio.com/{project}/_apis/git/repositories/{repositoryId}/pullrequests/{pullRequestId}?api-version=4.1
(defn- pull-request-by-id-url
  [instance project repo pull-request-id]
  (str "https://" (:name instance) "/" project
       "/_apis/git/repositories/" (:id repo)
       "/pullRequests/" pull-request-id
       "?api-version=4.1"))

;; GET https://{accountName}.visualstudio.com/{project}/_apis/git/repositories/{repositoryId}/pullRequests/{pullRequestId}/commits?api-version=4.1-preview
(defn- pull-request-commits-url
  [instance project repo pull-request]
  (str "https://" (:name instance) "/" project
       "/_apis/git/repositories/" (:id repo)
       "/pullRequests/" (:pullRequestId pull-request) "/commits"
       "?api-version=4.1"))

;GET https://{accountName}.visualstudio.com/{project}/_apis/git/repositories/{repositoryId}/pullRequests/{pullRequestId}/workitems?api-version=4.1-preview
(defn- pull-requests-work-items-url
  [instance project repo pull-request]
  (str "https://" (:name instance) "/" project
       "/_apis/git/repositories/" (:id repo)
       "/pullRequests/" (:pullRequestId pull-request) "/workitems"
       "?api-version=4.1"))

(defn pull-request-threads-url
  [instance project repo pull-request]
  (str "https://" (:name instance) "/" project
       "/_apis/git/repositories/" (:id repo)
       "/pullRequests/" (:pullRequestId pull-request)
       "/threads"
       "?api-version=4.1"))


;; GET https://{accountName}.visualstudio.com/{project}/_apis/git/repositories/{repositoryId}/pullRequests/{pullRequestId}/reviewers?api-version=4.1-preview

(defn pull-request-reviewers-url
  [instance project repo pull-request]
  (str "https://" (:name instance) "/" project
       "/_apis/git/repositories/" (:id repo)
       "/pullRequests/" (:pullRequestId pull-request)
       "/reviewers"
       "?api-version=4.1"))



;;GET https://{accountName}.visualstudio.com/{project}/_apis/git/repositories/{repositoryId}/pullRequests/{pullRequestId}/iterations?includeCommits={includeCommits}&api-version=4.1
(defn pull-request-iterations-url
  [instance project repo pull-request]
  (str "https://" (:name instance) "/" project
       "/_apis/git/repositories/" (:id repo)
       "/pullRequests/" (:pullRequestId pull-request)
       "/iterations"
       "?api-version=4.1"))


(defn get-work-items
  ([instance ids]
   (let [response (client/get (work-items-url instance ids)
                              (:http-options instance))]
     (json/parse-string (:body response) true)))
  ([instance ids as-of]
   (let [response (client/get (work-items-url instance ids as-of)
                              (:http-options instance))]
     (json/parse-string (:body response) true))))

(defn query-work-items
  [instance project query]
  (let [response
        (client/post (wiql-url instance project)
                     (merge
                      (:http-options instance)
                      {:form-params {:query query} :content-type :json}))]
    (json/parse-string (:body response) true)))

(defn get-work-item-updates [instance id]
  (let [response (client/get (work-item-updates-url instance id)
                             (:http-options instance))]
    (json/parse-string (:body response) true)))

(defn get-work-item-state-changes [instance id]
  (let [response (client/get (work-item-updates-url instance id)
                             (merge (:http-options instance)
                                    {:throw-exceptions false}))
        item-updates (json/parse-string (:body response) true)]
    (filter
     (fn [{fields :fields}]
       (or (:System.State fields)
           (:System.BoardColumn fields)
           (:System.BoardLane fields)
           (:System.BoardColumnDone fields)))
     (:value item-updates))))


(defn get-repository-by-name [instance project repo-name]
  (let [response (client/get (repositories-url instance project)
                             (:http-options (cfg/vsts-instance)))
        repos    (json/parse-string (:body response) true)]

    (first (filter #(= repo-name (:name %)) (:value repos)))))


(defn get-team-by-name [instance project team-name]
  (let [response (client/get (team-url instance project team-name)
                             (:http-options (cfg/vsts-instance)))
        team (json/parse-string (:body response) true)]
    team))

(defn get-groups
  [instance]
  (let [response  (client/get (groups-url instance)  (:http-options (cfg/vsts-instance)))
        groups (:value (json/parse-string (:body response) true))]
    groups))

(defn get-group-by-principal-name
  [instance principal-name]
  (let [groups (get-groups (cfg/vsts-instance))]
    (first (filter #(= (:principalName %) principal-name) groups))))


(defn get-pull-request
  [instance project repo pull-request-id]
  (let [response (client/get (pull-request-by-id-url instance project repo pull-request-id)
                             (:http-options instance))]
    (pull-requests/normalize-pull-request (json/parse-string (:body response) true))))

(defn get-pull-requests
  "Gets list of pull requests assigned to a team. Filter by pr status: Active,
  Abandoned, Completed. Defaults to Active."
  ([instance project repo pr-status opt-team-or-nil]
   (let [url (if opt-team-or-nil
               (team-pull-requests-url instance project repo pr-status (:id opt-team-or-nil))
               (all-pull-requests-url instance project repo pr-status))

         response (client/get url (:http-options instance))
         pull-reqs (:value (json/parse-string (:body response) true))]
     (map pull-requests/normalize-pull-request pull-reqs))))

(defn get-pull-request-commits
  [instance project repo pull-request]
  (let [url (pull-request-commits-url instance project repo pull-request)
        response (client/get url (:http-options instance))]
    (:value (json/parse-string (:body response) true))))


(defn get-pull-request-work-items
  [instance project repo pull-request]
  (let [url (pull-requests-work-items-url instance project repo pull-request)
        response (client/get url (:http-options instance))
        work-item-ids (map :id (:value (json/parse-string (:body response) true)))]

    (zipmap (map keyword work-item-ids)
            (map #(get-work-item-state-changes instance %) work-item-ids))))

(defn get-pull-request-threads
  [instance project repo pull-request]
  (let [url (pull-request-threads-url instance project repo pull-request)
        response (client/get url (:http-options instance))]
    (:value (json/parse-string (:body response) true))))

(defn get-pull-request-iterations
  [instance project repo pull-request]
  (let [url (pull-request-iterations-url instance project repo pull-request)
        response (client/get url (:http-options instance))]
    (:value (json/parse-string (:body response) true))))

(defn get-release-definitions
  []
  (let [instance (cfg/vsts-instance)
        project (cfg/vsts-project)
        url (release-definitions-url instance project)
        response (client/get url (:http-options instance))]
    (:value (json/parse-string (:body response) true))))

(defn get-release-definition
  [id]
  (let [instance (cfg/vsts-instance)
        project (cfg/vsts-project)
        url (release-definition-url instance project id)
        response (client/get url (:http-options instance))]
    (json/parse-string (:body response) true)))

(defn get-release-definition-summary
  [id]
  (let [instance (cfg/vsts-instance)
        project (cfg/vsts-project)
        url (release-definition-summary-url instance project id)
        response (client/get url (:http-options instance))]
    (json/parse-string (:body response) true)))

(defn get-link [url]
  (let [instance (cfg/vsts-instance)
        response (client/get url (:http-options instance))]
    (json/parse-string (:body response) true)))
