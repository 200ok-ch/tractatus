(ns tractatus.resources-test
  (:require [clojure.test :refer :all]
            [tractatus.resources :as r]
            [tractatus.domain :as d]
            [tractatus.persistence :as p]
            [tractatus.persistence.atom :as a]))

;; TODO: maybe test assert-fn

(deftest new*
  (is (instance? Integer
                 (r/new* (resolve 'Integer) 42)))
  (is (instance? Integer
                 (r/new* (resolve (symbol "Integer")) 42))))

(deftest arity
  (are [a f] (= a (#'r/arity f))
    1 identity
    2 +
    3 reduce
    4 map
    4 swap!
    6 update))

(deftest make-tablename
  (are [a b] (= a b)
    "strings" (r/make-tablename String)
    "hane-bambels" (r/make-tablename (deftype HaneBambel []))))

(defrecord SomeRecord [x])

(def record (->SomeRecord 42))

(deftest run-callback ;; callback record hook
  (are [errors callback] (= [record errors] (#'r/run-callback callback record :some-hook))
    ;; happy cases
    [] (constantly true)
    [] identity
    [] #(assoc % :x 42)
    ;; unhappy cases
    [{:error "Unspecified Error"}] (constantly false)
    [{:error "Vollpfosten"}] (constantly "Vollpfosten")
    [{:error "Vollpfosten"}] (constantly [{:error "Vollpfosten"}])
    [{:error "Unknown Error" :object 23}] (constantly 23)
    [{:error "Unknown Error" :object {:x 23}}] (constantly {:x 23})))

;; TODO: maybe test run-callbacks

;; NOTE: from here state is involved in testing

(r/defresource Resource0 {:x 42})

(deftest aspects
  (is (= 42 (-> {} ->Resource0 r/aspects :x))))


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

(r/defresource Monkey
  {::likes :banana})

(deftest defresource
  (testing "create instances of resource class"
    (are [i] (instance? Monkey i)
      (->Monkey {})
      (->Monkey {:name "Bubbles"})
      (Monkey. {:name "Bubbles"})
      (new Monkey {:name "Bubbles"})))

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
