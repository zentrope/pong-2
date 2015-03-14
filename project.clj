(defproject com.zentrope/pong-2 "1"

  :description "Multiplayer Pong"
  :url "http://zentrope.com"

  :license
  {:name "EPL" :url "http://bit.ly/1EXoLjp"}

  :dependencies
  [[org.clojure/clojure "1.7.0-alpha5"]
   [org.clojure/core.async "0.1.346.0-17112a-alpha"]
   [aleph "0.4.0-beta3"]
   [clout "2.1.0"]]

  :plugins
  [[lein-ancient "0.6.5" :exclusions [org.clojure/clojure]]
   [cider/cider-nrepl "0.8.2"]]

  :source-paths ["src/clj"]

  :main ^:skip-aot pong.main

  :profiles {:uberjar {:aot :all}})
