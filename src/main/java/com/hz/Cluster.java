package com.hz;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.instance.BuildInfoProvider;

public class Cluster {
    private static final String BLUE_CONFIG_RESOURCE = "blue.xml";
    private static final String GREEN_CONFIG_RESOURCE = "green.xml";
    private static final String BLUE_CONFIG_RESOURCE_52 = "blue-5.2.xml";
    private static final String GREEN_CONFIG_RESOURCE_52 = "green-5.2.xml";
    private static final int BLUE_REST_PORT = 6701;
    private static final int GREEN_REST_PORT = 6702;
    public static final String LICENSE_PATH_PROPERTY = "hazelcast.enterprise.license.path";
    public static final String LICENSE_KEY_PROPERTY = "hazelcast.enterprise.license.key";
    public static final String IP = "127.0.0.1";
    public static final int BLUE_CLUSTER_PORT = 5701;
    public static final int GREEN_CLUSTER_PORT = 5702;
    public static final String BLUE_CLUSTER_NAME = "blue";
    public static final String GREEN_CLUSTER_NAME = "green";
    public static final String MAP_NAME = "replicatedMap";

    protected HazelcastInstance startBlueCluster() throws IOException {
        return Hazelcast.newHazelcastInstance(loadConfiguredCluster(
                BLUE_CONFIG_RESOURCE,
                BLUE_CLUSTER_NAME,
                BLUE_CLUSTER_PORT,
                BLUE_REST_PORT,
                GREEN_CLUSTER_NAME,
                IP + ":" + GREEN_CLUSTER_PORT,
                3));
    }

    protected HazelcastInstance startGreenCluster() throws IOException {
        return Hazelcast.newHazelcastInstance(loadConfiguredCluster(
                GREEN_CONFIG_RESOURCE,
                GREEN_CLUSTER_NAME,
                GREEN_CLUSTER_PORT,
                GREEN_REST_PORT,
                BLUE_CLUSTER_NAME,
                IP + ":" + BLUE_CLUSTER_PORT,
                3));
    }

    protected HazelcastInstance startBlueCluster(
            String clusterName,
            int port,
            String targetClusterName,
            String targetCluster,
            int queueCapacity
    ) throws IOException {
        return startBlueCluster(clusterName, port, port + 1000, targetClusterName, targetCluster, queueCapacity);
    }

    protected HazelcastInstance startBlueCluster(
            String clusterName,
            int port,
            int restPort,
            String targetClusterName,
            String targetCluster,
            int queueCapacity
    ) throws IOException {
        return Hazelcast.newHazelcastInstance(loadConfiguredCluster(
                BLUE_CONFIG_RESOURCE, clusterName, port, restPort, targetClusterName, targetCluster, queueCapacity));
    }

    protected HazelcastInstance startGreenCluster(
            String clusterName,
            int port,
            String targetClusterName,
            String targetCluster,
            int queueCapacity
    ) throws IOException {
        return startGreenCluster(clusterName, port, port + 1000, targetClusterName, targetCluster, queueCapacity);
    }

    protected HazelcastInstance startGreenCluster(
            String clusterName,
            int port,
            int restPort,
            String targetClusterName,
            String targetCluster,
            int queueCapacity
    ) throws IOException {
        return Hazelcast.newHazelcastInstance(loadConfiguredCluster(
                GREEN_CONFIG_RESOURCE, clusterName, port, restPort, targetClusterName, targetCluster, queueCapacity));
    }

    protected HazelcastInstance startCluster(
            String clusterName,
            int port,
            String targetClusterName,
            String targetCluster,
            int queueCapacity
    ) throws IOException {
        return startCluster(clusterName, port, port + 1000, targetClusterName, targetCluster, queueCapacity);
    }

    protected HazelcastInstance startCluster(
            String clusterName,
            int port,
            int restPort,
            String targetClusterName,
            String targetCluster,
            int queueCapacity
    ) throws IOException {
        String configResource = clusterName.startsWith(BLUE_CLUSTER_NAME) ? BLUE_CONFIG_RESOURCE : GREEN_CONFIG_RESOURCE;
        return Hazelcast.newHazelcastInstance(loadConfiguredCluster(
                configResource, clusterName, port, restPort, targetClusterName, targetCluster, queueCapacity));
    }

    protected Config newConfig(
            String clusterName,
            int port,
            int restPort,
            String targetClusterName,
            String targetCluster,
            int queueCapacity
    ) throws IOException {
        String configResource = clusterName.startsWith(BLUE_CLUSTER_NAME) ? BLUE_CONFIG_RESOURCE : GREEN_CONFIG_RESOURCE;
        return loadConfiguredCluster(configResource, clusterName, port, restPort, targetClusterName, targetCluster, queueCapacity);
    }

    private Config loadConfiguredCluster(
            String configResource,
            String clusterName,
            int port,
            int restPort,
            String targetClusterName,
            String targetCluster,
            int queueCapacity
    ) throws IOException {
        Config config = Config.loadFromClasspath(
                Cluster.class.getClassLoader(),
                versionedConfigResource(configResource),
                runtimeProperties(clusterName, port, restPort, targetClusterName, targetCluster, queueCapacity));
        config.setLicenseKey(resolveLicenseKey());
        return config;
    }

    private String versionedConfigResource(String configResource) {
        String hazelcastVersion = BuildInfoProvider.getBuildInfo().getVersion();
        if (hazelcastVersion != null && hazelcastVersion.startsWith("5.2.")) {
            if (BLUE_CONFIG_RESOURCE.equals(configResource)) {
                return BLUE_CONFIG_RESOURCE_52;
            }
            if (GREEN_CONFIG_RESOURCE.equals(configResource)) {
                return GREEN_CONFIG_RESOURCE_52;
            }
        }
        return configResource;
    }

    private Properties runtimeProperties(
            String clusterName,
            int port,
            int restPort,
            String targetClusterName,
            String targetCluster,
            int queueCapacity
    ) throws IOException {
        Path diagnosticsDirectory = Path.of(System.getProperty("java.io.tmpdir"), "hazelcast-diagnostics", clusterName);
        Files.createDirectories(diagnosticsDirectory);

        Properties properties = new Properties();
        properties.setProperty("cluster.name", clusterName);
        properties.setProperty("member.port", Integer.toString(port));
        properties.setProperty("rest.port", Integer.toString(restPort));
        properties.setProperty("target.cluster.name", targetClusterName);
        properties.setProperty("target.endpoints", targetCluster);
        properties.setProperty("wan.queue.capacity", Integer.toString(queueCapacity));
        properties.setProperty("wan.replication.name", clusterName + "-wan-rep");
        properties.setProperty("publisher.id", clusterName + "-" + targetClusterName);
        properties.setProperty("diagnostics.directory", diagnosticsDirectory.toString());
        return properties;
    }

    private String resolveLicenseKey() throws IOException {
        String inlineLicense = System.getProperty(LICENSE_KEY_PROPERTY);
        if (inlineLicense != null && !inlineLicense.isBlank()) {
            return inlineLicense.trim();
        }

        Path configuredPath = configuredLicensePath();
        if (Files.exists(configuredPath)) {
            return Files.readString(configuredPath).trim();
        }
        throw new IOException("Hazelcast Enterprise license file not found at " + configuredPath
                + ". Set -D" + LICENSE_PATH_PROPERTY + "=... or -D" + LICENSE_KEY_PROPERTY + "=...");
    }

    private Path configuredLicensePath() {
        String configuredPath = System.getProperty(LICENSE_PATH_PROPERTY);
        if (configuredPath != null && !configuredPath.isBlank()) {
            return Paths.get(configuredPath);
        }

        Path defaultLicense = Path.of(System.getProperty("user.home"), "hazelcast", "demo.license");
        if (Files.exists(defaultLicense)) {
            return defaultLicense;
        }
        return Path.of(System.getProperty("user.home"), "hazelcast", "demo.license.old");
    }

    public static void main(String[] args) throws IOException {
        Cluster cluster = new Cluster();
        cluster.startBlueCluster();
        cluster.startGreenCluster();
    }
}
