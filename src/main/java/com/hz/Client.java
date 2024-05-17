package com.hz;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.client.config.ClientFailoverConfig;
import com.hazelcast.client.config.ClientNetworkConfig;
import com.hazelcast.client.config.ConnectionRetryConfig;
import com.hazelcast.core.HazelcastInstance;

public class Client {
    public static void main(String[] args){

        ClientConfig clientConfig = new ClientConfig();
        clientConfig.setClusterName(Cluster.BLUE_CLUSTER_NAME);
        ClientNetworkConfig networkConfig = clientConfig.getNetworkConfig();
        networkConfig.addAddress("localhost:"+Cluster.BLUE_CLUSTER_PORT);
        ConnectionRetryConfig connectionRetryConfig = clientConfig.getConnectionStrategyConfig().getConnectionRetryConfig();
        connectionRetryConfig.setClusterConnectTimeoutMillis(10);
        // connectionRetryConfig.setMaxBackoffMillis(10);

        ClientConfig clientConfig2 = new ClientConfig();
        clientConfig2.setClusterName(Cluster.GREEN_CLUSTER_NAME);
        ClientNetworkConfig networkConfig2 = clientConfig2.getNetworkConfig();
        networkConfig2.addAddress( "localhost:"+Cluster.GREEN_CLUSTER_PORT);
        ConnectionRetryConfig connectionRetryConfig2 = clientConfig2.getConnectionStrategyConfig().getConnectionRetryConfig();
        connectionRetryConfig2.setClusterConnectTimeoutMillis(10);
        // connectionRetryConfig2.setMaxBackoffMillis(10);

        ClientFailoverConfig clientFailoverConfig = new ClientFailoverConfig();
        clientFailoverConfig.addClientConfig(clientConfig).addClientConfig(clientConfig2).setTryCount(2);
        HazelcastInstance client = HazelcastClient.newHazelcastFailoverClient(clientFailoverConfig);
    }

}
