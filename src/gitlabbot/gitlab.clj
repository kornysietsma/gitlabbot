(ns gitlabbot.gitlab
  (:require [clj-http.client :as client]
            [clj-http.util :as util]
            [cheshire.core :as c]
            ))

(defn get-token [config login password]
  (let [url (str (:api-base config) "session")
        resp (client/post url {:form-params {:login login :password password}})
        data (c/parse-string (:body resp) true)]
    (:private_token data)))

(defn private-headers [token]
  {:headers {"PRIVATE-TOKEN" token}})

(defn get-paginated "get a lazy sequence from a possibly paginated result, following next: headers to traverse all pages"
  [url token]
  (let [resp (client/get url (private-headers token))
        data (c/parse-string (:body resp) true)
        next-url (get-in resp [:links :next :href])]
    (if next-url
      (lazy-seq (concat data (get-paginated next-url token)))
      data)))

(defn get-single "get a url representing a single resource"
  [url token]
  (-> (client/get url (private-headers token))
      :body
      (c/parse-string true)))

(defn all-projects [config]
  (get-paginated (str (:api-base config) "projects") (:token config)))

(defn project-by-name [config name]
  (get-single (str (:api-base config) "projects/" (util/url-encode name)) (:token config)))

(defn project-by-id [config id]
  (get-single (str (:api-base config) "projects/" id) (:token config)))

(defn all-groups "get groups - returns a list of objects with keys :id :name :path :owner_id"
  [config]
  (get-paginated (str (:api-base config) "groups") (:token config)))

(defn group-id [config group-path]
  (let [groups (all-groups config)
        g (first (filter #(= group-path (:path %)) groups))]
    (:id g)))

(defn group-projects [config group-id]
  (:projects (get-single (str (:api-base config) "groups/" group-id) (:token config))))

(defn all-commits [config project-id]
  (get-paginated (str (:api-base config) "projects/" project-id "/repository/commits") (:token config)))

(defn last-commit [config project-id]
  (first (all-commits config project-id)))

(defn named-projects [config]
  (into {}
        (for [p (all-projects config)]
          [(:path p )
           (:ssh_url_to_repo p)])))

(defn summary [project]
  (merge (select-keys project [:id :name :last_activity_at])
         {:short-name (:path project)}
         {:group (get-in project [:namespace :name])}
         {:short-group (get-in project [:namespace :path])}))

(defn commit-summary [commit]
  (if commit
    (let [summary (select-keys commit [:id :short_id :created_at
                                       :author_name])
          result (merge summary {:message (or (:message commit) (:title commit))})] ; use title if no message
      result)
    nil))

(defn project-summaries [config]
  (into {}
        (for [p (all-projects config)]
          [(:id p)
           (summary p)])))

(defn summary-with-last-commit [config project]
  (let [lc (last-commit config (:id project))]
    (merge (summary project)
           {:last-commit (commit-summary lc)})))

(defn initial-project-data [config]
  (let [result (into {}
                     (for [p (all-projects config)]
                       [(:id p)
                        (summary-with-last-commit config p)]))]
    result))

(defn changed-projects [old-summary new-summary]
  (let [ids (clojure.set/intersection (keys old-summary) (keys new-summary))]
    ; we only care about common ids, with different activity dates
    (remove #(= (:last_activity_at (get old-summary %)) (:last_activity_at (get new-summary %))) ids)))

(defn merge-with-latest-commit [config old-project new-full-project]
  (if (= (:last_activity_at old-project) (:last_activity_at new-full-project))
    old-project
    (do
      (println "found difference: " (:name old-project) " activity" (:last_activity_at old-project) " to " (:last_activity_at new-full-project))
      (summary-with-last-commit config new-full-project))))


(defn update-projects [config old-projects]
  (let [new-project-ids (keys old-projects)
        new-projects (map (partial project-by-id config) new-project-ids)]
    (into {}
          (for [new-p new-projects]
            [(:id new-p)
             (if-let [old-p (get old-projects (:id new-p))]
               (merge-with-latest-commit config old-p new-p)
               (summary-with-last-commit config new-p))]))))

(defn update-all-projects [config old-projects]
  (let [new-projects (all-projects config)]
    (into {}
          (for [new-p new-projects]
            [(:id new-p)
             (if-let [old-p (get old-projects (:id new-p))]
               (merge-with-latest-commit config old-p new-p)
               (summary-with-last-commit config new-p))]))))

(defn changed-project? [old-project new-project]
  (let [old-commit (get-in old-project [:last-commit :id])
        new-commit (get-in new-project [:last-commit :id])]
    (if (= old-commit new-commit)
      nil
      (do
        (println "found commit changes:" (:name old-project) ":" (:last-commit old-project) (:last-commit new-project))
        new-project))))


(defn diff-project-lists [old-projects new-projects]
  (let [new-ids (set (keys new-projects))
        old-ids (set (keys old-projects))
        removed-ids (clojure.set/difference new-ids old-ids)
        added-ids (clojure.set/difference old-ids new-ids)
        common-ids (clojure.set/intersection old-ids new-ids)]
    {:removed (vals (select-keys old-projects removed-ids))
     :added (vals (select-keys new-projects added-ids))
     :modified (filter identity
                       (for [id common-ids]
                         (changed-project? (old-projects id) (new-projects id))))}))

(defn print-commit-msg [{:keys [short-group short-name last-commit] :as commit}]
  (println "comming message:" commit)
  (if last-commit
    (str "[" short-group "/" short-name "/" (:short_id last-commit) "] " (:author_name last-commit) ": " (clojure.string/trim (:message last-commit)))
    (str "[" short-group "/" short-name "] - no commits yet")))

(defn printable-diff [diff-data]
  (concat
    (when-let [removed (:removed diff-data)]
      (map #(str "project disappeared: " (:group %) "/" (:name %)) removed))
    (when-let [added (:added diff-data)]
      (map print-commit-msg added))
    (when-let [modified (:modified diff-data)]
      (map print-commit-msg modified))))

(comment "for repl-ing"
  (def conf gitlabbot.config/default-config)
  (def prevp (initial-project-data conf))
  (def newp (update-all-projects conf prevp))
  (clojure.pprint/pprint prevp)
  (clojure.pprint/pprint newp)
  (clojure.pprint/pprint (diff-project-lists prevp newp))
  (clojure.pprint/pprint (printable-diff (diff-project-lists prevp newp)))

  (clojure.pprint/pprint
    (let [config gitlabbot.config/config
          projects (all-projects config)]
      (for [project projects]
        (do
          [(:name project)
           (map commit-summary (all-commits config (:id project)))])
        )
      ))
  )