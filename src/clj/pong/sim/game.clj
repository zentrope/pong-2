(ns pong.sim.game
  (:require
   [aleph.http :as http]
   [manifold.stream :as s]
   [clojure.core.async :refer [go timeout <!]]
   [clojure.edn :as edn]))

(def lock (promise))

;;-----------------------------------------------------------------------------

(def default-state
  {:players #{}
   :hits {}})

(def state
  (atom default-state))

(defn reset-state!
  []
  (reset! state default-state))

(add-watch state :debug (fn [_ _ _ n]
                          (println "STATE:" n)))

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
  [{:keys [stream player session] :as client} event]
  (println (:id event) " has joined the game")
  (swap! state (fn [s] (-> (update-in s [:players] #(conj % (:id event)))
                          (update-in [:hits] #(assoc % (:id event) 0)))))
  (when (= 2 (count (:players @state)))
    @(s/put! @stream (pr-str {:event :gamestart :id player :session @session}))))

(defmethod dispatch! :disconnect
  [client event]
  (let [{:keys [stream session player]} client]
    (reset-state!)
    @(s/put! @stream (pr-str {:event :gameover :id player :session @session}))))

(defmethod dispatch! :telemetry
  [{:keys [player stream session] :as client} event]
  (swap! state update-in [:hits (:id event)] #(when % (inc %)))
  (when-let [pid (some (fn [[k v]] (when (= v 5) k)) (:hits @state))]
    (reset-state!)
    @(s/put! @stream (pr-str {:event :gameover :id player
                              :session @session :winner pid}))))

;;-----------------------------------------------------------------------------

(defn recv-loop!
  [{:keys [stream session player] :as client}]
  (go (loop []
        (when-let [msg (edn/read-string @(s/take! @stream))]
          (println "recv:[" player "]" (pr-str msg))
          (try (dispatch! client msg)
               (catch Throwable t
                 (println "RECV.ERROR:" t)
                 (when (instance? java.lang.NullPointerException t)
                   (clojure.stacktrace/print-stack-trace t))))
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
  (println "\nGame Simulator (for protocol development)")
  (try
    (let [client (make-client)]
      (start! client)
      @lock
      (stop! client))
    (catch Throwable t
      (println "ERROR:" t))))
