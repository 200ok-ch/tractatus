(ns tractatus.resources)

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
     (-> specification :datasource)
     (select-keys specification [:tablename :primary-key])
     id))
  (find-by-conditions [this conditions]
    ((-> specification :retrievable :find-by-conditions assert-fn)
     (-> specification :datasource)
     (select-keys specification [:tablename :primary-key])
     conditions))

  Persistable
  (insert! [this attrs]
    ((-> specification :persistable :insert! assert-fn)
     (-> specification :datasource)
     (select-keys specification [:tablename :primary-key])
     attrs))
  (update! [this attrs]
    ((-> specification :persistable :update! assert-fn)
     (-> specification :datasource)
     (select-keys specification [:tablename :primary-key])
     attrs))
  (delete! [this id]
    ((-> specification :persistable :delete! assert-fn)
     (-> specification :datasource)
     (select-keys specification [:tablename :primary-key])
     id))

  Lifecyclable
  (create! [this attrs])
  (modify! [this attrs])
  (destroy! [this id]))

#_(def employee (->Resource employee-details))

(defmacro reify-domain
  "Takes a domain and defines the specified resources."
  [domain]
  `(do
     ~@(for [[resource-name# resource#] (:resources (eval domain))]
         (let [name# (symbol resource-name#)]
           `(def ~name# (->Resource ~resource#))))))

#_(def domain {:resources {:a {:name "a"} :b {:name "b"}}})
#_(macroexpand `(reify-domain domain))

;; --------------------------------------------------------------------------------

(comment

  ;; the application can use it like this

  (def ^:private ident (fn [& args] args))

  (def ^:private employee-details
    {:tablename :employee
     :retrievable {:find-by-id ident
                   :find-by-conditions ident}})

  (def ^:private domain
    {:resources {:employee employee-details}})

  ;; TODO: use the domain dsl instead

  (reify-domain domain)

  (find-by-id employee 1)

  (find-by-conditions employee {:name "Phil"})

  (describe employee)
  )


(comment
  (defmacro mkfn [fns]
    `(do
       ~@(for [fn# fns]
           `(defn ~(symbol fn#) []
              ~fn#))))

  (macroexpand '(mkfn [:a]))

  (mkfn [:a :b :c])

  (a)
  (b)
  (c)
  )
