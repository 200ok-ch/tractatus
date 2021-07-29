(ns tractatus.domain-test
  (:require [clojure.test :refer :all]
            [tractatus.domain :as d]))

(deftest datasource
  (is (= {:datasource {:some "datasource"}}
         (d/datasource {} {:some "datasource"}))))

(deftest retrievable
  (is (= {:retrievable {:some "retrievable"}}
         (d/retrievable {} {:some "retrievable"}))))

(deftest persistable
  (is (= {:persistable {:some "persistable"}}
         (d/persistable {} {:some "persistable"}))))

;; TODO: test defdomain

;; TODO: test resource

(deftest tablename
  (is (= {:a 1 :tablename "zaphod"}
         (d/tablename {:a 1} "zaphod")))
  (is (= {:tablename "beeblebrox"}
         (d/tablename {:tablename "zaphod"} "beeblebrox"))))

(deftest primary-key
  (is (= {:primary-key :some-primary-key}
         (d/primary-key {} :some-primary-key))))

;; TODO: maybe test add-association

(deftest has-many
  (is (= {:a 1
          :associations
          {:heads
           {:cardinality :has-many
            :resource-name :heads
            :name :heads}}}
         (d/has-many {:a 1} :heads))))

(deftest belongs-to
  (is (= {:a 1
          :associations
          {:ship
           {:cardinality :belongs-to
            :resource-name :ship
            :name :ship}}}
         (d/belongs-to {:a 1} :ship))))

;; TODO: maybe test vectorify

;; TODO: maybe test vectorify-values

(deftest add-callbacks
  (is (= {:a 1 :callbacks {:b [identity] :c [identity]}}
         (d/add-callbacks {:a 1} {:b [identity] :c [identity]}))))

(deftest add-callback
  (testing "works with valid hooks"
    (is (= {:a 1 :callbacks {:create [identity]}}
           (d/add-callback {:a 1} :create identity)))))
