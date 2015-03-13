(ns pong.client
  (:require
   [aleph.http :as http]
   [manifold.stream :as s]
   [clojure.core.async :refer [go-loop timeout <!]]))

(defn test-loop!
  [stream]
  (go-loop [x 1]
    (<! (timeout 2000))
    (s/put! stream (pr-str [:value x]))
    (recur (inc x))))

(defn -main
  [& args]
  (println "Welcome to the client.")
  (try
    (let [lock (promise)
          stream @(http/websocket-client "ws://localhost:3001/ws")]
      (test-loop! stream)
      @lock)
    (catch Throwable t
      (println "ERROR:" t))))
