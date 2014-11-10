(ns gitlabbot.config
  (:require [clojure.tools.reader.edn :as edn]))

(def default-config {:api-base "http://192.168.59.103:10080/api/v3/"
             :token "86PGosueNcCoRFxykM1z"
             :botname "gitlabbot"
             :irchost "localhost"
             :ircport 6667
             :channel "#general"})

(defn read-config [filename]
  (edn/read-string (slurp filename)))
