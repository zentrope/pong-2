(ns pong.main
  (:require
   [aleph.http :as http]
   [clojure.core.async :refer [go]]
   [clojure.java.io :as io]
   [clojure.pprint :refer [pprint]]
   [clout.core :as clout]
   [manifold.stream :as s])
  (:gen-class))

;;-----------------------------------------------------------------------------
;; Utilities
;;-----------------------------------------------------------------------------

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

;; The idea (eventually) is to have controllers log in to a certain
;; chat room so that the app can serve lots of simultaneous
;; games. When you bring up the game, it should present a code. Use
;; the code in the controller to attach to the correct game.

(defn chat-loop!
  [req]
  (let [stream @(http/websocket-connection req)]
    (go (loop []
          (when-let [message @(s/take! stream)]
            (println " recv:" message)
            (recur)))
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
