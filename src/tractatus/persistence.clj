(ns tractatus.persistence)

(defprotocol Entities
  (get [data-source entity-spec id])
  (find [data-source entity-spec conditions])
  (create! [data-source entity-spec emap])
  (update! [data-source entity-spec emap])
  (destroy! [data-source entity-spec id]))
