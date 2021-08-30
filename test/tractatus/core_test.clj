(ns tractatus.core-test
  (:require [clojure.test :refer :all]
            [tractatus.core :as t]
            [tractatus.persistence.atom :as a])
  (:import [java.lang AssertionError]))

(deftest assert-fn
  (is (thrown? AssertionError (t/assert-fn nil)))
  (is (thrown? AssertionError (t/assert-fn 42)))
  (is (= identity (t/assert-fn identity)))
  (is (= t/assert-fn (t/assert-fn t/assert-fn))))

(deftest new*
  (is (instance? Integer
                 (t/new* (resolve 'Integer) 42)))
  (is (instance? Integer
                 (t/new* (resolve (symbol "Integer")) 42))))

;; FIXME: this works in the repl but fails with lein test
;; (deftest arity
;;   (are [a f] (= a (#'t/arity f))
;;     1 identity
;;     2 +
;;     3 reduce
;;     4 map
;;     4 swap!
;;     6 update))

(deftest make-tablename
  (are [a b] (= a b)
    "strings" (t/make-tablename String)
    "hane_bambels" (t/make-tablename (deftype HaneBambel []))
    "octopi" (t/make-tablename (deftype Octopus []))))

(defrecord SomeRecord [x])

(def record (->SomeRecord 42))

(deftest run-callback ;; callback record hook
  (are [errors callback]
      (= [record errors]
         (#'t/run-callback callback record :some-hook))
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
(t/defresource Resource0 {:x 42})

(deftest aspects
  (is (= 42 (-> {} ->Resource0 t/aspects :x))))

(def db (a/make-atomdb))

(t/defresource Post {:datasource db})

(deftest insert!
  (is (= #{:id :title}
         (set (keys (t/insert! (->Post {:title "Hello world"})))))))

(deftest find-by-id
  (let [post (t/insert! (->Post {:title "Hello world"}))]
    (is (= post
           (t/find-by-id Post (:id post))))))

(deftest find-by-conditions
  (let [post (t/insert! (->Post {:title "Hello world"}))]
    (is (= post
           (first (t/find-by-conditions Post {:id (:id post)}))))))

(defn in?
  "true if coll contains elm"
  [coll elm]
  (some #(= elm %) coll))

(deftest find-by-conditions-2
  (let [post (t/insert! (->Post {:title "Hello world!"}))]
    (is ((set (t/find-by-conditions Post {:title "Hello world!"})) post))))

(deftest update!
  (let [post (t/insert! (->Post {:title "Hello world"}))]
    (t/update! (assoc post :title "Brave new title"))
    (is (= "Brave new title" (:title (t/find-by-id Post (:id post)))))))

(deftest delete!
  (let [post (t/insert! (->Post {:title "Hello world"}))]
    (t/delete! post)
    (is (nil? (t/find-by-id Post (:id post))))))

;; TODO: test callbacks

(deftest create!
  (is (= #{:id :title}
         (set (keys (t/create! (->Post {:title "Hello world"})))))))

(deftest modify!
  (let [post (t/insert! (->Post {:title "Hello world"}))]
    (t/modify! (assoc post :title "Brave new title"))
    (is (= "Brave new title" (:title (t/find-by-id Post (:id post)))))))

(deftest destroy!
  (let [post (t/insert! (->Post {:title "Hello world"}))]
    (t/destroy! post)
    (is (nil? (t/find-by-id Post (:id post))))))

(t/defresource Monkey
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
