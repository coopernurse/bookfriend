(ns bookfriend.search
  (:require [bookfriend.linkshare :as linkshare])
  (:require [bookfriend.amazon :as amazon]))

(defn search [keyword kindle? nook? & [amazon-requester linkshare-token]]
  (concat
    (if kindle?
      (amazon/result-to-book-entity-list
        (:items (amazon/amazon-search-kindle (or amazon-requester (amazon/create-requester-from-conf)) keyword))))
    (if nook?
      (linkshare/result-to-book-entity-list
        (:items (linkshare/product-search (or linkshare-token (linkshare/get-token-from-conf)) keyword {:cat "ebooks"}))))))

