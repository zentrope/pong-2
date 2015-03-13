(ns pong.main
  (:require
   [aleph.http :as http]
   [manifold.stream :as s]
   [clout.core :as clout]
   [clojure.pprint :refer [pprint]]
   [clojure.core.async :refer [go-loop]])
  (:gen-class))

;;-----

(defn response
  ([]
   {:status 200})
  ([body]
   {:status 200 :body ""}))

(defn header
  [response k v]
  (assoc-in response [:headers k] v))

(defn content
  [response type]
  (header response "content-type" type))

(defn status
  [response status]
  (assoc response :status status))

;;-----

(defn ping
  [req]
  (println "- ping")
  (pprint req)
  (-> (response)
      (content "text/plain")
      (assoc :body "pong")))

;; The idea (eventually) is to have controllers log in to a certain
;; chat room so that the app can serve lots of simultaneous
;; games. When you bring up the game, it should present a code. Use
;; the code in the player to attach to the correct game.

(defn chat
  [req]
  (println "- chat")
  (let [stream @(http/websocket-connection req)]
    (go-loop []
      (when-let [message @(s/take! stream)]
        (println " recv:" message)
        (recur)))))

(defn not-found
  [_]
  (-> (response)
      (status 404)))

;;-----

(defn mid
  [h]
  (fn [request]
    (h (assoc request :keith "irwin"))))

(defn real
  [h]
  (fn [{:keys [body] :as request}]
    (h (if-not (nil? body)
         (assoc request :body (if (string? body) body (slurp body)))
         request))))

(def routes
  ;;
  ;; Declarative routing.
  ;;
  [{:route "/ping"
    :method :get
    :middleware (comp mid real)
    :handler ping}
   {:route "/ws"
    :method :get
    :handler chat}])

;;-----

(defn dispatch
  [routes req]
  ;; Reduce might work better. Return a list of [params route-def].
  (loop [routes (filter #(= (:request-method req) (:method %)) routes)]
    (if-let [{:keys [route handler middleware]} (first routes)]
      (if (clout/route-matches route req)
        (if middleware
          ((middleware handler) req)
          (handler req))
        (recur (rest routes)))
      (not-found req))))

;;-----

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
