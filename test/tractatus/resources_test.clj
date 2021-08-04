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

(deftest new*
  (is (instance? Integer
                 (r/new* (resolve 'Integer) 42)))
  (is (instance? Integer
                 (r/new* (resolve (symbol "Integer")) 42))))

(r/defresource Monkey
  {::likes :banana})

(deftest defresource
  (testing "create instances of resource class"
    (are [i] (instance? Monkey i)
      (->Monkey {})
      (->Monkey {:name "Bubbles"})
      (Monkey. {:name "Bubbles"})
      (new Monkey {:name "Bubbles"})
      (r/new* (resolve 'Monkey) {:name "Bubbles"})))

  (testing "lookup attrs as if it was just a map"
    (let [monkey (->Monkey {:name "Bubbles"})]
      (are [a b] (= a b)
        "Bubbles" (:name monkey)
        "Bubbles" (monkey :name))))

  (testing "lookup aspects as a fallback to attrs"
    (let [monkey (->Monkey {:name "Bubbles"})]
      (are [a b] (= a b)
        :banana (::likes monkey)
        :banana (monkey ::likes))))

  (testing "attrs can shadow (aka override) aspects"
    (let [monkey (->Monkey {::likes "steak"})]
      (are [a b] (= a b)
        "steak" (::likes monkey)
        "steak" (monkey ::likes))))

  (testing "assoc attrs as if it was just a map"
    (let [monkey (->Monkey {:name "Bubbles"})
          new-monkey (assoc monkey
                            :name "Donkey Kong"
                            :year 1981)]
      (is (= "Donkey Kong" (:name new-monkey)))
      (is (= 1981 (:year new-monkey)))
      (is (instance? Monkey new-monkey)))))
