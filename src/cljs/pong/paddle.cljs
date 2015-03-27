(ns pong.paddle)

(enable-console-print!)

(def paddle-height 200)
(def paddle-radius (int (/ paddle-height 2)))
(def paddle-width 50)
(def paddle-offset (int (/ paddle-width 2)))

(def paddle-color {1 "peru" 2 "dodgerblue"})
(def paddle-num (atom :paddle-1))

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

(defn- place
  [y]
  (let [h (.-innerHeight js/window)]
    (js/Math.round (* 100 (/ y h)))))

(defn- paddle-move!
  [e]
  (let [x (.-clientX e)
        y (.-clientY e)
        p (by-id "paddle")
        coord (place y)
        m {:id @paddle-num :session "1234" :event :telemetry :y coord}]
    (set-css! p "top" (px (- y paddle-radius)))
    (set-html! (by-id "debug") (pr-str m))))

(defn window-resize!
  []
  (let [w (.-innerWidth js/window)
        h (.-innerHeight js/window)
        p (by-id "paddle")
        midway (px (- (int (/ w 2)) paddle-offset))]
    (set-css! p "left" midway)))

(defn- main
  [pid]
  (window-resize!)
  (-> (by-id "paddle")
      (set-css! "background-color" (get paddle-color pid)))
  (reset! paddle-num (keyword (str "player-" pid)))
  (listen! js/window "mousemove" #(paddle-move! %))
  (listen! js/window "resize" #(window-resize!)))

(defn ^:export start
  [paddle-num]
  (println "Welcome to Paddle" paddle-num)
  (main paddle-num))
