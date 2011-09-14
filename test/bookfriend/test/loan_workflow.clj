(ns bookfriend.test.loan_workflow
  (:import (com.google.apphosting.api ApiProxy))
  (:require [appengine-magic.testing :as ae-testing])
  (:use [bookfriend.db])
  (:use [bookfriend.routes])
  (:use [clojure.test]))

(use-fixtures :each (ae-testing/local-services :all))

(defn get-mail-service []
  (.getService (ApiProxy/getDelegate) "mail"))

(defn clear-mail-service []
  (.clearSentMessages (get-mail-service)))

(defn get-mail-messages []
  (.getSentMessages (get-mail-service)))

(defn create-two-user-want-have [u1 u2 b1]
  (put-user! (create-user-entity u1 "name" "u1@example.com" "u1-kindle@example.com" "u1-nook@example.com" 0))
  (put-user! (create-user-entity u2 "name2" "u2@example.com" "u2-kindle@example.com" "u2-nook@example.com" 0))
  (put-book! (create-book-entity b1 "nook" "a1" "t1" nil nil 0))
  (put-book-user u1 b1 "have" "")
  (put-book-user u2 b1 "want" ""))

;  - set date acked=now and success=false on loan record
;  - email lender to let them know the loan failed
;  - flash message, refresh my tasks
;  - loan shouldn't show up in task list
(deftest loan-ack-fail-test
  (create-two-user-want-have "u1" "u2" "b1")
  (def loan (loan-book-create "b1" "u1" "u2"))
  (clear-mail-service)
  (loan-ack-fail (:id loan) "u2")
  (is (= false (:success (get-loan (:id loan)))))
  (is (= 1 (:points (get-user "u1"))))
  (is (= 1 (:points (get-user "u2"))))
  (def msgs (get-mail-messages))
  (is (= 1 (count msgs)))
  (is (= "Your loan of t1 was not received" (.getSubject (first msgs))))
  (is (= "u1@example.com" (.getTo (first msgs) 0)))
  (is (empty? (get-loans-to-ack "u2"))))

(deftest loan-ack-test
  (create-two-user-want-have "u1" "u2" "b1")
  (def loan (loan-book-create "b1" "u1" "u2"))
  (is (= "b1" (:id (first (dbg (get-loans-to-ack "u2"))))))
  (clear-mail-service)
  (loan-ack (:id loan) "u2")
  (is (= true (:success (get-loan (:id loan)))))
  ; points are incremented when they add book status, so 1+5 and 1+2
  (is (= 6 (:points (get-user "u1"))))
  (is (= 3 (:points (get-user "u2"))))
  (def msgs (get-mail-messages))
  (is (= 1 (count msgs)))
  (is (= "Your loan of t1 has been acknowledged!" (.getSubject (first msgs))))
  (is (= "u1@example.com" (.getTo (first msgs) 0)))
  (is (empty? (get-loans-to-ack "u2"))))

(deftest book-not-loanable-test
  (create-two-user-want-have "u1" "u2" "b1")
  (loan-book-not-loanable "b1" "u1")
  (is (= nil (get-book-user "u1" "b1")))
  (is (= 1 (:not-loanable-count (get-book "b1")))))

(deftest loan-create-test
  (clear-mail-service)
  (create-two-user-want-have "u1" "u2" "b1")
  (loan-book-create "b1" "u1" "u2")
  (def book-user1 (get-book-user "u1" "b1"))
  (is (= (:loan-id book-user1) (:loan-id (get-book-user "u2" "b1"))))
  (is (not (= "" (:loan-id (get-book-user "u1" "b1")))))
  (def loan (get-loan (:loan-id book-user1)))
  (is (= {:book-id "b1" :from-user-id "u1" :to-user-id "u2" :date-acked 0 :success false}
    (select-keys loan [:book-id :from-user-id :to-user-id :date-acked :success])))
  (def msgs (get-mail-messages))
  (is (= 2 (count msgs)))
  (is (= "t1 loaned to your nook" (.getSubject (first msgs))))
  (is (= "Thank you for lending t1" (.getSubject (second msgs))))
  (is (= "u2@example.com" (.getTo (first msgs) 0)))
  (is (= "u1@example.com" (.getTo (second msgs) 0))))

(deftest loan-book-bad-recip-removes-record
  (create-two-user-want-have "u1" "u2" "b1")
  (is (="u2" (:id (get-loan-recip "b1"))))
  (loan-book-bad-recip "b1" "u2")
  (is (= nil (:id (get-loan-recip "b1")))))

