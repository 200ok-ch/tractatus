(ns tractatus.protocols)

(defprotocol IInspect
  (inspect [this]))

(defprotocol Describable
  (describe [this]))

(defprotocol Retrievable
  (find-by-id [this id])
  (find-by-conditions [this conditions]))

(defprotocol Persistable
  "Low level fns which operate directly on the persistence layer."
  (insert! [this])
  (update! [this])
  (delete! [this]))

(defprotocol Callbackable
  "Run all callbacks on ATTRS for a given HOOK."
  (callbacks [this hook]))

(defprotocol Lifecyclable
  "Higher level fns which handle callbacks and such."
  (create! [this])
  (modify! [this])
  (destroy! [this]))

;; (defprotocol Associatable
;;   "Retrieving associated resources."
;;   (associated [this assoced]))

(defprotocol IAspects
  (aspects [this]))

(defprotocol IAttributes
  (attributes [this]))
