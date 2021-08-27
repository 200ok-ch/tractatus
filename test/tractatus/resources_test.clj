(ns tractatus.resources-test
  (:require [clojure.test :refer :all]
            [tractatus.resources :as r]
            [tractatus.domain :as d]
            [tractatus.persistence :as p]
            [tractatus.persistence.atom :as a])
  (:import [java.lang AssertionError]))

(deftest assert-fn
  (is (thrown? AssertionError (r/assert-fn nil)))
  (is (thrown? AssertionError (r/assert-fn 42)))
  (is (= identity (r/assert-fn identity)))
  (is (= r/assert-fn (r/assert-fn r/assert-fn))))

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
    "hane_bambels" (r/make-tablename (deftype HaneBambel []))
    "octopi" (r/make-tablename (deftype Octopus []))))

(defrecord SomeRecord [x])

(def record (->SomeRecord 42))

(deftest run-callback ;; callback record hook
  (are [errors callback]
      (= [record errors]
         (#'r/run-callback callback record :some-hook))
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

(r/defresource Post {:datasource db})

(deftest insert!
  (is (= #{:id :title}
         (set (keys (r/insert! (->Post {:title "Hello world"})))))))

(deftest find-by-id
  (let [post (r/insert! (->Post {:title "Hello world"}))]
    (is (= post
           (r/find-by-id Post (:id post))))))

(deftest find-by-conditions
  (let [post (r/insert! (->Post {:title "Hello world"}))]
    (is (= post
           (first (r/find-by-conditions Post {:id (:id post)}))))))

(defn in?
  "true if coll contains elm"
  [coll elm]
  (some #(= elm %) coll))

(deftest find-by-conditions-2
  (let [post (r/insert! (->Post {:title "Hello world!"}))]
    (is ((set (r/find-by-conditions Post {:title "Hello world!"})) post))))

(deftest update!
  (let [post (r/insert! (->Post {:title "Hello world"}))]
    (r/update! (assoc post :title "Brave new title"))
    (is (= "Brave new title" (:title (r/find-by-id Post (:id post)))))))

(deftest delete!
  (let [post (r/insert! (->Post {:title "Hello world"}))]
    (r/delete! post)
    (is (nil? (r/find-by-id Post (:id post))))))

;; TODO: test callbacks

(deftest create!
  (is (= #{:id :title}
         (set (keys (r/create! (->Post {:title "Hello world"})))))))

(deftest modify!
  (let [post (r/insert! (->Post {:title "Hello world"}))]
    (r/modify! (assoc post :title "Brave new title"))
    (is (= "Brave new title" (:title (r/find-by-id Post (:id post)))))))

(deftest destroy!
  (let [post (r/insert! (->Post {:title "Hello world"}))]
    (r/destroy! post)
    (is (nil? (r/find-by-id Post (:id post))))))

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
