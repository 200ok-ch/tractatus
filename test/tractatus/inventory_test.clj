(ns tractatus.inventory-test
  (:require [clojure.test :refer :all]
            [tractatus.inventory :as i]))

(deftest tablename
  (is (= {:a 1 :tablename "zaphod"}
         (i/tablename {:a 1} "zaphod")))
  (is (= {:tablename "beeblebrox"}
         (i/tablename {:tablename "zaphod"} "beeblebrox"))))

(deftest has-many
  (is (= {:a 1
          :associations
          {:heads
           {:cardinality :has-many
            :resource-name :heads
            :name :heads}}}
         (i/has-many {:a 1} :heads))))

(deftest belongs-to
  (is (= {:a 1
          :associations
          {:ship
           {:cardinality :belongs-to
            :resource-name :ship
            :name :ship}}}
         (i/belongs-to {:a 1} :ship))))

;; (deftest add-callbacks
;;   (is (= (add-callbacks {:a 1} {:b [identity] :c [identity]})
;;          {:a 1 :callbacks {:b [identity] :c [identity]}})))
;;
;; (deftest add-callback
;;   (is (= (add-callback {:a 1} :b identity)
;;          {:a 1 :callbacks {:b [identity]}})))
;;
;; (deftest resolve-strategy
;;   (is (= (#'resolve-strategy {} :a) nil))
;;   (is (= (#'resolve-strategy {:a_id 1} :a) :belongs-to)))
;;
;; (deftest resolve-missing
;;   (is (= (resolve-missing {} :a) "no value for :a"))
;;   (is (= (resolve-missing {:a_id 1} :a) "resolving associated :a")))
;;
;; (deftest entity-map
;;   (is (= ((map->EntityMap {:a_id 1}) :a) "resolving associated :a")))
;;
;; (deftest run-callback
;;   (is (= (#'run-callback
;;           (fn [& x] true) nil nil {:a 42})
;;          [{:a 42} []]))
;;   (is (= (#'run-callback
;;           (fn [& x] {:b 23}) nil nil {:a 42})
;;          [{:b 23} []]))
;;   (is (= (#'run-callback
;;           (fn [& x] false) nil nil {:a 42})
;;          [{:a 42} [{:error "Unspecified Error"}]]))
;;   (is (= (#'run-callback
;;           (fn [& x] [{:error "Vollpfosten"}]) nil nil {:a 42})
;;          [{:a 42} [{:error "Vollpfosten"}]]))
;;   (is (= (#'run-callback
;;           (fn [& x] 23) nil nil {:a 42})
;;          [{:a 42} [{:error "Unknown Error" :object 23}]])))
;;
;; (def registry
;;   {:petunias
;;    {:callbacks
;;     {:drop [(fn [& _] false)]}}
;;    :more-petunias
;;    {:callbacks
;;     {:drop [(fn [& _] false)
;;             (fn [& _] false)]}}
;;    :even-more-petunias
;;    {:callbacks
;;     {:drop [(fn [& _] {:c 23})
;;             (fn [& _] "Vollpfosten")]}}
;;    :whale
;;    {:callbacks
;;     {:drop [(fn [_ _ a] (update a :b inc))
;;             (fn [_ _ a] (update a :b inc))]}}
;;    :more-whales
;;    {:callbacks
;;     {:drop [(fn [& a] [{:halt true}])
;;             (fn [& a] false)]}}})
;;
;; (def emap {:b 42})
;;
;; (def hook :drop)
;;
;; (deftest callbacks
;;   (is (= {:entity {:b 42} :errors [{:error "Unspecified Error"}]}
;;          (#'callbacks emap registry :petunias hook))
;;       "callback returning false yields an unspecified error")
;;
;;   (is (= {:entity {:b 42} :errors [{:error "Unspecified Error"}
;;                                    {:error "Unspecified Error"}]}
;;          (#'callbacks emap registry :more-petunias hook))
;;       "two callbacks returning false yield two unspecified errors")
;;
;;   (is (= {:entity {:c 23} :errors [{:error "Vollpfosten"}]}
;;          (#'callbacks emap registry :even-more-petunias hook))
;;       "one callback returns modified emap & one callback returns a string")
;;
;;   (is (= {:entity {:b 42} :errors []}
;;          (#'callbacks {:b 40} registry :whale hook))
;;       "two callbacks modifing the emap")
;;
;;   (is (= {:entity {:b 42} :errors [{:halt true}]}
;;          (#'callbacks emap registry :more-whales hook))
;;       "two callbacks but the first is causing an early halt"))
