(ns pong.paddle
  (:require-macros [cljs.core.async.macros :refer [go-loop]])
  (:require [cljs.core.async :refer [put! chan]]
            [cljs.reader :as reader]))

(enable-console-print!)

(def paddle-height 200)
(def paddle-radius (int (/ paddle-height 2)))
(def paddle-width 50)
(def paddle-offset (int (/ paddle-width 2)))

(def paddle-color {1 "peru" 2 "dodgerblue"})

;;-----------------------------------------------------------------------------
;; Socket
;;-----------------------------------------------------------------------------

(defn- sk-start!
  [{:keys [url ws ch] :as socket}]
  (let [sock (js/WebSocket. url)]
    (aset sock "onerror"   #(put! ch {:event :socket-error :err %}))
    (aset sock "onmessage" #(put! ch (reader/read-string (.-data %))))
    (aset sock "onclose"   #(put! ch  {:event :socket-close}))
    (aset sock "onopen"    #(put! ch {:event :socket-open}))
    (reset! ws sock)))

(defn- sk-stop!
  [{:keys [url ws ch] :as socket}]
  (aset @ws "onclose" nil)
  (.-close @ws)
  (reset! ws nil))

(defn- sk-send!
  [{:keys [url ws ch] :as socket} msg]
  (try
    (.send @ws msg)
    (catch js/Error e
      (println "socket-exception:" e)
      (put! ch {:event :socket-exception :err e}))))

(defn- socket!
  [event-ch]
  (let [host (.-hostname (.-location js/window))
        port (.-port (.-location js/window))]
    {:url (str "ws://" host ":" port "/ws")
     :ws (atom nil)
     :ch event-ch}))

;;-----------------------------------------------------------------------------
;; DOM
;;-----------------------------------------------------------------------------

(defn- px
  [v]
  (str v "px"))

(defn- by-id
  [id]
  (.getElementById js/document id))

(defn- listen!
  [el type fn]
  (.addEventListener el type fn false))

(defn- set-html!
  [el html]
  (aset el "innerHTML" html))

(defn- set-css!
  [el attr val]
  (-> (aget el "style")
      (aset attr val)))

;;-----------------------------------------------------------------------------
;; Math
;;-----------------------------------------------------------------------------

(defn- place
  [y]
  (let [h (.-innerHeight js/window)]
    (js/Math.round (* 100 (/ y h)))))

;;-----------------------------------------------------------------------------
;; Events
;;-----------------------------------------------------------------------------

(defn- paddle-move!
  [e {:keys [ws id session]}]
  (let [x (.-clientX e)
        y (.-clientY e)
        p (by-id "paddle")
        coord (place y)
        m {:id id :session session :event :telemetry :y coord}]
    (sk-send! ws (pr-str m))
    (set-css! p "top" (px (- y paddle-radius)))
    (set-html! (by-id "debug") (pr-str m))))

(defn window-resize!
  []
  (let [w (.-innerWidth js/window)
        h (.-innerHeight js/window)
        p (by-id "paddle")
        midway (px (- (int (/ w 2)) paddle-offset))]
    (set-css! p "left" midway)))

;;-----------------------------------------------------------------------------
;; State
;;-----------------------------------------------------------------------------

(defonce state
  (atom {:ws nil
         :id :player-1
         :session "g13995"
         :event-ch nil}))

;;-----------------------------------------------------------------------------
;; Comm
;;-----------------------------------------------------------------------------

(defmulti handle!
  (fn [state msg]
    (:event msg)))

(defmethod handle! :default
  [state msg])

(defmethod handle! :error
  [state msg]
  (println "ERROR:" (pr-str msg)))

(defmethod handle! :socket-open
  [state msg]
  (sk-send! (:ws @state)
            {:event :join :session (:session @state) :id (:id @state)}))

(defn- events!
  [state]
  (go-loop []
    (when-let [msg (<! (:event-ch @state))]
      (println "event:" (pr-str msg))
      (try
        (handle! state msg)
        (catch js/Error e
          (handle! state {:event :error
                          :error 1
                          :msg msg})))
      (recur))))

;;-----------------------------------------------------------------------------
;; Boot
;;-----------------------------------------------------------------------------

(defn- main
  [pid]

  (window-resize!)

  (let [event-ch (chan)
        socket (socket! event-ch)]

    (sk-start! socket)
    (swap! state merge {:ws socket
                        :event-ch event-ch
                        :id (keyword (str "player-" pid))})

    (events! state)



    (-> (by-id "paddle")
        (set-css! "background-color" (get paddle-color pid)))

    (listen! js/window "mousemove" #(paddle-move! % @state))
    (listen! js/window "resize" #(window-resize!))))

(defn ^:export start
  [paddle-num]
  (println "Welcome to Paddle" paddle-num)
  (main paddle-num))
