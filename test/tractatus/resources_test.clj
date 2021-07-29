(ns tractatus.resources-test
  (:require [clojure.test :refer :all]
            [tractatus.resources :as r]
            [tractatus.domain :as d]
            [tractatus.persistence :as p]
            [tractatus.persistence.atom :as a]))

;; TODO: maybe test assert-fn

;; TODO: maybe test arity

(deftest run-callback
  (is (= [{:a 42} []]
         (#'r/run-callback (fn [& x] true) nil nil {:a 42})))
  (is (= [{:b 23} []]
         (#'r/run-callback (fn [& x] {:b 23}) nil nil {:a 42})))
  (is (= [{:a 42} [{:error "Unspecified Error"}]]
         (#'r/run-callback (fn [& x] false) nil nil {:a 42})))
  (is (= [{:a 42} [{:error "Vollpfosten"}]]
         (#'r/run-callback (fn [& x] [{:error "Vollpfosten"}]) nil nil {:a 42})))
  (is (= [{:a 42} [{:error "Unknown Error" :object 23}]]
         (#'r/run-callback (fn [& x] 23) nil nil {:a 42}))))

;; TODO: maybe test run-callbacks

(deftest describe
  (is (= {:some "description"}
         (r/describe (r/->Resource {:some "description"})))))

;; NOTE: from here state is involved in testing

(def db (a/make-atomdb))

(d/defdomain test-domain
  (d/datasource db)
  (d/resource :post))

#_(def post
    (p/insert! db
               {:tablename "posts" :primary-key :id}
               {:title "Abstractions rule!"}))

#_(p/find-by-id db {:tablename "posts"} (:id post))

(r/reify-domain test-domain)

(deftest insert!
  (is (= #{:id :title}
         (set (keys (r/insert! Post {:title "Hello world"}))))))

(deftest find-by-id
  (let [post (r/insert! Post {:title "Hello world"})]
    (is (= post
           (r/find-by-id Post (:id post))))))

;; TODO: test find-by-conditions

;; TODO: test update!

;; TODO: test delete!

;; TODO: test callbacks

;; TODO: test create!

;; TODO: test modify!

;; TODO: test destroy!

;; TODO: test reify-domain
