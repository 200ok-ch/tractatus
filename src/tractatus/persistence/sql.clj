(ns tractatus.persistence.sql
  (:require [next.jdbc.sql :as sql]))

(defrecord Database [spec])

(extend-protocol tractatus.persistence/Entities
  Database

  (get [{db :spec} {:keys [tablename primary-key]} id]
    (sql/get-by-id db tablename id primary-key {}))

  (find [{db :spec} {:keys [tablename]} conditions]
    (cond

      (map? conditions)
      (sql/find-by-keys db tablename conditions)

      (sequential? conditions)
      (let [[condition-str & params] conditions]
        (sql/query db (cons (str "SELECT * FROM " tablename " WHERE " condition-str) params)))

      :else (throw (str "Sql adapter does not support conditions of type " (type conditions)))))

  (create! [{db :spec} {:keys [tablename]} emap]
    (sql/insert! db tablename emap))

  (update! [{db :spec} {:keys [tablename primary-key]} emap]
    (->> (select-keys emap [primary-key])
         (sql/update! db tablename emap)))

  (destroy! [{db :spec} {:keys [tablename primary-key]} id]
    (sql/delete! db tablename {primary-key id})))
