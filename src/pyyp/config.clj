(ns pyyp.config
  (:require
   [clojure.core.async :as async]
   [integrant.core :as ig]
   [next.jdbc :as jdbc]
   [pyyp.router :as router]
   [pyyp.worker :as worker]
   [ring.adapter.jetty :refer [run-jetty]]))

(def config {:database/connection "jdbc:sqlite:resources/db.sqlite"
             :backend/application
             {:db                     (ig/ref :database/connection)
              :scrape-dataset/channel (ig/ref :scrape-dataset/worker)
              :auth                   (ig/ref :backend/auth)}
             :backend/auth
             {:jwt-secret            "ff363172-d6b5-46f4-889e-858dbf200d43"
              :jwt-opts              {:alg :hs512}
              :jwt-token-expire-secs 31557600}
             :backend/server
             {:application (ig/ref :backend/application)
              :port        3000
              :join?       false}
             :scrape-dataset/worker
             {:db                         (ig/ref :database/connection)
              :openneuro-url              "https://openneuro.org/crn/graphql"
              :openneuro-dataset-request  "{\"operationName\" : \"dataset\", \"variables\" : {\"datasetId\" : \"%s\"}, \"query\" : \"query dataset($datasetId: ID!) { dataset (id: $datasetId) { id created public worker ...DatasetDraft ...DatasetSnapshots ...DatasetIssues ...DatasetMetadata uploader { id name email __typename }  __typename } } fragment DatasetDraft on Dataset { id draft { id modified readme head description { Name Authors DatasetDOI License Acknowledgements HowToAcknowledge Funding ReferencesAndLinks EthicsApprovals __typename } summary { modalities secondaryModalities sessions subjects subjectMetadata { participantId age sex group __typename } tasks size totalFiles dataProcessed pet { BodyPart ScannerManufacturer ScannerManufacturersModelName TracerName TracerRadionuclide __typename } __typename } __typename } __typename } fragment DatasetSnapshots on Dataset {  id snapshots { id tag created hexsha __typename  } __typename} fragment DatasetIssues on Dataset { id draft { id issues { severity code reason files { evidence line character reason file { name path relativePath __typename } __typename } additionalFileCount __typename } __typename  } __typename} fragment DatasetMetadata on Dataset { id metadata { datasetId datasetUrl datasetName firstSnapshotCreatedAt latestSnapshotCreatedAt dxStatus tasksCompleted trialCount grantFunderName grantIdentifier studyDesign studyDomain studyLongitudinal dataProcessed species associatedPaperDOI openneuroPaperDOI seniorAuthor adminUsers ages modalities affirmedDefaced affirmedConsent __typename  } __typename}\"}"
              :openneuro-snapshot-request "{\"operationName\":\"snapshot\",\"variables\":{\"datasetId\":\"%s\",\"tag\":\"%s\"},\"query\":\"query snapshot($datasetId: ID!, $tag: String!) {  snapshot(datasetId: $datasetId, tag: $tag) { id ...SnapshotFields __typename  } }  fragment SnapshotFields on Snapshot {  id  tag  created  readme  deprecated { id user reason timestamp __typename  }  description { Name Authors DatasetDOI License Acknowledgements HowToAcknowledge Funding ReferencesAndLinks EthicsApprovals __typename  }  files { id key filename size directory annexed __typename  }  summary { modalities secondaryModalities sessions subjects subjectMetadata {   participantId age sex group __typename } tasks size totalFiles dataProcessed pet {   BodyPart   ScannerManufacturer   ScannerManufacturersModelName   TracerName   TracerRadionuclide __typename } __typename  }  ...SnapshotIssues  hexsha  onBrainlife  __typename }  fragment SnapshotIssues on Snapshot {  id  issues { severity code reason files {evidence line character reason file {  name  path   relativePath  __typename } __typename } additionalFileCount __typename } __typename }\"}"}})

(defmethod ig/init-key :database/connection [_ db-connection]
  (jdbc/get-connection db-connection))

(defmethod ig/init-key :backend/application [_ {:keys [db auth build-research-chan]}]
  (router/application db auth build-research-chan))

(defmethod ig/init-key :backend/server [_ {:keys [application port join?]}]
  (run-jetty application {:port port :join? join?}))

(defmethod ig/halt-key! :backend/server [_ server] (.stop server))

(defmethod ig/init-key :backend/auth [_ config] config)

(defmethod ig/init-key :scrape-dataset/worker [_ {:keys [db openneuro-url openneuro-dataset-request openneuro-snapshot-request]}]
  (let [start-ch         (async/chan)
        db-ch            (async/chan)
        version-ch       (async/chan)
        metadata-ch      (-> start-ch
                             (worker/dataset-preconditions
                               (complement (partial worker/dataset-exists? db)))
                             (worker/request-dataset-metadata
                               openneuro-url openneuro-dataset-request))
        metadata-ch-mult (async/mult metadata-ch)
        version-ch       (-> (async/tap metadata-ch-mult version-ch)
                             (worker/request-dataset-snapshot
                               openneuro-url openneuro-snapshot-request))
        ]
    (worker/save-dataset-metadata (async/tap metadata-ch-mult db-ch) db)
    [start-ch version-ch]
    ))

(defmethod ig/halt-key! :scrape-dataset/worker [_ channels]
  (doseq [x channels] (async/close! x)))
