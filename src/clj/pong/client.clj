(ns pong.client
  (:require
   [aleph.http :as http]
   [manifold.stream :as s]
   [clojure.core.async :refer [go timeout <!]]
   [clojure.edn :as edn]))

(def session-id (atom nil))

(defn test-loop!
  [stream lock]
  (s/put! stream (pr-str {:event :session :id :game}))

  (go (loop []
        (when-let [msg (edn/read-string @(s/take! stream))]
          (println "recv:" (pr-str msg))
          (case (:event msg)
            :session (reset! session-id (:session msg))
            nil)
          (recur)))
      (println "stat: test recv loop terminated"))

  (go (loop [x 1]
        (<! (timeout 2000))
        (let [msg {:event :telemetry :session @session-id :id :test :y x}]
          (when @(s/put! stream (pr-str msg))
            (println "send:" msg)
            (recur (inc x)))))
      (println "stat: send loop terminated")
      (deliver lock :release)))

(defn -main
  [& args]
  (println "Welcome to the client.")
  (try
    (let [lock (promise)
          stream @(http/websocket-client "ws://localhost:3001/ws")]
      (test-loop! stream lock)
      @lock)
    (catch Throwable t
      (println "ERROR:" t))))
