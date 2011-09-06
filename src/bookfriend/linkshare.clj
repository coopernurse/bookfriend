(ns bookfriend.linkshare
  (:use clojure.contrib.zip-filter.xml)
  (:use bookfriend.xml)
  (:use bookfriend.url)
  (:use bookfriend.collections)
  (:require [appengine-magic.services.url-fetch :as fetch]))

(defmacro dbg [x] `(let [x# ~x] (println '~x "=" x#) x#))

(defn parse-product-item [item]
  (element-to-map (:content (first item))))

(defn parse-product-search-result [res]
  (let [xz (zip-xml-str res)]
    {
      :TotalMatches (first (xml-> xz :TotalMatches text))
      :TotalPages (first (xml-> xz :TotalPages text))
      :items (map parse-product-item (xml-> xz :item))
      }))

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
    url (build-url "http://productsearch.linksynergy.com/productsearch" (dbg query-map))]
    (parse-product-search-result (String. (:content (fetch/fetch url))))))