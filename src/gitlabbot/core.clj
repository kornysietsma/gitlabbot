(ns gitlabbot.core
  (:require [botty.core :as botty]
            [clojure.pprint :refer [pprint]]
            [gitlabbot.gitlab :as gitlab]
            [clojure.core.match :refer [match]]
            [clojure.tools.reader.edn :as edn]
            [clojure.core.async :refer [put!]])
  (:gen-class))


(defn on-tick [world]
  (if-let [projects (:projects world)]
    (let [new-projects (gitlab/update-projects (:config world) projects)
          _ (prn "looking for changes on tick")
          updates (gitlab/printable-diff (gitlab/diff-project-lists projects new-projects))]
      (doall (map (partial botty/broadcast-message! world) updates))
      (assoc world :projects new-projects))
    (do
      (println "please add some projects!")
      world)))

(defn on-command [world {:keys [type value from-nick reply-to] :as payload}]
  (case type
    :command
    (match (clojure.string/split (clojure.string/trim value) #"\s")
           ["groups"] (let [groups (gitlab/all-groups (:config world))
                            group-names (map :path groups)]
                        (botty/send-message! world reply-to (str "known groups: " (clojure.string/join " " group-names)))
                        world)
           ["projects" g] (do
                            (if-let [group-id (gitlab/group-id (:config world) g)]
                                (do
                                  (let [projects (gitlab/group-projects (:config world) group-id)]
                                    (doseq [project projects]
                                      (botty/send-message! world reply-to (str (:path_with_namespace project) " : " (:web_url project)))
                                      )
                                    )
                                  )
                                (botty/send-message! world reply-to (str "no group with name " g " visible to current user")))
                            world)
           ["watch" p]  (if-let [project (gitlab/project-by-name (:config world) p)]
                            (let [summary (gitlab/summary-with-last-commit (:config world) project)]
                                (assoc-in world [:projects (:id project)] summary))
                            (do (botty/send-message! world reply-to (str "no project with name " p " visible to current user - use name as listed in 'projects' list, with group prefix!"))
                                world))
           ["quit"] (do
                      (botty/broadcast-message! world (str "killed by request from " from-nick))
                      (put! (:killer world) (str "killed by request from " from-nick))
                      world)
           ["help"] (do (doall (map
                                 (partial botty/send-message! world reply-to)
                                 ["send message via 'gitlabbot:cmd or /msg gitlabbit cmd"
                                  "commands:"
                                  "'help' for this message"
                                  "'quit' to quit - you'll have to re-start from the commandline!"
                                  "'groups' to list known groups"
                                  "'projects' [groupname] to list projects in a group"
                                  "'watch group-name/project-name' to watch a project"
                                  "'watch-group group-name' to watch all projects in a group - NOT YET WORKING"
                                  " ... there is no un-watch command yet :)"]))
                        world)
           :else (do (botty/send-message! world reply-to (str "Unknown command:" value " - try 'help'"))
                     world))))

(def default-config {:api-base "http://192.168.59.103:10080/api/v3/"
                     :token "86PGosueNcCoRFxykM1z"
                     :botname "gitlabbot"
                     :irchost "localhost"
                     :ircport 6667
                     :tick-ms 10000
                     :channels ["#general"]})

(defn -main [& args]
  (let [config (case (count args)
                 0 default-config
                 1 (edn/read-string (slurp (first args)))
                 (throw (Exception. "please specify a config file, or no args for dev-only defaults")))
        wait-for-death-fn (botty/irc-loop config {:on-tick on-tick
                                       :on-command on-command})]
    (prn "waiting to die.")
    (wait-for-death-fn)
    (prn "done!")))