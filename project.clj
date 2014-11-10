(defproject gitlabbot "0.1.0-SNAPSHOT"
  :description "Basic IRC cctray build Bot"
  :license {:name "Do What The Fuck You Want To Public License (WTFPL)"
            :url "http://www.wtfpl.net/"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [irclj "0.5.0-alpha4"]
                 [midje "1.6.3"]
                 [overtone/at-at "1.2.0"]
                 [org.clojure/data.xml "0.0.8"]
                 [org.clojure/data.zip "0.1.1"]
                 [org.clojure/core.match "0.2.2"]
                 [clj-http "1.0.0"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]]
  :main gitlabbot.core
  :profiles {:uberjar {:main gitlabbot.core
                       :aot :all}})
