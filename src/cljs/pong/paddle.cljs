(ns pong.paddle
  (:require-macros [cljs.core.async.macros :refer [go-loop]])
  (:require [cljs.core.async :refer [put! chan]]
            [cljs.reader :as reader]
            [pong.lib.dom :as dom]
            [pong.lib.socket :as sk]))

(enable-console-print!)

(def paddle-height 200)
(def paddle-radius (int (/ paddle-height 2)))
(def paddle-width 50)
(def paddle-offset (int (/ paddle-width 2)))

(def paddle-color {1 "peru" 2 "dodgerblue"})

;;-----------------------------------------------------------------------------
;; Renders
;;-----------------------------------------------------------------------------

(defmulti view-mode
  (fn [state] (:mode state)) :default :prejoin)

(defmethod view-mode :wait
  [state]
  (dom/show! (dom/by-id "session-button")))

(defmethod view-mode :prejoin
  [state]
  (dom/show! (dom/by-id "session-button"))
  (dom/hide! (dom/by-id "paddle"))
  (dom/show! (dom/by-id "session-form"))
  (dom/focus! (dom/by-id "session")))

(defmethod view-mode :playing
  [state]
  (dom/hide! (dom/by-id "session-button"))
  (dom/hide! (dom/by-id "session-form"))
  (dom/show! (dom/by-id "paddle")))

;;-----------------------------------------------------------------------------
;; Events
;;-----------------------------------------------------------------------------

(defn- paddle-move!
  [e {:keys [ws id session mode]}]
  (when (= mode :playing)
    (let [y (.-clientY e)
          p (dom/by-id "paddle")
          coord (js/Math.round (* 100 (/ y (.-innerHeight js/window))))
          m {:id id :session session :event :telemetry :y coord}]
      (sk/send! ws (pr-str m))
      (dom/set-css! p "top" (dom/px (- y paddle-radius)))
      (dom/set-html! (dom/by-id "debug") (pr-str m)))))

(defn- join-button!
  [e event-ch]
  (put! event-ch {:event :client/join}))

(defn- session-form!
  [e event-ch]
  (cond
    (= (.-keyCode e) 13)
    (put! event-ch {:event :client/join})
    ;;
    (= (.-keyCode e) 27)
    (do (aset (.-target e) "value" "")
        (put! event-ch {:event :client/session :value ""}))
    ;;
    :else
    (put! event-ch {:event :client/session :value (.-value (.-target e))})))

(defn window-resize!
  []
  (let [w (.-innerWidth js/window)
        h (.-innerHeight js/window)
        p (dom/by-id "paddle")
        midway (dom/px (- (int (/ w 2)) paddle-offset))]
    (dom/set-css! p "left" midway)))

;;-----------------------------------------------------------------------------
;; State
;;-----------------------------------------------------------------------------

(defonce state
  (atom {:ws nil
         :id :player-1
         :session "-"
         :mode :prejoin ;; :wait :playing
         :event-ch nil}))

#_(add-watch state :debug (fn [_ _ _ n]
                            (println "state:" (pr-str n))))

;;-----------------------------------------------------------------------------
;; Comm
;;-----------------------------------------------------------------------------

(defmulti handle!
  (fn [state msg]
      (println "event:" (pr-str msg))
      (:event msg)))

(defmethod handle! :default
  [state msg])

(defmethod handle! :disconnect
  [state msg]
  (swap! state assoc :mode :prejoin)
  (view-mode @state))

(defmethod handle! :client/session
  [state msg]
  (println :new-session)
  (swap! state assoc :session (:value msg)))

(defmethod handle! :client/join
  [state msg]
  (let [{:keys [session id ws]} @state]
    (sk/send! ws {:event :join :session session :id id})
    (swap! state assoc :mode :playing)
    (view-mode @state)))

(defmethod handle! :client/error
  [state msg]
  (println "ERROR:" (pr-str msg)))

(defmethod handle! :socket/exception
  [state msg]
  (println "ERROR:" (pr-str msg)))

(defmethod handle! :socket/open
  [state msg])

(defn- events!
  [state]
  (go-loop []
    (when-let [msg (<! (:event-ch @state))]
      (try
        (handle! state msg)
        (catch js/Error e
          (handle! state {:event :client/error :error e :msg msg})))
      (recur))))

;;-----------------------------------------------------------------------------
;; Boot
;;-----------------------------------------------------------------------------

(defn- main
  [pid]

  (window-resize!)

  (-> (dom/by-id "paddle")
      (dom/set-css! "background-color" (get paddle-color pid)))

  (let [event-ch (chan)
        socket (sk/socket! event-ch)
        paddle-id (keyword (str "player-" pid))]

    (swap! state merge {:ws socket :event-ch event-ch :id paddle-id})

    (view-mode @state)
    (events! state)
    (sk/open! socket)

    (dom/listen! (dom/by-id "session-button") "click" #(join-button! % event-ch))
    (dom/listen! (dom/by-id "session-form") "keyup" #(session-form! % event-ch))
    (dom/listen! js/window "mousemove" #(paddle-move! % @state))
    (dom/listen! js/window "resize" #(window-resize!))))

(defn ^:export start
  [paddle-num]
  (println "Welcome to Paddle" paddle-num)
  (main paddle-num))
