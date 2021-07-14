(ns tractatus.persistence)

(defprotocol Entities
  (find-by-id [data-source resource-specification id])
  (find-by-conditions [data-source resource-specification conditions])
  (insert! [data-source resource-specification attrs])
  (update! [data-source resource-specification attrs])
  (delete! [data-source resource-specification id]))
