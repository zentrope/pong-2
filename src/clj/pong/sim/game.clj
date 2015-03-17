(ns pong.sim.game
  (:require
   [aleph.http :as http]
   [manifold.stream :as s]
   [clojure.core.async :refer [go timeout <!]]
   [clojure.edn :as edn]))

(def lock (promise))

;;-----------------------------------------------------------------------------

(defmulti dispatch!
  (fn [client event] (:event event)))

(defmethod dispatch! :default
  [{:keys [player session] :as client} event]
  (println "recv:[" player "] ??? -> " (pr-str event)))

(defmethod dispatch! :session
  [{:keys [player session] :as client} event]
  (reset! session (:session event))
  (println "stat:[" player "] ~ set session to: " @session))

(defmethod dispatch! :join
  [_ _]
  ;; squelch
  )

(defmethod dispatch! :telemetry
  [_ _]
  ;; squelch
  )

;;-----------------------------------------------------------------------------

(defn recv-loop!
  [{:keys [stream session player] :as client}]
  (go (loop []
        (when-let [msg (edn/read-string @(s/take! @stream))]
          (println "recv:[" player "]" (pr-str msg))
          (dispatch! client msg)
          (recur)))
      (println "stat:[" player "] terminating recv listener")
      (deliver lock :release)))

;;-----------------------------------------------------------------------------

(defn start!
  [{:keys [stream session player] :as client}]
  (println "stat:[" player "] starting")
  (reset! stream @(http/websocket-client "ws://localhost:3001/ws"))
  (recv-loop! client)
  @(s/put! @stream (pr-str {:event :session :id :game})))

(defn stop!
  [{:keys [stream session player] :as client}]
  (println "stat:[" player "] stopping")
  (.close @stream))

(defn make-client
  []
  {:stream (atom nil)
   :session (atom nil)
   :player :game})

(defn -main
  [& args]
  (println "Game Simulator (for protocol development)")
  (try
    (let [client (make-client)]
      (start! client)
      @lock
      (stop! client))
    (catch Throwable t
      (println "ERROR:" t))))
