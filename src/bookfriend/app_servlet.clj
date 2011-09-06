(ns bookfriend.app_servlet
  (:gen-class :extends javax.servlet.http.HttpServlet)
  (:use bookfriend.core)
  (:use [appengine-magic.servlet :only [make-servlet-service-method]]))


(defn -service [this request response]
  ((make-servlet-service-method bookfriend-app) this request response))
