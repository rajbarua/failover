# WAN Tests
## WAN Sync tests
Prove syncronisation under the following condition:
1. Keep all in sync and then shut `blue` down and add some records to `green`. Start `blue` and execute full sync from `green` to `blue`. Verify that all records are available in `blue`. No records deleted in `green`.
    - Start both cluster using class `BlueCluster` and `GreenCluster` and MC using `hz-mc start`. Make sure blue and green clusters are available with WAN setup.
    - Add CLC config `clc config add blue cluster.name=blue cluster.address=localhost:5701` and `clc config add green cluster.name=green cluster.address=localhost:5702`
    - Using CLC add some records to `blue` and `green` 
        ```
        clc -c blue -n replicatedMap map set key1 value1
        clc -c blue -n replicatedMap map get key1
        clc -c green -n replicatedMap map get key1
        clc -c green -n replicatedMap map set key2 value2
        clc -c green -n replicatedMap map get key2
        clc -c blue -n replicatedMap map get key2
        ```
    - Now shutdown `blue` cluster and add some records to `green`
        ```
        clc -c green -n replicatedMap map set key3 value3
        clc -c green -n replicatedMap map get key3
        ```
    - Start `blue` cluster and check if all records are available in `blue`
        ```
        clc -c blue -n replicatedMap map entry-set
        ```
        Results will show that `blue` cluster is missing `key1` and `key2` records but has `key3`. This happened becuase the WAN queue in `green` cluster was holding `key3` record and when `blue` cluster was unavailale but `key1` and `key2` records were not queued.
    - Sync from `green` to `blue` cluster using MC. Go to `green` cluster in MC -> WAN Replication -> Sync -> and pick the wan rep, EP and Maps and click on `Sync` button.
    - Now test if all records are available in `blue` cluster
        ```
        clc -c blue -n replicatedMap map entry-set
        ```
        Results will show that all records are available in `blue` cluster.
    
1. Simulate network outage for a prolonged period by breaking the network between `blue` and `green` cluster. With a WAN queue size of 3, messages start dropping. Once network is restored, manually sync from `green` to `blue` cluster. Verify that all records are available in `blue` cluster.
    - Start both cluster using class `BlueCluster` and `GreenCluster` and MC using `hz-mc start`. Make sure blue and green clusters are available with WAN setup.
    - If not done, Add CLC config `clc config add blue cluster.name=blue cluster.address=localhost:5701` and `clc config add green cluster.name=green cluster.address=localhost:5702`
    - Using CLC add some records to `blue` and `green` 
        ```
        clc -c green -n replicatedMap map set key1 value1
        clc -c green -n replicatedMap map get key1
        clc -c blue -n replicatedMap map get key1
        ```
    - Now simulate network outage by blocking the network between `blue` and `green` cluster by copying the following command in terminal. **<span style="color:red;">CAUTION--></span>**I use [pfctl](https://docs.freebsd.org/en/books/handbook/firewalls/#firewalls-pf) to block the network but you can use other mechanism. This can mess up your system network settings. Use with caution.**<span style="color:red;"><--CAUTION</span>**
        ```
        sudo cp com.hazelcast.5701.conf /etc/pf.anchors
        sudo pfctl -ef /etc/pf.anchors/com.hazelcast.5701.conf
        ```
    - Add some records to `green` cluster
        ```
        clc -c green -n replicatedMap map set key2 value2
        clc -c green -n replicatedMap map set key3 value3
        clc -c green -n replicatedMap map set key4 value4
        clc -c green -n replicatedMap map set key5 value5
        clc -c green -n replicatedMap map set key6 value6
        clc -c green -n replicatedMap map remove key1
        clc -c green -n replicatedMap map entry-set
        ```
        Querying `blue` cluster will result in timeout or error. Ctrl+C to stop the query.
        ```
        clc -c blue -n replicatedMap map entry-set
        ```
    - Remove the rule
        ```
        sudo rm /etc/pf.anchors/com.hazelcast.5701.conf
        sudo pfctl -F all -f /etc/pf.conf
        ```
    - Now if you query `blue` cluster, you will see that `key5` and `key6` are unavailable due to the fact that `GreenCluster` is created with a WAN queue size of 3 we created 4 entries in `green` cluster.  
        ```
        clc -c blue -n replicatedMap map entry-set
        ```
    - Sync from `green` to `blue` cluster using MC. Go to `green` cluster in MC -> WAN Replication -> Sync -> and pick the wan rep, EP and Maps and click on `Sync` button.
    - **<span style="color:red;">Issue:</span>**Note that `key1` exists in `blue` cluster but not in `green` cluster. This is because the `key1` was deleted in `green` cluster but as replication queue was overflowing, the delete operation was dropped. A full sync does not fix this but can delta fix this? Lets check. 
## Clint Failover
Demonstrate client failover by shutting off one cluster and observing the switch over. Use classes `Cluster` and `Client`.

