(defproject bookfriend "1.0.0-SNAPSHOT"
  :description "bookfriend.me - Kindle and Nook book sharing"
  :dependencies [[org.clojure/clojure "1.2.1"]
                 [org.clojure/clojure-contrib "1.2.0"]
                 [clj-appengine-oauth "0.1.0"]
                 [am.ik/clj-aws-ecs "0.1.0"]
                 [noir "1.1.1-SNAPSHOT"] ]
  :dev-dependencies [[appengine-magic "0.4.4"]])

(use 'am.ik.clj-aws-ecs)
(def requester (make-requester "ecs.amazonaws.com" "0E0H5H2MA5VSC80AWH82" "JJqDI8/sz19oE1VkVZ+XOy97ql42Tfo//g+RtkNe"))
(item-search-map requester "Books" "Clojure" { "ResponseGroup" "Medium" "BrowseNode" "1286228011" })