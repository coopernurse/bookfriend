(ns bookfriend.xml
  (:use [clojure.string :only (trim)])
  (:require [clojure.xml :as xml]
            [clojure.zip :as zip]))

(declare element-to-map)

(defn drop-dupes [xs]
  (let [counts (group-by (fn [x] (first x)) xs)]
    (filter (fn [x] (< (count (counts (first x))) 2)) xs)))

(defn zip-xml-str [s]
  (zip/xml-zip (xml/parse (new org.xml.sax.InputSource (new java.io.StringReader s)))))

(defn safe-trim [trim? val]
  (if trim?
    (if val (trim val) val)
    val))

(defn element-value [e trim?]
  (if (> (count e) 1) (element-to-map e) (safe-trim trim? (first e))))

(defn element-to-map
  ([e] (element-to-map e false))
  ([e trim?]
    (apply array-map
      (apply concat
        (drop-dupes
          (map (fn [x] (list (:tag x) (element-value (:content x) trim?))) e))))))
