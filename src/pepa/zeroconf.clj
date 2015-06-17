(ns pepa.zeroconf
  (:require [com.stuartsierra.component :as component]
            [pepa.log :as log])
  (:import [javax.jmdns JmDNS ServiceInfo]
           [java.net InetAddress]))

(defmulti service-info (fn [module config] module))
(defmethod service-info :default [module _] nil)

(defn ^:private map->ServiceInfo [service]
  (let [{:keys [type name port props]} service]
    (assert (string? type))
    (assert (string? name))
    (assert (integer? port))
    (assert (map? props))
    (assert (every? string? (keys props)))
    (assert (every? string? (vals props)))
    (ServiceInfo/create ^String type ^String name ^int port 10 10 true props)))

(defn ^:private service-infos [zeroconf]
  (keep (fn [module]
          (let [service (service-info module (:config zeroconf))]
            (assert (or (nil? service) (map? service)))
            (if service
              (map->ServiceInfo service)
              (log/warn zeroconf (str "Couldn't get service-info for " module)))))
        (get-in zeroconf [:config :zeroconf :modules])))

(defrecord Zeroconf [config mdns]
  component/Lifecycle
  (start [component]
    (if (get-in config [:zeroconf :enable])
      (do
        (log/info component "Starting Zeroconf Announcements")
        (let [ip (if-let [ip (get-in config [:zeroconf :ip-address])]
                   (InetAddress/getByName ip)
                   (InetAddress/getLocalHost))
              jmdns (JmDNS/create ip nil)]
          (doseq [service (service-infos component)]
            (log/info component "Announcing service:" (.getType service))
            (.registerService jmdns service))
          (assoc component :mdns jmdns)))
      component))
  (stop [component]
    (log/info component "Stopping Zeroconf Announcement Service")
    (when-let [mdns (:mdns component)]
      (.close mdns))
    (assoc component
           :mdns nil)))

(defn make-zeroconf-component []
  (map->Zeroconf {}))
