(ns pong.sim.game
  (:require
   [aleph.http :as http]
   [manifold.stream :as s]
   [clojure.core.async :refer [go timeout <!]]
   [clojure.edn :as edn]))

(def lock (promise))

;;-----------------------------------------------------------------------------

(def state
  (atom {:players #{}
         :hits {}}))

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
  [{:keys [player session] :as client} event]
  (println (:id event) " has joined the game")
  (swap! state (fn [s] (-> (update-in s [:players] #(conj % (:id event)))
                          (update-in [:hits] #(assoc % (:id event) 0)))))
  (println "STATE:" @state))

(defmethod dispatch! :disconnect
  [client event]
  (let [{:keys [stream session player]} client]
    (swap! state (fn [s] (-> (update-in s [:players] #(disj % (:id event)))
                            (update-in [:hits] #(dissoc % (:id event))))))
    @(s/put! @stream (pr-str {:event :gameover :id player :session @session}))
    (println "STATE:" @state)))

(defmethod dispatch! :telemetry
  [{:keys [player] :as client} event]
  (swap! state update-in [:hits (:id event)] #(inc %))
  (println "STATE:" @state)
  ;;(println "recv:" event)
  ;; squelch

  ;; When one of the players reaches 10, send the :gameover
  ;; screen.
  )

;;-----------------------------------------------------------------------------

(defn recv-loop!
  [{:keys [stream session player] :as client}]
  (go (loop []
        (when-let [msg (edn/read-string @(s/take! @stream))]
          (println "recv:[" player "]" (pr-str msg))
          (try (dispatch! client msg)
               (catch Throwable t
                 (println "RECV.ERROR:" t)))
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
