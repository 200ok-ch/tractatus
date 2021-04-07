(ns tractatus.resource)

(defprotocol Describable
  (describe [this]))

(defprotocol Retrievable
  (find-by-id [this id])
  (find-by-conditions [this conditions]))

(defprotocol Persistable
  "Low level fns which operate directly on the persistence layer."
  (insert! [this attrs])
  (update! [this attrs])
  (delete! [this id]))

(defprotocol Lifecyclable
  "Higher level fns which handle callbacks and such."
  (create! [this attrs])
  (modify! [this attrs])
  (destroy! [this id]))

(defn- assert-fn [f]
  (assert (fn? f) (str "Expected function, found " (or (type f) "nil") "."))
  f)

(deftype Resource [specification]
  Describable
  (describe [this]
    specification)
  Retrievable
  (find-by-id [this id]
    ((-> specification :retrievable :find-by-id assert-fn)
     (-> specification :tablename)
     id))
  (find-by-conditions [this conditions]
    ((-> specification :retrievable :find-by-conditions assert-fn)
     (-> specification :tablename)
     conditions))
  Persistable
  (insert! [this attrs]
    ((-> specification :persistable :insert! assert-fn)
     (-> specification :tablename)
     attrs))
  (update! [this attrs]
    ((-> specification :persistable :update! assert-fn)
     (-> specification :tablename)
     attrs))
  (delete! [this id]
    ((-> specification :persistable :delete! assert-fn)
     (-> specification :tablename)
     id))
  Lifecyclable
  (create! [this attrs])
  (modify! [this attrs])
  (destroy! [this id]))

#_(def employee (->Resource employee-details))

(defmacro reify-inventory
  "Takes an inventory and defines the specified resources."
  [inventory]
  `(do
     ~@(for [[resource-name# resource#] (:resources inventory)]
         (let [name# (symbol resource-name#)]
           `(def ~name#
              (->Resource ~resource#))))))

#_(macroexpand '(reify-inventory {:resources {:a {}}}))

(comment

  ;; the application can use it like this

  (def ^:private ident (fn [& args] args))

  (def ^:private employee-details
    {:tablename :employee
     :retrievable {:find-by-id ident
                   :find-by-conditions ident}})

  (def ^:private inventory
    {:resources {:employee employee-details}})

  ;; TODO: use the inventory dsl instead

  (reify-inventory inventory)

  (find-by-id employee 1)

  (find-by-conditions employee {:name "Phil"})

  (describe employee)
  )
