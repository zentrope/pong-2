(ns pong.main
  (:require-macros
   [cljs.core.async.macros :refer [go-loop go]])
  (:require
   [cljs.core.async :refer [chan <! put! timeout]]
   [pong.lib.dom :as dom]
   [pong.lib.socket :as sk]))

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

;; Math

(defn sqr
  [x]
  (* x x))

(defn dist
  [x1 y1 x2 y2]
  (js/Math.sqrt (+ (sqr (- x2 x1))
                   (sqr (- y2 y1)))))

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
  (draw! [_ ctx] [_ ctx session]))

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
  (draw! [_ ctx]
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
  (draw! [_ ctx session]
    (.save ctx)
    (aset ctx "textAlign" "center")
    (aset ctx "font" "60px Helvetica")
    (aset ctx "fillStyle" "peru")
    (.fillText ctx "Welcome to Pong!" MID_W 100)
    (aset ctx "font" "italic 14px Helvetica")
    (aset ctx "fillStyle" "slategray")
    (.fillText ctx "two player" MID_W 150)
    (aset ctx "font" "70px Helvetica")
    (aset ctx "fillStyle" "lime")
    (.fillText ctx session MID_W 300)
    (aset ctx "font" "italic 20px Helvetica")
    (aset ctx "fillStyle" "dodgerblue")
    (.fillText ctx "Each paddle must enter the above code to play." MID_W 400)
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
    (.fillText ctx "Restarting..." MID_W 300)
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

;; Animated Objects

(defprotocol IAnimatable
  (move [_]))

(extend-type Ball
  IAnimatable
  (move [{:keys [x y vx vy radius] :as ball}]
    (cond
      (= x radius) (assoc ball :x -100)
      (= x (- SCALE-W radius)) (assoc ball :x (+ SCALE-W 100))
      :else (let [vy (if (< radius y (- SCALE-H radius)) vy (* -1 vy))]
              (assoc ball :x (+ x vx) :y (+ y vy) :vy vy)))))

;; Movable Objects

(defprotocol IMovable
  (position! [_ y]))

(extend-type Paddle
  IMovable
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
                :socket nil
                :session "-"
                :paddles #{}})))

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
    (draw! (:game-start @state) ctx (:session @state)))
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
  (if (or (hit? paddle-1 ball) (hit? paddle-2 ball))
    (let [{:keys [vx vy]} ball
          delta (rand-nth [0.3 0.5 0.7])
          vxf (if (< vx 0) dec inc)
          vyf (if (< vy 0) #(- % delta) #(+ % delta))]
      (assoc state :ball (assoc ball :vx (* -1 (vxf vx)) :vy (vyf vy))))
    state))

(defn score-phase!
  [event-ch {:keys [ball score-1 score-2] :as state}]
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
      (when (= mode :game-over)
        (go (<! (timeout 5000))
            (put! event-ch {:event :client/restart-game})))
      (assoc state :score-1 s1 :score-2 s2 :ball ball :mode mode))))

(defn animate-loop!
  [state event-ch ctx]
  (let [scorer! (partial score-phase! event-ch)]
    ((fn again []
       (when (= (:mode @state) :playing)
         (swap! state #(-> % move-phase! collision-phase! scorer!)))
       (draw-phase! state ctx)
       (.requestAnimationFrame js/window again)))))

;;-----------------------------------------------------------------------------
;; Control
;;-----------------------------------------------------------------------------

(defn resize!
  [state ctx]
  (let [w (- (.-innerWidth js/window) 40)
        h (- (int (/ (* w 9) 16)) 40)
        canvas (dom/by-id "canvas")]
    (aset canvas "width" w)
    (aset canvas "height" h)
    (.scale ctx (/ w SCALE-W) (/ h SCALE-H))
    (draw-phase! state ctx)))

;;-----------------------------------------------------------------------------
;; Events
;;-----------------------------------------------------------------------------

(defmulti handle!
  (fn [state event-ch msg]
    (:event msg)))

(defmethod handle! :default
  [state event-ch msg]
  (println "unhandled: " msg))

;; server
(defmethod handle! :session
  [state event-ch msg]
  (when (= (:id msg) :game)
    (swap! state assoc :session (:session msg))))

;; server
(defmethod handle! :telemetry
  [state event-ch msg]
  (let [{:keys [y id]} msg
        y (* 450 (/ y 100))
        object (get @state id)]
    (swap! state assoc id (position! object y))))

;; server
(defmethod handle! :join
  [state event-ch msg]
  (when-not (= (:id msg) :game)
    (swap! state #(update-in % [:paddles] (fn [p] (conj p (:id msg)))))
    (if (= #{:paddle-1 :paddle-2} (:paddles @state))
      (swap! state assoc :mode :playing))))

;; server
(defmethod handle! :disconnect
  [state event-ch msg]
  (swap! state (fn [s]
                 (-> (update-in s [:paddles] #(disj % (:id msg)))
                     (assoc :mode :game-start)))))

(defmethod handle! :socket/open
  [state event-ch msg]
  (sk/send! (:ws @state) {:event :session :id :game}))

(defmethod handle! :client/restart-game
  [state event-ch msg]
  (swap! state (fn [s] (-> (merge s objects)
                          (assoc :mode :game-start)
                          (assoc :paddles #{}))))
  (sk/send! (:ws @state)
            {:event :gameover :session (:session @state) :id :game}))

(defmethod handle! :client/err
  [state event-ch msg]
  (println "ERROR:" (pr-str msg)))

(defn- event-loop!
  [state event-ch]
  (go-loop []
    (when-let [msg (<! event-ch)]
      (try
        (handle! state event-ch msg)
        (catch js/Error e
          (handle! state event-ch {:event :client/err :error e :msg msg})))
      (recur))))

;;-----------------------------------------------------------------------------
;; Boot
;;-----------------------------------------------------------------------------

(defn- main
  []
  (println "Welcome to Pong Single Player")
  (let [ctx (.getContext (dom/by-id "canvas") "2d")
        event-ch (chan)
        socket (sk/socket! event-ch)]
    (swap! state assoc :ws socket)
    (event-loop! state event-ch)

    (sk/open! socket)
    (dom/listen! js/window "resize" #(resize! state ctx))
    (resize! state ctx)
    (animate-loop! state event-ch ctx)))

(defn ^:export startGame
  []
  (main))
