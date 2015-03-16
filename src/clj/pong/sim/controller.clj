(ns pong.sim.controller
  (:require
   [aleph.http :as http]
   [manifold.stream :as s]
   [clojure.core.async :refer [go timeout <!]]
   [clojure.edn :as edn]))


(def lock (promise))

(defprotocol LifeCycle
  (start! [_])
  (stop! [_]))

(defn send-loop!
  [stream player-id session-id]
  (let [stub {:id player-id :session session-id :event :telemetry}]
    (go (loop [y 10]
          (<! (timeout 2000))
          (when (> y 0)
            (let [msg (pr-str (assoc stub :y y))]
              (when @(s/put! stream msg)
                (println "send:[" player-id "]" msg)
                (recur (dec y))))))
        (println "stat:[" player-id "] sends terminated")
        (deliver lock :release))))

(defn recv-loop!
  [stream player-id]
  (go (loop []
        (when-let [msg (edn/read-string @(s/take! stream))]
          (println "recv:[" player-id "]" (pr-str msg))
          (recur)))
      (println "stat:[" player-id "] terminating recv listener")
      (deliver lock :release)))

(defrecord ControllerClient [stream player-id session-id]

  LifeCycle

  (start! [this]
    (println "stat:[" player-id "] starting")
    (reset! stream @(http/websocket-client "ws://localhost:3001/ws"))
    (recv-loop! @stream player-id)
    (send-loop! @stream player-id session-id)
    (when-not @(s/put! @stream
                       (pr-str {:event :join :id player-id :session session-id}))
      (println "stat:[" player-id "] no route to server")
      (stop! this)))

  (stop! [_]
    (println "stat:[" player-id "] stopping")
    (.close @stream)))

(defn make-controller
  [player-id session-id]
  (ControllerClient. (atom nil) player-id session-id))

(defn -main
  [& args]
  (println "Controller Simulation")
  (let [[player-id session-id] args
        player-id (keyword player-id)]
   (println " - player-id: " player-id)
   (println " - session-id:" session-id)

   (try
     (let [controller (make-controller player-id session-id)]
       (start! controller)
       @lock
       (stop! controller))
     (catch Throwable t
       (println "ERROR:" t)))))
