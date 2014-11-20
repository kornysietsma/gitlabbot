(ns gitlabbot.core
  (:require [botty.core :as botty]
            [irclj.core :as irclj]
            [clojure.pprint :refer [pprint]]
            [clj-http.client :as http]
            [overtone.at-at :as at-at]
            [gitlabbot.config :as c]
            [gitlabbot.gitlab :as gitlab]
            [clojure.core.async :refer [chan go-loop >! <! timeout alt! put! <!!] :as async]
            [clojure.tools.reader.edn :as edn])
  (:gen-class))


(defn on-tick [world]
  (if-let [projects (:projects world)]
    (let [new-projects (gitlab/update-projects (:config world) projects)
          _ (prn "loaded new projects on tick")
          updates (gitlab/printable-diff (gitlab/diff-project-lists projects new-projects))]
      (doall (map (partial botty/broadcast-message! world) updates))
      (assoc world :projects new-projects))
    (do
      (println "first time - getting initial projects...")
      (assoc world :projects (gitlab/initial-project-data (:config world)))))
  )

(defn on-command [world {:keys [type value from-nick reply-to] :as payload}]
  (case type
    :command
    (case (clojure.string/trim value)
      "quit" (do
               (botty/broadcast-message! world (str "killed by request from " from-nick))
               (put! (:killer world) (str "killed by request from " from-nick)))
      "help" (botty/send-message! world reply-to (str
                                             "send message via 'gitlab:cmd' or msg gitlabbot with 'cmd'"
                                             "commands: 'help' and 'quit' only!"))
      (botty/send-message! world reply-to (str "Unknown command:" value " - try 'help'"))))
  world)

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
        killer (botty/irc-loop config {:on-tick on-tick
                                       :on-command on-command})]
    (prn "waiting to die.")
    (println (<!! killer))
    (prn "done!")))