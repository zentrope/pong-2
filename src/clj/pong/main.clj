(ns pong.main
  (:require
   [aleph.http :as http]
   [clojure.core.async :refer [go]]
   [clojure.java.io :as io]
   [clojure.pprint :refer [pprint]]
   [clojure.edn :as edn]
   [clojure.string :as s]
   [clout.core :as clout]
   [manifold.stream :as stream])
  (:gen-class))

;;-----------------------------------------------------------------------------
;; Utilities
;;-----------------------------------------------------------------------------

(defn uuid
  []
  (-> (gensym) str (s/replace "__" "") s/lower-case))

(defn mime-for
  [file]
  (let [path (.getPath file)]
    (cond
      (.endsWith path "css") "text/css"
      (.endsWith path "html") "text/html"
      (.endsWith path "js") "application/javascript"
      :else "text/plain")))

;;-----------------------------------------------------------------------------
;; Chat
;;-----------------------------------------------------------------------------

(defonce state
  (atom {}))

(add-watch state :debug
           (fn [_ _ _ new]
             (pprint {:state (reduce (fn [a [k v]]
                                       (assoc a (str k) v)) {} new)})))

;;-----------------------------------------------------------------------------

(defn ensure-session!
  [state stream sid]
  (when-not (or (nil? sid)
                (= (get @state stream) sid))
    (swap! state #(-> (dissoc % stream)
                      (assoc stream sid)))))

(defn participants
  [state session]
  (->> @state
       (filterv (fn [[s sid]] (= session sid)))
       keys))

(defn publish!
  [state origin event]
  (doseq [p (participants state (:session event))]
    (when-not (= p origin)
      @(stream/put! p (pr-str event)))))

(defn disconnect!
  [state stream]
  (swap! state dissoc stream)
  (.close stream))

;;-----------------------------------------------------------------------------

(defmulti dispatch-event!
  (fn [state stream event]
    (:event event)))

(defmethod dispatch-event! :default
  [state stream event]
  (println "chat> [unhandled-event]" (pr-str event)))

(defmethod dispatch-event! :join
  [state stream event]
  (publish! state stream event))

(defmethod dispatch-event! :telemetry
  [state stream event]
  (publish! state stream event))

(defmethod dispatch-event! :session
  [state stream event]
  (let [sid (uuid)
        new-event (assoc event :session sid)]
    (if @(stream/put! stream (pr-str new-event))
      (println "send:" new-event)
      (println "send: [fail]" new-event))))

;;-----------------------------------------------------------------------------

(defn chat-loop!
  [req]
  (let [stream @(http/websocket-connection req)]
    (go (loop []
          (when-let [event (edn/read-string @(stream/take! stream))]
            (try
              (ensure-session! state stream (:session event))
              (dispatch-event! state stream event)
              (catch Throwable t
                (dispatch-event! stream {:event :exception
                                         :msg event
                                         :exception t})))
            (recur)))
        (disconnect! state stream)
        (println "- chat terminated"))))

;;-----------------------------------------------------------------------------
;; Handlers
;;-----------------------------------------------------------------------------

(defn game-board
  [req]
  {:status 200
   :headers {"content-type" "text/html"}
   :body (slurp (io/resource "www/index.html"))})

(defn game-controller
  [player req]
  (let [doc (format "www/p%s.html" player)]
    {:status 200
     :headers {"content-type" "text/html"}
     :body (slurp (io/resource doc))}))

(defn chat
  [req]
  (println "- chat")
  (chat-loop! req))

(defn not-found
  [_]
  {:status 404
   :headers {"content-type" "text/plain"}
   :body "Not found"})

(defn resources
  [req]
  (let [doc (io/file (io/resource (str "www/" (:uri req))))]
    (if (and doc (.exists doc))
      {:status 200
       :headers {"content-type" (mime-for doc)}
       :body (slurp doc)}
      (not-found req))))

;;-----------------------------------------------------------------------------
;; Routing
;;-----------------------------------------------------------------------------

(def routes
  [{:route "/"   :method :get :handler game-board}
   {:route "/p1" :method :get :handler (partial game-controller 1)}
   {:route "/p2" :method :get :handler (partial game-controller 2)}
   {:route "/ws" :method :get :handler chat}])

(defn dispatch
  [routes req]
  (loop [routes (filter #(= (:request-method req) (:method %)) routes)]
    (if-let [{:keys [route handler middleware]} (first routes)]
      (if (clout/route-matches route req)
        (if middleware
          ((middleware handler) req)
          (handler req))
        (recur (rest routes)))
      (resources req))))

;;-----------------------------------------------------------------------------
;; System
;;-----------------------------------------------------------------------------

(defn- hook-shutdown!
  [f]
  (doto (Runtime/getRuntime)
    (.addShutdownHook (Thread. f))))

(defn -main
  [& args]
  (println "Welcome to the Pong Server")
  (let [lock (promise)
        server (http/start-server #(dispatch routes %) {:port 3001})]
    (hook-shutdown! #(println "Shutting down."))
    (hook-shutdown! #(.close server))
    (hook-shutdown! #(deliver lock :release))
    @lock))
