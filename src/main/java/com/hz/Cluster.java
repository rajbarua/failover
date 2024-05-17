package com.hz;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import com.hazelcast.config.Config;
import com.hazelcast.config.ConsistencyCheckStrategy;
import com.hazelcast.config.WanBatchPublisherConfig;
import com.hazelcast.config.WanQueueFullBehavior;
import com.hazelcast.config.WanReplicationConfig;
import com.hazelcast.config.WanReplicationRef;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.spi.merge.LatestUpdateMergePolicy;

public class Cluster {
    public final static String LICENSE = System.getProperty("user.home") + "/hazelcast/demo.license.old";
    public final static String IP = "127.0.0.1";
    public final static int BLUE_CLUSTER_PORT = 5701;
    public final static int GREEN_CLUSTER_PORT = 5702;
    public final static String BLUE_CLUSTER_NAME = "blue";
    public final static String GREEN_CLUSTER_NAME = "green";


    protected HazelcastInstance startCluster(String clusterName, int port, String targetClusterName, String targetCluster
            , int queueCapacity) throws IOException {
        Config config = new Config();
        String lic = new String(Files.readAllBytes(Paths.get(LICENSE))).toString();
        System.out.println("License::"+lic);
        config.setLicenseKey(lic);
        config.setClusterName(clusterName);
        config.getNetworkConfig().setPort(port);
        config.getNetworkConfig().getInterfaces().addInterface("127.0.0.1").setEnabled(true);
        //turn on diagnostic
        config.setProperty( "hazelcast.diagnostics.enabled", "true" );
        config.setProperty( "hazelcast.diagnostics.metric.level", "info" );
        config.setProperty( "hazelcast.diagnostics.invocation.sample.period.seconds", "30" );
        config.setProperty( "hazelcast.diagnostics.pending.invocations.period.seconds", "30" );
        config.setProperty( "hazelcast.diagnostics.slowoperations.period.seconds", "30" );
        config.setProperty( "hazelcast.diagnostics.storeLatency.period.seconds", "60" );
        config.setProperty( "hazelcast.diagnostics.directory", "/Users/raj/logs" );
        
        String wanRepName = clusterName + "-wan-rep";
        WanReplicationConfig wrConfig = new WanReplicationConfig()
            .setName(wanRepName);

        if(targetClusterName != null) {
            WanBatchPublisherConfig batchPublisherConfig1 = new WanBatchPublisherConfig()
                .setPublisherId(clusterName + "-" + targetClusterName)
                .setClusterName(targetClusterName)
                .setTargetEndpoints(targetCluster)
                //set low queue capacity to test queue full behavior
                .setQueueCapacity(queueCapacity)
                //simply drop messages from WAN queue if queue is full
                .setQueueFullBehavior(WanQueueFullBehavior.DISCARD_AFTER_MUTATION);
            //set merkle tree
            batchPublisherConfig1.getSyncConfig().setConsistencyCheckStrategy(ConsistencyCheckStrategy.MERKLE_TREES);

            wrConfig.addBatchReplicationPublisherConfig(batchPublisherConfig1);
            //redundant config as per customer

        }
                
        config.addWanReplicationConfig(wrConfig);
        config.getMapConfig("replicatedMap").setPerEntryStatsEnabled(true);
        config.getMapConfig("replicatedMap").setWanReplicationRef(new WanReplicationRef().setRepublishingEnabled(true)
            .setMergePolicyClassName(LatestUpdateMergePolicy.class.getName()).setName(wrConfig.getName()));
        //configure merkle tree
        config.getMapConfig("replicatedMap").getMerkleTreeConfig().setEnabled(true);
        
        return Hazelcast.newHazelcastInstance(config);
    }
    public static void main(String[] args) throws IOException {
        Cluster cluster = new Cluster();
        cluster.startCluster(BLUE_CLUSTER_NAME, BLUE_CLUSTER_PORT, GREEN_CLUSTER_NAME, IP + ":" + GREEN_CLUSTER_PORT, 10000);
        cluster.startCluster(GREEN_CLUSTER_NAME, GREEN_CLUSTER_PORT, BLUE_CLUSTER_NAME, IP + ":" +  BLUE_CLUSTER_PORT, 10000);
    }
}