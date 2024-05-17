package com.hz;

import java.io.IOException;

public class GreenCluster extends Cluster{
    public static void main(String[] args) throws IOException{
        GreenCluster cluster = new GreenCluster();
        cluster.startCluster(GREEN_CLUSTER_NAME, GREEN_CLUSTER_PORT, BLUE_CLUSTER_NAME, IP + ":" + BLUE_CLUSTER_PORT, 3);
    }

}
