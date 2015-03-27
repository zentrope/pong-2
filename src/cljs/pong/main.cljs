(ns pong.main
  (:require-macros
   [cljs.core.async.macros :refer [go-loop]])
  (:require
   [cljs.core.async :refer [chan <! put! sliding-buffer]]))

(enable-console-print!)

;; Constants

(def SCALE-W 800)
(def SCALE-H 450)
(def MID_W (/ SCALE-W 2))
(def WIN_SCORE 5)

(def PLAYER_1_COLOR "dodgerblue")
(def PLAYER_2_COLOR "peru")

(def SCORE_FONT "60px Helvetica")
(def SCORE_COLOR "slategray")

(def KEYBOARD {27 :abort 32 :space})

;; Math

(defn sqr
  [x]
  (* x x))

(defn dist
  [x1 y1 x2 y2]
  (js/Math.sqrt (+ (sqr (- x2 x1))
                   (sqr (- y2 y1)))))

;; DOM

(defn by-id
  [id]
  (.getElementById js/document id))

(defn listen!
  [el type fn]
  (.addEventListener el type fn false))

;; Object Definitions

(defrecord GameBackground [])
(defrecord GameNet [])
(defrecord GameStartScreen [])
(defrecord GameOverScreen [])
(defrecord Paddle [x y width height vy color side])
(defrecord Ball [x y radius vx vy fill-color])
(defrecord Score [score x y font color align])

;; Drawable Objects

(defprotocol IDrawable
  (draw! [_ ctx]))

(extend-type GameBackground
  IDrawable
  (draw! [_ ctx]
    (.save ctx)
    (aset ctx "lineWidth" "0.5")
    (aset ctx "fillStyle" "black")
    (.fillRect ctx 0 0 SCALE-W SCALE-H)
    (aset ctx "lineWidth" "2")
    (aset ctx "strokeStyle" "#333")
    (.strokeRect ctx 0 0 SCALE-W SCALE-H)
    (.restore ctx)))

(extend-type GameNet
  IDrawable
  (draw! [- ctx]
    (.save ctx)
    (aset ctx "lineWidth" "1")
    (aset ctx "strokeStyle" SCORE_COLOR)
    (.setLineDash ctx (array 1 2))
    (.beginPath ctx)
    (.moveTo ctx MID_W 0)
    (.lineTo ctx MID_W SCALE-H)
    (.stroke ctx)
    (.closePath ctx)
    (.restore ctx)))

(extend-type GameStartScreen
  IDrawable
  (draw! [_ ctx]
    (.save ctx)
    (aset ctx "textAlign" "center")
    (aset ctx "font" "60px Helvetica")
    (aset ctx "fillStyle" "peru")
    (.fillText ctx "Welcome to Pong!" MID_W 100)
    (aset ctx "font" "italic 14px Helvetica")
    (aset ctx "fillStyle" "slategray")
    (.fillText ctx "single player" MID_W 150)
    (aset ctx "font" "20px Helvetica")
    (aset ctx "fillStyle" "dodgerblue")
    (.fillText ctx "Press the spacebar to start." MID_W 300)
    (aset ctx "font" "italic 12px Helvetica")
    (aset ctx "fillStyle" "slategray")
    (.fillText ctx "Position the mouse near a paddle to move that paddle." MID_W 370)
    (.fillText ctx "[ Spacebar ] to pause ." MID_W 410)
    (.fillText ctx "[ Escape ] to quit." MID_W 430)
    (.restore ctx)))

(extend-type GameOverScreen
  IDrawable
  (draw! [_ ctx]
    (.save ctx)
    (aset ctx "textAlign" "center")
    (aset ctx "font" "60px Helvetica")
    (aset ctx "fillStyle" "peru")
    (.fillText ctx "GAME OVER" MID_W 200)
    (aset ctx "font" "20px Helvetica")
    (aset ctx "fillStyle" "dodgerblue")
    (.fillText ctx "Press the spacebar." MID_W 300)
    (.restore ctx)))

(extend-type Score
  IDrawable
  (draw! [{:keys [score x y font color align]} ctx]
    (.save ctx)
    (aset ctx "font" font)
    (aset ctx "fillStyle" color)
    (aset ctx "textAlign" align)
    (.fillText ctx (str score) x y)
    (.restore ctx)))

(extend-type Paddle
  IDrawable
  (draw! [{:keys [color x y width height]} ctx]
    (.save ctx)
    (aset ctx "fillStyle" color)
    (.fillRect ctx x y width height)
    (.restore ctx)))

(extend-type Ball
  IDrawable
  (draw! [{:keys [x y radius fill-color] :as ball} ctx]
    (.save ctx)
    (aset ctx "fillStyle" fill-color)
    (.beginPath ctx)
    (.arc ctx x y radius 0 (* 2 js/Math.PI) false)
    (.fill ctx)
    (.closePath ctx)
    (.restore ctx)))

;; Moveable Objects

(defprotocol IMovable
  (move [_]))

(extend-type Ball
  IMovable
  (move [{:keys [x y vx vy radius] :as ball}]
    (cond
      (= x radius) (assoc ball :x -100)
      (= x (- SCALE-W radius)) (assoc ball :x (+ SCALE-W 100))
      :else (let [vy (if (< radius y (- SCALE-H radius)) vy (* -1 vy))]
              (assoc ball :x (+ x vx) :y (+ y vy) :vy vy)))))

;; Controllable Objects

(defprotocol IPositionable
  (position! [_ y]))

(extend-type Paddle
  IPositionable
  (position! [{:keys [y height] :as paddle} new-y]
    (let [new-y (- new-y (/ height 2))
          new-y (cond
                  (< new-y 0) 0
                  (> new-y (- SCALE-H height)) (- SCALE-H height)
                  :else new-y)]
      (assoc paddle :y new-y))))

(defprotocol IHittable
  (hit? [_ ball]))

(extend-type Paddle
  IHittable
  (hit? [{x1 :x y1 :y w :width h :height side :side :as paddle}
          {x :x y :y vx :vx radius :radius :as ball}]
    (let [x2 (+ x1 w)
        y2 (+ y1 h)]
    (or (and (= side :right)
             (<= y1 y y2)
             (< vx 0)
             (>= radius (dist x2 y x y)))
        (and (= side :left)
             (<= y1 y y2)
             (> vx 0)
             (>= radius (dist x1 y x y)))
        false))))

;; Game State

(def objects
  {:background (GameBackground.)
   :net        (GameNet.)
   :game-start (GameStartScreen.)
   :game-over  (GameOverScreen.)
   :score-1    (Score. 0 (- MID_W 75) 60 SCORE_FONT SCORE_COLOR "right")
   :score-2    (Score. 0 (+ MID_W 75) 60 SCORE_FONT SCORE_COLOR "left")
   :paddle-1   (Paddle. 10 (- SCALE-H 120) 10 100 10 PLAYER_1_COLOR :right)
   :paddle-2   (Paddle. (- SCALE-W 20) 10 10 100 10 PLAYER_2_COLOR :left)
   :ball       (Ball. 400 225 13 3 2 "lime")})

(defonce state
  (atom (merge objects
               {:mode :game-start ;; game-start game-over playing
                :pause? false
                :current-paddle :paddle-1})))

;; Animation

(defn draw-phase!
  [state ctx]
  (.clearRect ctx 0 0 SCALE-W SCALE-H)
  (draw! (:background @state) ctx)
  (when (contains? #{:playing :game-over} (:mode @state))
    (draw! (:score-2 @state) ctx)
    (draw! (:score-1 @state) ctx))
  (when (contains? #{:game-over} (:mode @state))
    (draw! (:game-over @state) ctx))
  (when (contains? #{:game-start} (:mode @state))
    (draw! (:game-start @state) ctx))
  (when (contains? #{:playing} (:mode @state))
    (draw! (:net @state) ctx)
    (draw! (:paddle-1 @state) ctx)
    (draw! (:paddle-2 @state) ctx)
    (draw! (:ball @state) ctx)))

(defn move-phase!
  [{:keys [ball] :as state}]
  (merge state {:ball (move ball)}))

(defn collision-phase!
  [{:keys [ball paddle-1 paddle-2] :as state}]
  (if (or (hit? paddle-1 ball)
          (hit? paddle-2 ball))
    (let [{:keys [vx vy]} ball
          delta (rand-nth [0.3 0.5 0.7])
          vxf (if (< vx 0) dec inc)
          vyf (if (< vy 0) #(- % delta) #(+ % delta))]
      (assoc state :ball (assoc ball :vx (* -1 (vxf vx)) :vy (vyf vy))))
    state))

(defn score-phase!
  [{:keys [ball score-1 score-2] :as state}]
  (if (<= 0 (:x ball) SCALE-W)
    state
    (let [{:keys [x vx radius]} ball
          {score1 :score} score-1
          {score2 :score} score-2
          p1? (> x SCALE-W)
          p2? (< x 0)
          score1 (if p1? (inc score1) score1)
          score2 (if p2? (inc score2) score2)
          s1 (assoc score-1 :score score1)
          s2 (assoc score-2 :score score2)
          mode (if (or (>= score1 WIN_SCORE) (>= score2 WIN_SCORE))
                 :game-over
                 (:mode state))
          ball (if p2?
                 (assoc ball :x (- SCALE-W 25) :y (/ SCALE-H 2) :vy -2 :vx -3)
                 (assoc ball :x 25 :y (/ SCALE-H 2) :vy 2 :vx 3))]
      (assoc state :score-1 s1 :score-2 s2 :ball ball :mode mode))))

(defn animate-loop!
  [state ctx]
  (when-not (:pause? @state)
    (when (= (:mode @state) :playing)
      (swap! state #(-> % move-phase! collision-phase! score-phase!)))
    (draw-phase! state ctx))
  (.requestAnimationFrame js/window (partial animate-loop! state ctx)))

;; Control

(defn resize!
  [state ctx]
  (let [w (- (.-innerWidth js/window) 40)
        h (- (int (/ (* w 9) 16)) 40)
        canvas (by-id "canvas")]
    (aset canvas "width" w)
    (aset canvas "height" h)
    (.scale ctx (/ w SCALE-W) (/ h SCALE-H))
    (draw-phase! state ctx)))

(defn- pause-or-abort!
  [state]
  (swap! state #(if (:pause? %)
                  (assoc % :pause? false)
                  (assoc % :mode :game-start))))

(defn- pause-or-next!
  [state]
  (swap! state #(case (:mode %)
                  :game-start (merge % objects {:mode :playing})
                  :game-over (assoc % :mode :game-start)
                  (assoc % :pause? (not (:pause? %))))))
(defn- event-loop!
  [state ch]
  (go-loop []
    (when-let [event (<! ch)]
      (case event
        :abort (pause-or-abort! state)
        :space (pause-or-next! state)
        (println "Unhandled event:" event))
      (recur))))

(defn- paddle-move!
  [state e]
  (when (= (:mode @state) :playing)
    (let [t (.-target e)
          w (- (.-innerWidth js/window) 40)
          h (- (int (/ (* w 9) 16)) 40)
          sh (/ h SCALE-H)
          new-y (int (/ (- (.-clientY e) (.-offsetTop t)) sh))
          x (.-clientX e)
          ww (/ (.-innerWidth js/window) 2)
          paddle (if (>= x ww) :paddle-2 :paddle-1)
          object (get @state paddle)]
      (swap! state assoc paddle (position! object new-y)))))

(def key-stroke-stream
  (comp (map #(or (get KEYBOARD %) :unknown))
        (filter #(not= % :unknown))))

(defn- main
  []
  (println "Welcome to Pong Single Player")
  (let [canvas (by-id "canvas")
        ctx (.getContext canvas "2d")
        events (chan 1 key-stroke-stream)]
    (event-loop! state events)
    (listen! js/window "resize" #(resize! state ctx))
    (listen! js/document "keydown" #(put! events (.-keyCode %)))
    (listen! canvas "mousemove" #(paddle-move! state %))
    (resize! state ctx)
    (animate-loop! state ctx)))

(set! (.-onload js/window) main)
