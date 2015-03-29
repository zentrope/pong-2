(ns pong.lib.socket
  (:require [cljs.core.async :refer [put!]]
            [cljs.reader :as reader]))

(defn open!
  [{:keys [url ws ch] :as socket}]
  (let [sock (js/WebSocket. url)]
    (aset sock "onerror"   #(put! ch {:event :socket/error :err %}))
    (aset sock "onmessage" #(put! ch (reader/read-string (.-data %))))
    (aset sock "onclose"   #(put! ch {:event :socket/close}))
    (aset sock "onopen"    #(put! ch {:event :socket/open}))
    (reset! ws sock)))

(defn close!
  [{:keys [url ws ch] :as socket}]
  (aset @ws "onclose" nil)
  (.-close @ws)
  (reset! ws nil))

(defn send!
  [{:keys [url ws ch] :as socket} msg]
  (try
    (.send @ws msg)
    (catch js/Error e
      (println "socket-exception:" e)
      (put! ch {:event :socket/exception :err e}))))

(defn socket!
  [event-ch]
  (let [host (.-hostname (.-location js/window))
        port (.-port (.-location js/window))]
    {:url (str "ws://" host ":" port "/ws")
     :ws (atom nil)
     :ch event-ch}))
