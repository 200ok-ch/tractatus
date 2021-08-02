(ns tractatus.persistence.sql
  (:require [next.jdbc.sql :as sql]
            [tractatus.persistence :as persistence]))

(defrecord Database [spec])

(extend-protocol persistence/Entities
  Database

  (find-by-id [{db :spec} {:keys [tablename primary-key]} id]
    (sql/get-by-id db tablename id primary-key {}))

  (find-by-conditions [{db :spec} {:keys [tablename]} conditions]
    (cond

      (map? conditions)
      (sql/find-by-keys db tablename conditions)

      (sequential? conditions)
      (let [[condition-str & params] conditions]
        (sql/query db (cons (str "SELECT * FROM " tablename " WHERE " condition-str) params)))

      :else (throw (str "Sql adapter does not support conditions of type " (type conditions)))))

  (insert! [{db :spec} {:keys [tablename]} attrs]
    (sql/insert! db tablename attrs))

  (update! [{db :spec} {:keys [tablename primary-key]} attrs]
    (sql/update! db tablename attrs
                 (select-keys attrs [primary-key])
                 {:return-keys true}))

  (delete! [{db :spec} {:keys [tablename primary-key]} id]
    (sql/delete! db tablename
                 {primary-key id}
                 {:return-keys true})))
