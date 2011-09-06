(ns bookfriend.xml
  (:require [clojure.xml :as xml]
            [clojure.zip :as zip]))

(declare element-to-map)

(defn zip-xml-str [s]
  (zip/xml-zip (xml/parse (new org.xml.sax.InputSource (new java.io.StringReader s)))))

(defn element-value [e]
  (if (> (count e) 1) (element-to-map e) (first e)))

(defn element-to-map [e]
  (apply array-map
    (apply concat
      (map (fn [x] (list (:tag x) (element-value (:content x)))) e))))
