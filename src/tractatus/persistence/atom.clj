(ns tractatus.persistence.atom
  (:require [next.jdbc.sql :as sql]))

(defrecord AtomDB [spec])

(extend-protocol tractatus.persistence/Entities
  AtomDB

  (get [{db :spec} {:keys [tablename]} id]
    (-> @db tablename (get id)))

  (find [{db :spec} {:keys [tablename]} conditions]
    (cond

      (map? conditions)
      (->> @db tablename vals (filter #(clojure.set/subset? (set conditions) (set %))))

      :else
      (throw (str "AtomDB does not support conditions of type " (type conditions)))))

  (create! [{db :spec} {:keys [tablename primary-key]} emap]
    (let [uuid (str (java.util.UUID/randomUUID))]
      (swap! db assoc-in [tablename uuid] emap)
      (assoc emap primary-key uuid)))

  (update! [{db :spec} {:keys [tablename primary-key]} emap]
    (swap! db assoc-in [tablename (primary-key emap)] emap))

  (destroy! [{db :spec} {:keys [tablename primary-key]} id]
    (swap! db update tablename #(dissoc % id)))
