package com.hz;

import java.io.IOException;

public class BlueCluster extends Cluster{
    public static void main(String[] args) throws IOException{
        BlueCluster cluster = new BlueCluster();
        cluster.startCluster(BLUE_CLUSTER_NAME, BLUE_CLUSTER_PORT, GREEN_CLUSTER_NAME, IP + ":" + GREEN_CLUSTER_PORT, 3);
    }

}
