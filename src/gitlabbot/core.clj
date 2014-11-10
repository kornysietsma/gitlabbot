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


(defn message! [world nick msg]
  (put! (:in world) {:cmd :write :nick nick :message msg}))

(defn quit! [world reason]
  (put! (:killer world) reason))

(defn do-command [world text nick reply-to]
  (println "command:" text nick reply-to)
  (case text
    "quit" (quit! world (str "request from " nick))
    "help" (message! world reply-to (str
                                                   "send message via 'gitlab:cmd' or msg gitlabbot with 'cmd'"
                                                   "commands: 'help' and 'quit' only!"))
    (message! world reply-to (str "unknown command: " text " - try help"))))

(defn send-help [world nick]
  (message! world nick "try \"/msg gitlabbot help\" or \"gitlabbot: help\" from a channel the bot is in"))

(defn parse-command [botname text target]
  (when-let [match (if (= botname target)
                     (re-matches #"(.*)" text)
                     (re-matches #"gitlabbot:(.*)" text))]
    (clojure.string/trim (second match))))

(defn privmsg
  "send a private message - takes a world with an :in channel for comms"
  [world raw-irc {:keys [nick text target] :as data}]
  ; nick is the sender, target is the channel or botname if private
  (println "privmsg:" nick text)
  (let [botname (get-in world [:config :botname])
        reply-to (if (= target botname)
                   nick
                   target)]
    (if-let [command (parse-command botname text target)]
      (do-command world command nick reply-to)
      (if (= botname target)                                ; unknown message to bot
        (send-help (:in world) nick)))))

(defn report [irc message]
  (irclj/message (:connection irc) (get-in irc [:config :channel]) message))

(defn irc-tick [irc]
  (try
    (if-let [projects (:projects irc)]
      (let [new-projects (gitlab/update-projects (:config irc) projects)
            updates (gitlab/printable-diff (gitlab/diff-project-lists projects new-projects))]
        (doall (map (partial report irc) updates))
        (assoc irc :projects new-projects))
      (assoc irc :projects (gitlab/initial-project-data (:config irc))))
    (catch Exception e
      (do
        (println "caught exception:" e)
        (clojure.stacktrace/print-stack-trace e)
        irc)
      )))

(defn irc-command [data command]
  (case (:cmd command)
    :write (do  (println "connection:" (:connection data) " command:" command)
             (irclj/message
                 (:connection data)
                 (:nick command)
                 (:message command)))
    (println "Unknown command:" command " data:" data))
  data)


(defn callbacks [world] {:privmsg (partial privmsg world)})

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
  (let [killer (chan)
        in (chan)]
    (go-loop [data (connect initial-config in killer)]
      (alt!
        in ([command]
              (recur (irc-command data command)))
        killer ([reason] (do
                           (irclj/quit (:connection data))
                           (println "killed because:" reason)))
        (timeout 1000) (recur (irc-tick data))))
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