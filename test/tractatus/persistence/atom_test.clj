(ns tractatus.persistence.atom-test
  (:require [tractatus.persistence.atom :as a]
            [tractatus.persistence :as p]
            [clojure.test :refer :all]))

(deftest atomdb
  (let [db (a/make-atomdb)
        opts {:tablename "plays" :primary-key :id}]

    (testing "insert! works and returns attrs with id"
      (let [title "Much Ado About Nothing"
            result (p/insert! db opts {:title title})]
        (are [x y] (= x y)
          title (:title result)
          #{:id :title} (-> result keys set))))

    (testing "find-by-id works"
      (let [post¹ (p/insert! db opts {})
            post² (p/find-by-id db opts (:id post¹))]
        (is (= post¹ post²))))

    (testing "find-by-conditions works"
      (let [post (p/insert! db opts {:find :me})
            posts (p/find-by-conditions db opts {:find :me})]
        (is (= 1 (count posts)))
        (is (= post (first posts)))))

    (testing "update! works"
      (let [post (p/insert! db opts {:title "Macbeth"})
            title² "Much Ado About Nothing"]
        (p/update! db opts (assoc post :title title²))
        (is (= title²
               (:title (p/find-by-id db opts (:id post)))))))

    (testing "delete! works"
      (let [post (p/insert! db opts {})]
        (p/delete! db opts (:id post))
        (is (nil?
             (p/find-by-id db opts (:id post))))))))
