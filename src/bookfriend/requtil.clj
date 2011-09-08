(ns bookfriend.requtil
  (:require [bookfriend.db :as db])
  (:require [noir.session :as session]))

(defmacro dbg[x] `(let [x# ~x] (println '~x "=" x#) x#))

(declare *request*)
(declare *user*)

(defn logged-in? []
  (not (nil? (session/get :user-id))))

(defn get-user []
  (if (logged-in?)
    (if (nil? @*user*)
      (reset! *user* (dbg (db/get-user (session/get :user-id))))
      @*user*)
    nil))

(defn absolute-url
  "Converts uri into a full URL based on the current request
  For example, given a current request URL of:
  http://example.com:9000/foo/bar
  Deployed as foo.war (so /foo is the servlet context path)
  Then: (absolute-url \"/baz\") returns: http://example.com:9000/foo/baz"
  ([uri]
    (let [req (:request *request*)]
      (absolute-url (.getScheme req) (.getServerName req) (.getServerPort req) (.getContextPath req) uri)))
  ([scheme host port context uri]
      (str scheme "://" host (if (or (= 80 port) (= 443 port)) "" (str ":" port)) context uri)))

(defn wrap-requtil
  [handler]
  (fn [request]
    (binding [*request* request
              *user* (atom nil)]
      (handler request))))
