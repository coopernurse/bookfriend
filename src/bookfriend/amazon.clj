(ns bookfriend.amazon
  (:use [am.ik.clj-aws-ecs])
  (:use clojure.contrib.zip-filter.xml)
  (:use [bookfriend.xml])
  (:require [bookfriend.db :as db]))

(defn create-requester-from-conf []
  (let [conf (db/get-all-config-as-map)]
    (make-requester
      "ecs.amazonaws.com" (conf "aws.access") (conf "aws.secret"))))

(defn parse-product-item [item]
  (element-to-map (:content (first item))))

(defn parse-product-search-result [res]
  (let [xz (clojure.zip/xml-zip res)]
    { :TotalResults (first (xml-> xz :Items :TotalResults text))
      :TotalPages   (first (xml-> xz :Items :TotalPages text))
      :items        (map parse-product-item (xml-> xz :Items :Item)) }))

(defn result-to-book-entity [item]
  (db/create-book-entity (:ASIN item) "kindle" (:Author (:ItemAttributes item)) (:Title (:ItemAttributes item))
    (:DetailPageURL item) (:URL (:MediumImage item)) 0))

(defn result-to-book-entity-list [xs]
  (map result-to-book-entity xs))

(defn amazon-search-kindle [requester keywords]
  (parse-product-search-result
    (item-search-map requester "Books" keywords
      { "ResponseGroup" "Medium" "BrowseNode" "1286228011" })))

