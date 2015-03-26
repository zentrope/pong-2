(ns pong.sim.controller
  (:require
   [aleph.http :as http]
   [manifold.stream :as s]
   [clojure.core.async :refer [go timeout <!]]
   [clojure.edn :as edn]))

(def lock (promise))


(defn send-telemetry!
  [stream stub value]
  (let [msg (pr-str (assoc stub :y value))]
    (when @(s/put! stream msg)
      (println "send:[" (:id stub) "]" msg))))

(defn send-loop!
  [stream pid sid]
  (let [stub {:id pid :session sid :event :telemetry}]
    (go (dotimes [y 10]
          (<! (timeout (rand-nth [1000 3000])))
          (send-telemetry! stream stub y))
        (deliver lock :release))))

(defmulti dispatch!
  (fn [stream pid msg]
    (:event msg)))

(defmethod dispatch! :default
  [stream pid msg])

(defmethod dispatch! :gamestart
  [stream pid msg]
  (send-loop! stream pid (:session msg)))

(defmethod dispatch! :disconnect
  [stream pid msg]
  (deliver lock :disconnect))

(defmethod dispatch! :gameover
  [stream pid msg]
  (deliver lock :gameover))

(defmethod dispatch! :err
  [stream pid msg]
  (println "  :: ERROR ::" msg))

(defn recv-loop!
  [stream pid]
  (go (loop []
        (when-let [msg @(s/take! stream)]
          (println "recv:[" pid "]" msg)
          (try
            (dispatch! stream pid (edn/read-string msg))
            (catch Throwable t
              (dispatch! stream pid {:event :err :error t :msg msg})))
          (recur)))
      (deliver lock :release)))

(defn start!
  [{:keys [pid sid stream] :as controller}]
  (reset! stream @(http/websocket-client "ws://localhost:3001/ws"))
  (recv-loop! @stream pid)
  @(s/put! @stream (pr-str {:event :join :id pid :session sid}))
  controller)

(defn stop!
  [{:keys [pid sid stream] :as controller}]
  (when @stream
    (.close @stream))
  (reset! stream nil)
  controller)

(defn controller
  [pid sid]
  {:pid pid :sid sid :stream (atom nil)})

(defn -main
  [& args]
  (println "\nController Simulation")
  (let [[player-id session-id] args
        player-id (keyword player-id)]
   (println " - player-id: " player-id)
   (println " - session-id:" session-id)

   (try
     (let [controller (controller player-id session-id)]
       (start! controller)
       @lock
       (stop! controller))
     (catch Throwable t
       (println "ERROR:" t)))))
