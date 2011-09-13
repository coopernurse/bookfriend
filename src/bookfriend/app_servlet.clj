(ns bookfriend.app_servlet
  (:gen-class :extends javax.servlet.http.HttpServlet)
  (:require [bookfriend.requtil :as requtil])
  (:require [bookfriend.httpsession :as hs])
  (:require [noir.util.gae :as noir-gae])
  (:require [appengine-magic.core :as ae])
  (:use [appengine-magic.servlet :only [make-servlet-service-method]]))

;;
;; require any namespaces that contain defpage macros
;; so that noir can find them and setup the routes
;;
(require 'bookfriend.routes)
(require 'bookfriend.config)

(def app-handler
  (noir-gae/gae-handler {:session-store (hs/http-session-store "bookfriend-session") }))

(ae/def-appengine-app bookfriend-app
  (-> app-handler
    (requtil/wrap-requtil)
    (hs/wrap-http-session-store)))

(defn -service [this request response]
  ((make-servlet-service-method bookfriend-app) this request response))
