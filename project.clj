(defproject com.zentrope/pong-2 "1"

  :description "Multiplayer Pong"
  :url "http://zentrope.com"

  :license
  {:name "EPL" :url "http://bit.ly/1EXoLjp"}

  :dependencies
  [[org.clojure/clojure "1.7.0-alpha5"]
   [org.clojure/core.async "0.1.346.0-17112a-alpha"]
   [aleph "0.4.0-beta3"]
   [clout "2.1.0"]
   [org.clojure/data.priority-map "0.0.6"]]

  :source-paths ["src/clj"]

  :main ^:skip-aot pong.main

  :global-vars {*warn-on-reflection* false}

  :aliases {"game" ["run" "-m" "pong.sim.game"]
            "player1" ["run" "-m" "pong.sim.controller" "player1"]
            "player2" ["run" "-m" "pong.sim.controller" "player2"]}

  :profiles {:uberjar
             {:aot :all}

             :dev
             {:dependencies
              [[org.clojure/tools.nrepl "0.2.8"]] ;; override lein
              :plugins
              [[lein-ancient "0.6.5"]
               [cider/cider-nrepl "0.9.0-SNAPSHOT"]]}})
