(ns bookfriend.config
  (:use [noir.core :only (defpage defpartial render)])
  (:use [hiccup.form-helpers])
  (:require [bookfriend.db :as db])
  (:require [bookfriend.views :as views]))

(defpartial config-row [c]
  (let [id (str "delete-" (str (:id c)))]
    [:tr
     [:td [:input {:type "checkbox" :name id :value "1"} ]]
     [:td (:id c)]
     [:td (:value c)]]))

(defpartial config-view [configs]
  (views/layout "Configuration"
    [:form {:id "config-form" :post "/admin/config" :method "POST"}
      [:p "Add/edit properties. One per line. Format: key=value" ]
      [:p (text-area {:class "radius input-text":rows 10 :cols 40 } "props") ]
      [:table
       [:tr [:th "Delete"] [:th "Key"] [:th "Value"] ]
       (map config-row configs)
       ]
      (views/submit-link "config-form" "Save") ] ) )

(defn config-page
  []
  (render config-view (db/get-all-config)))

(defn delete-prop
  [k]
  (let [key (subs (str k) 8)]
    (db/delete-config-by-id key)))

(defn delete-props
  [params]
  (doall (map delete-prop (filter #(.startsWith (str %) ":delete-") (keys params)))))

(defn save-prop
  [line]
  (let [pos (.indexOf line "=")]
    (if (> pos -1)
      (let [key (subs line 0 pos)
            val (subs line (+ 1 pos))]
        (db/put-config key val)))))

(defn save-props
  [p]
  (doall (map save-prop (views/split-newline p))))

(defpage [:get "/admin/config"] {:as params}
  (config-page))

(defpage [:post "/admin/config"] {:as params}
  (delete-props params)
  (save-props (:props params))
  (config-page))
