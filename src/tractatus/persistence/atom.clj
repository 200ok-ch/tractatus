(ns tractatus.persistence.atom)

;; TODO: use deftype instead
(defrecord AtomDB [atom])

(extend-protocol tractatus.persistence/Entities
  AtomDB

  (find-by-id [{db :atom} {:keys [tablename]} id]
    (get-in @db [tablename id]))

  (find-by-conditions [{db :atom} {:keys [tablename]} conditions]
    (cond

      (map? conditions)
      (->> (get @db tablename)
           vals
           (filter #(clojure.set/subset? (set conditions) (set %))))

      :else
      (throw (str "AtomDB does not support conditions of type " (type conditions)))))

  (insert! [{db :atom} {:keys [tablename primary-key]} attrs]
    (let [uuid (str (java.util.UUID/randomUUID))
          attrs¹ (assoc attrs primary-key uuid)]
      (swap! db assoc-in [tablename uuid] attrs¹)
      attrs¹))

  (update! [{db :atom} {:keys [tablename primary-key]} attrs]
    (swap! db assoc-in [tablename (primary-key attrs)] attrs))

  (delete! [{db :atom} {:keys [tablename primary-key]} id]
    (swap! db update tablename #(dissoc % id))))

(defn make-atomdb []
  (->AtomDB (atom {})))
