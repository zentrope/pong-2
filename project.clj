(defproject com.zentrope/pong-2 "1"

  :description "Multiplayer Pong"
  :url "http://zentrope.com"

  :license
  {:name "Eclipse Public License"
   :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies
  [[org.clojure/clojure "1.7.0-alpha5"]
   [aleph "0.4.0-beta3"]
   [clout "2.1.0"]]

  :source-paths ["src/clj"]

  :main ^:skip-aot pong.main

  :profiles {:uberjar {:aot :all}})
