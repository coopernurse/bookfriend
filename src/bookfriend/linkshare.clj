(ns bookfriend.linkshare
  (:use clojure.contrib.zip-filter.xml)
  (:use bookfriend.xml)
  (:use bookfriend.url)
  (:use bookfriend.collections)
  (:require [bookfriend.db :as db])
  (:require [appengine-magic.services.url-fetch :as fetch]))

(defn parse-product-item [item]
  (element-to-map (:content (first item)) true))

(defn parse-product-search-result [res]
  (let [xz (zip-xml-str res)]
    {
      :TotalMatches (first (xml-> xz :TotalMatches text))
      :TotalPages (first (xml-> xz :TotalPages text))
      :items (map parse-product-item (xml-> xz :item))
      }))

(defn get-token-from-conf []
  (let [conf (db/get-all-config-as-map)]
    (conf "linkshare.token")))

(defn result-to-book-entity [item]
  (db/create-book-entity (:sku item) "nook" (:short (:description item) ) (:productname item)
    (:linkurl item) (:imageurl item) 0))

(defn result-to-book-entity-list [xs]
  (map result-to-book-entity xs))

(defn product-search
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
    (parse-product-search-result (String. (:content (fetch/fetch url)) "utf-8"))))