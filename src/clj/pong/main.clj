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

(defonce state (atom {}))

(add-watch state :debug
           (fn [_ _ old new]
             (println "--")
             (pprint new)
             (println "--")))

;;-----------------------------------------------------------------------------

(defn create-session!
  [state session-id role-id stream]
  (swap! state assoc session-id {role-id stream}))

(defn in-session?
  [state session-id]
  (not (nil? (get @state session-id))))

(defn move-session!
  [state from-session to-session client-key]
  ;; placeholder
  )

(defn close-session!
  [state session-id]
  (when-let [session (get @state session-id)]
    (swap! state dissoc session-id)
    (doseq [stream (vals session)]
      (.close stream))))

;;-----------------------------------------------------------------------------

(defmulti do-event!
  (fn [session-id stream event]
    (:event event)))

(defmethod do-event! :default
  [session-id stream event]
  (println "chat> [unhandled-event]" (pr-str event)))

(defmethod do-event! :session
  [session-id stream event]
  (let [new-event (assoc event :session session-id)]
    (if @(stream/put! stream (pr-str new-event))
      (println "send:" new-event)
      (println "send: [fail]" new-event))))

;;-----------------------------------------------------------------------------

(defn chat-loop!
  [req]
  (let [stream @(http/websocket-connection req)
        session-id (uuid)]
    (go (loop []
          (when-let [event (edn/read-string @(stream/take! stream))]
            (try

              (when-not (in-session? state session-id)
                (create-session! state session-id (:id event) stream))

              (do-event! session-id stream event)

              (catch Throwable t
                (do-event! session-id
                           stream
                           {:event :exception :msg event :exception t})))
            (recur)))
        (close-session! state session-id)
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
