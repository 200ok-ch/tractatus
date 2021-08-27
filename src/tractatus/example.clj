(ns tractatus.example
  (:require [tractatus.inventory :as i]
            [tractatus.resource :as r]))

(i/definventory my-inventory
  ;; datasources need to implement the protocol `tractatus.core/Persistence`
  (i/datasource :x) ;; (tractatus.persistence.sql/->Database {}))
  (i/resource :user
              ;; (i/spec ::user)
              (i/has-many :memberships))
  (i/resource :membership
              (i/belongs-to :user)
              (i/belongs-to :project))
  (i/resource :project
              (i/has-many :memberships)
              ;; (i/resource :task)
              ))

(:resources my-inventory)

(r/reify-inventory my-inventory)

(macroexpand-1 '(r/reify-inventory {:resources {:user {}}}))

(r/mkfn [:a :b :c])

user
