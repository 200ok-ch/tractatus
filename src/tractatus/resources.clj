(ns tractatus.resources
  (:require [camel-snake-kebab.core :as csk]
            [tractatus.domain :as td]
            [tractatus.protocols :as tp]
            [clojure.string :as str]
            [tractatus.inflection :as ti])
  (:import [clojure.lang ILookup IFn Associative IPersistentCollection]))

;;; General Helpers

(defn assert-fn
  "Asserts that F is a function and returns f for threadability"
  [f]
  (assert (fn? f) (str "Expected function, found " (or (type f) "nil") "."))
  f)

(defn new*
  "Slow instanciation without the use of any special forms."
  [klass & args]
  (clojure.lang.Reflector/invokeConstructor
   klass (to-array args)))

(defn- arity [f]
  {:pre [(instance? clojure.lang.AFunction f)]}
  (-> f class .getDeclaredMethods first .getParameterTypes alength))

(defn make-tablename [s]
  (-> (.getName s)
      (str/split #"\.")
      last
      (str/replace #"([a-z])([A-Z])" "$1-$2")
      str/lower-case
      ti/pluralize))

;;; Callback Helpers

(defn- run-callback
  "Runs the given callback and interprets the result. Returns a
  vector (tuple) of the potentially modified attributes map and a
  vector of error maps."
  [callback record hook]
  (let [result (case (arity callback)
                 2 (callback record hook)
                 1 (callback record)
                 (throw (ex-info
                         "Wrong arity for callback"
                         {:resource (name (type record))
                          :hook hook})))]
    (cond
      ;; happy cases
      (true? result) [record []]
      (instance? (type record) result) [result []]
      ;; unhappy cases
      (false? result) [record [{:error "Unspecified Error"}]]
      (string? result) [record [{:error result}]]
      (sequential? result) [record result]
      :default [record [{:error "Unknown Error" :object result}]])))

(defn run-callbacks [record hook fns]
  (let [[callback & remaining] fns
        [record¹ errors¹] (run-callback callback record hook)]
    (if (or (empty? remaining) (some :halt errors¹))
      [record¹ errors¹]
      (let [[record² errors²] (run-callbacks record hook remaining)]
        [record² (concat errors¹ errors²)]))))

;;; Defaults

;; TODO: use qualified keywords instead
(def default-aspects
  {:primary-key :id
   :retrievable
   {:find-by-id 'tractatus.persistence/find-by-id
    :find-by-conditions 'tractatus.persistence/find-by-conditions}
   :persistable
   {:insert! 'tractatus.persistence/insert!
    :update! 'tractatus.persistence/update!
    :delete! 'tractatus.persistence/delete!}})

;; defresource

(defmacro defresource [resource-name aspects]
  `(deftype ~resource-name [~'attrs]

     tp/IAspects

     (aspects [this#]
       ;; TODO: deep merge
       (merge default-aspects
              {:tablename (make-tablename ~resource-name)}
              ~aspects))

     tp/IAttributes

     (attributes [this#]
       ~'attrs)

     ILookup ;; (:name monkey)

     (valAt [this# key#]
       (get ~'attrs key#
            ;; TODO: maybe fallback to retrieve associated
            ;; fallback to lookup in aspects
            (get (tp/aspects this#) key#)))

     IFn ;; (monkey :name)

     (invoke [this# arg#]
       (clojure.lang.ILookup/.valAt this# arg#))

     IPersistentCollection

     (count [this#]
       (count ~'attrs))

     ;; TODO: implement
     (cons [this# arg#]
       this#)

     (empty [this#]
       (new* (type this#) {}))

     (equiv [this# arg#]
       (= ~'attrs arg#))

     Associative

     (containsKey [this# key#]
       (contains? ~'attrs key#))

     (entryAt [this# key#]
       (get ~'attrs key#))

     (assoc [this# key# val#]
       (new* (type this#) (assoc ~'attrs key# val#)))

     tp/Retrievable

     (find-by-id [this# id#]
       (assert (-> this# tp/aspects :datasource) "Oops, no datasource?")
       (if-let [attrs#
                ((get (tp/aspects this#) :transform-on-read identity)
                 ((-> this# tp/aspects :retrievable :find-by-id resolve deref assert-fn)
                  (-> this# tp/aspects :datasource)
                  (select-keys (tp/aspects this#) [:tablename :primary-key])
                  id#))]
         (new* (type this#) attrs#)
         nil)) ; if not found return nil

     (find-by-conditions [this# conditions#]
       (assert (-> this# tp/aspects :datasource) "Oops, no datasource?")
       (not-empty ; if not found instead of an empty list return nil
        (map
         (comp
          (partial new* (type this#))
          (get (tp/aspects this#) :transformable-on-read identity))
         ((-> this# tp/aspects :retrievable :find-by-conditions resolve deref assert-fn)
          (-> this# tp/aspects :datasource)
          (select-keys (tp/aspects this#) [:tablename :primary-key])
          conditions#))))

     tp/Persistable

     (insert! [this#]
       (assert (-> this# tp/aspects :datasource) "Oops, no datasource?")
       (new* (type this#)
             ((get (tp/aspects this#) :transform-on-read identity)
              ((-> this# tp/aspects :persistable :insert! resolve deref assert-fn)
               (-> this# tp/aspects :datasource)
               (select-keys (tp/aspects this#) [:tablename :primary-key])
               ((get (tp/aspects this#) :transform-on-write identity) ~'attrs)))))

     (update! [this#]
       (assert (-> this# tp/aspects :datasource) "Oops, no datasource?")
       (new* (type this#)
             ((get (tp/aspects this#) :transform-on-read identity)
              ((-> this# tp/aspects :persistable :update! resolve deref assert-fn)
               (-> this# tp/aspects :datasource)
               (select-keys (tp/aspects this#) [:tablename :primary-key])
               ((get (tp/aspects this#) :transform-on-write identity) ~'attrs)))))

     (delete! [this#]
       (assert (-> this# tp/aspects :datasource) "Oops, no datasource?")
       (new* (type this#)
             ((get (tp/aspects this#) :transform-on-read identity)
              ((-> this# tp/aspects :persistable :delete! resolve deref assert-fn)
               (-> this# tp/aspects :datasource)
               (select-keys (tp/aspects this#) [:tablename :primary-key])
               ((-> this# tp/aspects :primary-key) ~'attrs)))))

     tp/Callbackable

     (callbacks [this# hook#]
       (if-let [fns# (get-in (tp/aspects this#) [:callbacks hook#])]
         (->> (run-callbacks this# hook# fns#)
              (zipmap [:record :errors]))
         {:record this# :errors []}))

     tp/Lifecyclable

     (create! [this#]
       (let [result¹# (tp/callbacks this# :create)]
         (if (-> result¹# :errors empty?)
           (let [result²# (tp/callbacks (tp/insert! (:record result¹#)) :created)]
             (if (-> result²# :errors empty?)
               (:record result²#)
               ;; TODO: rollback
               (throw (ex-info "Error in callback after create w/o rollback" result²#))))
           (throw (ex-info "Failed to create" result¹#)))))

     (modify! [this#]
       (let [result¹# (tp/callbacks this# :modify)]
         (if (-> result¹# :errors empty?)
           (let [result²# (tp/callbacks (tp/update! (:record result¹#)) :modified)]
             (if (-> result²# :errors empty?)
               (:record result²#)
               ;; TODO: rollback
               (throw (ex-info "Error in callback after modify w/o rollback" result²#))))
           (throw (ex-info "Failed to modify" result¹#)))))

     (destroy! [this#]
       (let [result¹# (tp/callbacks this# :destroy)]
         (if (-> result¹# :errors empty?)
           (let [result²# (tp/callbacks (tp/delete! (:record result¹#)) :destroyed)]
             (if (-> result²# :errors empty?)
               (:record result²#)
               ;; TODO: rollback
               (throw (ex-info "Error in callback after destroy w/o rollback" result²#))))
           (throw (ex-info "Failed to destroy" result¹#)))))

     ;; tp/IInspect
     ;; (inspect [this#]
     ;;   (new* (type this#) {}))

     ))

;;; Delegation and syntactic sugar

(def aspects tp/aspects)

(def attributes tp/attributes)

(defn find-by-id [resource-class id]
  (tp/find-by-id (new* resource-class {}) id))

(defn find-by-conditions [resource-class conditions]
  (tp/find-by-conditions (new* resource-class {}) conditions))

(def insert! tp/insert!)

(def update! tp/update!)

(defn delete!
  ([resource]
   (tp/delete! resource))
  ([resource-class id]
   (let [{:keys [primary-id]} (aspects (new* resource-class {}))]
     (delete! (new* resource-class {primary-id id})))))

(def create! tp/create!)

(def modify! tp/modify!)

(defn destroy!
  ([resource]
   (tp/destroy! resource))
  ([resource-class id]
   (let [{:keys [primary-id]} (aspects (new* resource-class {}))]
     (destroy! (new* resource-class {primary-id id})))))

(comment

  (require '[tractatus.persistence.atom :as ta])

  (def db (ta/make-atomdb))

  (defresource Monkey
    {:datasource db
     ::name :monkey})
  ;; create instance
  (def monkey (->Monkey {:name "Bubbles" :banana true}))
  ;; lookup attributes
  (:banana monkey)
  (monkey :banana)
  (monkey :name)
  (:name monkey)
  ;; lookup spec data
  (::name monkey)
  (monkey ::name)

  (assoc monkey :name "Donkey Kong")
  (assoc monkey
         :name "Donkey Kong"
         :age 5)
  ((assoc monkey :name "Donkey Kong") :name)

  (def saved-monkey (create! monkey))
  (:id saved-monkey)
  (attributes monkey)
  (attributes saved-monkey)
  (find-by-id Monkey (:id saved-monkey))
  (find-by-conditions Monkey {:name "Bubbles"})
  )

;;; Notes

(comment

  ;; TODO: https://gist.github.com/kriyative/2642569

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
