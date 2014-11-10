(ns gitlabbot.core
  (:require [irclj.core :as irclj]
            [clojure.pprint :refer [pprint]]
            [clj-http.client :as http]
            [overtone.at-at :as at-at]
            [gitlabbot.config :as c]
            [gitlabbot.gitlab :as gitlab]
            [clojure.core.async :refer [chan go-loop >! <! timeout alt! put! <!!] :as async]
            )
  (:gen-class))

(defn parse-command [botname text target]
  (prn botname text target)
  (when-let [match (if (= botname target)
                     (re-matches #"(.*)" text)
                     (re-matches #"gitlabbot:(.*)" text))]
    (clojure.string/trim (second match))))

(defn privmsg-callback
  "handle a private message - takes a partially-constructed world with an :in channel for relaying commands"
  [world raw-irc {:keys [nick text target] :as data}]
  ; nick is the sender, target is the channel or botname if private
  (let [botname (get-in world [:config :botname])
        reply-to (if (= target botname)
                   nick
                   target)]
    (if-let [command (parse-command botname text target)]
      (put! (:in world) {:cmd      command
                         :nick     nick
                         :reply-to reply-to}))))

(defn send-message! [world target message]
  (irclj/message (:connection world) target message))

(defn irc-tick [world]
  (try
    (if-let [projects (:projects world)]
      (let [new-projects (gitlab/update-projects (:config world) projects)
            _ (prn "loaded new projects on tick")
            updates (gitlab/printable-diff (gitlab/diff-project-lists projects new-projects))
            target (get-in world [:config :channel])]
        (doall (map (partial send-message! world target) updates))
        (assoc world :projects new-projects))
      (do
        (println "first time - getting initial projects...")
        (assoc world :projects (gitlab/initial-project-data (:config world)))))
    (catch Exception e
      (do
        (println "caught exception:" e)
        (clojure.stacktrace/print-stack-trace e)
        world)
      )))

(defn irc-command [world {:keys [cmd nick reply-to] :as payload}]
  (case (clojure.string/trim cmd)
    "quit" (do
             (send-message! world (get-in world [:config :channel]) (str "killed by request from " nick))
             (put! (:killer world) (str "killed by request from " nick)))
    "help" (send-message! world reply-to (str
                                      "send message via 'gitlab:cmd' or msg gitlabbot with 'cmd'"
                                      "commands: 'help' and 'quit' only!"))
    (send-message! world reply-to (str "Unknown command:" cmd " - try 'help'")))
  world)


(defn callbacks [world] {:privmsg (partial privmsg-callback world)})

(defn connection [{:keys [config] :as world}]
  (irclj/connect
    (:irchost config)
    (:ircport config)
    (:botname config)
    :username (:botname config)
    :realname (:botname config)
    :callbacks (callbacks world)
    :auto-reconnect-delay-mins 1 ; reconnect delay after disconnect
    :timeout-mins 20 ; socket timeout - length of time to keep socket open when nothing happens
    ))

(defn connect [initial-config in killer]
  (let [world (merge {:config initial-config}
                     {:in in
                      :killer killer
                      :projects nil})
        conn (connection world)
        _ (println "joining" (:channel initial-config) "via" conn)
        _ (irclj/join conn (:channel initial-config))]
    (merge world {:connection conn})
    ))

(defn irc-loop
  "core.async based main loop" [initial-config]
  (let [in (chan)
        killer (chan)]
    (go-loop [data (connect initial-config in killer)]
      (alt!
        in ([command]
            (recur (irc-command data command)))
        killer ([reason] (do
                           (irclj/quit (:connection data))
                           (println "killed because:" reason)))
        (timeout 10000) (recur (irc-tick data))))
    killer))

(comment "for repling"
  (def k (irc-loop c/default-config))
  (put! k "die")
)

(defn -main [& args]
  (let [config (case (count args)
                 0 c/default-config
                 1 (c/read-config (first args))
                 (throw (Exception. "please specify a config file, or no args for dev-only defaults")))
        killer (irc-loop config)]
    (prn "waiting to die.")
    (println (<!! killer))
    (prn "done!")))