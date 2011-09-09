(ns bookfriend.search
  (:use am.ik.clj-aws-ecs)
  (:use clojure.contrib.zip-filter.xml)
  (:use bookfriend.xml)
  (:use bookfriend.url)
  (:use bookfriend.collections)
  (:require [bookfriend.db :as db])
  (:require [appengine-magic.services.url-fetch :as fetch]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; nook ;;
;;;;;;;;;;

(defn parse-nook-item [item]
  (element-to-map (:content (first item)) true))

(defn parse-nook-search-result [res]
  (let [xz (zip-xml-str res)]
    {
      :TotalMatches (first (xml-> xz :TotalMatches text))
      :TotalPages (first (xml-> xz :TotalPages text))
      :items (map parse-nook-item (xml-> xz :item))
      }))

(defn linkshare-token-from-conf []
  (let [conf (db/get-all-config-as-map)]
    (conf "linkshare.token")))

(defn nook-result-to-book-entity [item]
  (db/create-book-entity (:sku item) "nook" (:short (:description item) ) (:productname item)
    (:linkurl item) (:imageurl item) 0))

(defn nook-result-list [xs]
  (map nook-result-to-book-entity xs))

(defn nook-product-search
  [token keyword & [{:keys [cat max-results pagenumber mid sort sort-type]}]]
  (let [
    query-map (create-map-exclude-nil [
      [:token token]
      [:keyword keyword]
      [:cat cat]
      [:MaxResults max-results]
      [:pagenumber pagenumber]
      [:mid mid]])
    url (build-url "http://productsearch.linksynergy.com/productsearch" query-map)]
    (parse-nook-search-result (String. (:content (fetch/fetch url)) "utf-8"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; kindle ;;
;;;;;;;;;;;;

(defn create-requester-from-conf []
  (let [conf (db/get-all-config-as-map)]
    (make-requester
      "ecs.amazonaws.com" (conf "aws.access") (conf "aws.secret"))))

(defn parse-kindle-item [item]
  (element-to-map (:content (first item))))

(defn parse-kindle-search-result [res]
  (let [xz (clojure.zip/xml-zip res)]
    { :TotalResults (first (xml-> xz :Items :TotalResults text))
      :TotalPages   (first (xml-> xz :Items :TotalPages text))
      :items        (map parse-kindle-item (xml-> xz :Items :Item)) }))

(defn kindle-result-to-book-entity [item]
  (db/create-book-entity (:ASIN item) "kindle" (:Author (:ItemAttributes item)) (:Title (:ItemAttributes item))
    (:DetailPageURL item) (:URL (:MediumImage item)) 0))

(defn kindle-result-list [xs]
  (map kindle-result-to-book-entity xs))

(defn amazon-search-kindle [requester keywords]
  (parse-kindle-search-result
    (item-search-map requester "Books" keywords
      { "ResponseGroup" "Medium" "BrowseNode" "1286228011" })))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn search [keyword kindle? nook? & [amazon-requester linkshare-token]]
  (concat
    (if kindle?
      (kindle-result-list
        (:items (amazon-search-kindle (or amazon-requester (create-requester-from-conf)) keyword))))
    (if nook?
      (nook-result-list
        (:items (nook-product-search (or linkshare-token (linkshare-token-from-conf)) keyword {:cat "ebooks"}))))))

