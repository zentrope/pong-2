(ns pong.lib.dom)

(defn px
  [v]
  (str v "px"))

(defn by-id
  [id]
  (.getElementById js/document id))

(defn listen!
  [el type fn]
  (.addEventListener el type fn false)
  el)

(defn set-html!
  [el html]
  (aset el "innerHTML" html)
  el)

(defn set-css!
  [el attr val]
  (-> (aget el "style")
      (aset attr val))
  el)

(defn hide!
  [el]
  (set-css! el "display" "none")
  el)

(defn show!
  [el]
  (set-css! el "display" "block")
  el)
