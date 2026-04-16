package com.hz;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.instance.impl.HazelcastInstanceProxy;
import com.hazelcast.instance.impl.HazelcastInstanceImpl;
import com.hazelcast.internal.json.Json;
import com.hazelcast.internal.json.JsonObject;
import com.hazelcast.internal.monitor.LocalWanPublisherStats;
import com.hazelcast.internal.monitor.LocalWanStats;
import com.hazelcast.internal.monitor.WanSyncState;
import com.hazelcast.map.IMap;
import com.hazelcast.wan.impl.WanReplicationService;
import com.hazelcast.wan.impl.WanSyncStats;
import com.hazelcast.wan.impl.WanSyncStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class WanSyncRestApiTest {

    @Test
    void syncMapViaRestApiRepairsRestartedTargetCluster() throws Exception {
        try (TestClusters clusters = new TestClusters()) {
            clusters.startBlue();
            clusters.startGreen();

            IMap<String, String> greenMap = clusters.green().getMap(Cluster.MAP_NAME);
            IMap<String, String> blueMap = clusters.blue().getMap(Cluster.MAP_NAME);

            greenMap.put("key1", "value1");
            greenMap.put("key2", "value2");
            waitUntil(() -> blueMap.size() == 2, Duration.ofSeconds(20), "initial WAN replication to target");

            clusters.stopBlue();
            greenMap.put("key3", "value3");
            clusters.startBlue();

            waitUntil(() -> clusters.blue().getMap(Cluster.MAP_NAME).containsKey("key3"),
                    Duration.ofSeconds(30),
                    "queued mutation to reach restarted target");

            IMap<String, String> restartedBlueMap = clusters.blue().getMap(Cluster.MAP_NAME);
            assertFalse(restartedBlueMap.containsKey("key1"));
            assertFalse(restartedBlueMap.containsKey("key2"));
            assertTrue(restartedBlueMap.containsKey("key3"));

            UUID syncId = clusters.triggerMapSync();
            SyncProgress progress = clusters.awaitSyncFinished(syncId);
            assertEquals("FINISHED", progress.stage());
            assertEquals(100, progress.progress());

            waitUntil(() -> {
                IMap<String, String> targetMap = clusters.blue().getMap(Cluster.MAP_NAME);
                return targetMap.size() == 3
                        && "value1".equals(targetMap.get("key1"))
                        && "value2".equals(targetMap.get("key2"))
                        && "value3".equals(targetMap.get("key3"));
            }, Duration.ofSeconds(30), "full WAN sync to update target map");

            WanReplicationService wanReplicationService = clusters.greenWanService();
            WanSyncState wanSyncState = wanReplicationService.getWanSyncState();
            assertEquals(WanSyncStatus.READY, wanSyncState.getStatus());
            assertEquals(clusters.greenWanReplicationName(), wanSyncState.getActiveWanConfigName());
            assertEquals(clusters.greenPublisherId(), wanSyncState.getActivePublisherName());

            LocalWanStats localWanStats = wanReplicationService.getStats().get(clusters.greenWanReplicationName());
            assertNotNull(localWanStats);
            LocalWanPublisherStats publisherStats = localWanStats.getLocalWanPublisherStats().get(clusters.greenPublisherId());
            assertNotNull(publisherStats);
            WanSyncStats syncStats = publisherStats.getLastSyncStats().get(Cluster.MAP_NAME);
            assertNotNull(syncStats);
            assertEquals(syncId, syncStats.getUuid());
            assertTrue(syncStats.getPartitionsSynced() > 0);
            assertTrue(syncStats.getRecordsSynced() >= 2);
        }
    }

    @Test
    void syncProgressEndpointReturnsFailureForUnknownUuid() throws Exception {
        try (TestClusters clusters = new TestClusters()) {
            clusters.startBlue();
            clusters.startGreen();

            HttpJsonResponse response = clusters.fetchSyncProgress(UUID.randomUUID());
            assertEquals(200, response.statusCode());
            assertEquals("fail", response.body().getString("status", ""));
            assertTrue(response.body().getString("message", "").contains("couldn't be found"));
        }
    }

    private static void waitUntil(BooleanSupplier condition, Duration timeout, String description) throws InterruptedException {
        long deadlineNanos = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadlineNanos) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(200);
        }
        assertTrue(condition.getAsBoolean(), "Timed out waiting for " + description);
    }

    private static final class SyncProgress {
        private final String stage;
        private final int progress;

        private SyncProgress(String stage, int progress) {
            this.stage = stage;
            this.progress = progress;
        }

        private String stage() {
            return stage;
        }

        private int progress() {
            return progress;
        }
    }

    private static final class HttpJsonResponse {
        private final int statusCode;
        private final JsonObject body;

        private HttpJsonResponse(int statusCode, JsonObject body) {
            this.statusCode = statusCode;
            this.body = body;
        }

        private int statusCode() {
            return statusCode;
        }

        private JsonObject body() {
            return body;
        }
    }

    private static final class TestClusters implements AutoCloseable {
        private static final String HOST = "127.0.0.1";

        private final Cluster clusterFactory = new Cluster();
        private final HttpClient httpClient = HttpClient.newHttpClient();
        private final String suffix = UUID.randomUUID().toString().substring(0, 8);
        private final String blueName = Cluster.BLUE_CLUSTER_NAME + "-" + suffix;
        private final String greenName = Cluster.GREEN_CLUSTER_NAME + "-" + suffix;
        private final int bluePort = availablePort();
        private final int blueRestPort = availablePort();
        private final int greenPort = availablePort();
        private final int greenRestPort = availablePort();
        private HazelcastInstance blue;
        private HazelcastInstance green;

        private void startBlue() throws IOException {
            if (blue == null) {
                blue = clusterFactory.startBlueCluster(blueName, bluePort, blueRestPort, greenName, endpoint(greenPort), 50);
            }
        }

        private void startGreen() throws IOException {
            if (green == null) {
                green = clusterFactory.startGreenCluster(greenName, greenPort, greenRestPort, blueName, endpoint(bluePort), 50);
            }
        }

        private void stopBlue() {
            if (blue != null) {
                blue.shutdown();
                blue = null;
            }
        }

        private HazelcastInstance blue() {
            return blue;
        }

        private HazelcastInstance green() {
            return green;
        }

        private UUID triggerMapSync() throws IOException, InterruptedException {
            String requestBody = joinEncoded(
                    greenName,
                    "",
                    greenWanReplicationName(),
                    greenPublisherId(),
                    Cluster.MAP_NAME
            );
            HttpJsonResponse response = post("/hazelcast/rest/wan/sync/map", requestBody);
            assertEquals(200, response.statusCode());
            assertEquals("success", response.body().getString("status", ""));
            return UUID.fromString(response.body().getString("uuid", null));
        }

        private SyncProgress awaitSyncFinished(UUID syncId) throws Exception {
            long deadlineNanos = System.nanoTime() + Duration.ofSeconds(30).toNanos();
            while (System.nanoTime() < deadlineNanos) {
                HttpJsonResponse response = fetchSyncProgress(syncId);
                assertEquals(200, response.statusCode());
                JsonObject result = response.body().get("result").asObject();
                String stage = result.getString("stage", "");
                int progress = result.getInt("progress", -1);
                if ("FINISHED".equals(stage)) {
                    return new SyncProgress(stage, progress);
                }
                if ("FAILED".equals(stage)) {
                    fail("WAN sync failed: " + response.body());
                }
                Thread.sleep(200);
            }
            HttpJsonResponse lastResponse = fetchSyncProgress(syncId);
            fail("Timed out waiting for sync to finish. Last response: " + lastResponse.body());
            return null;
        }

        private HttpJsonResponse fetchSyncProgress(UUID syncId) throws IOException, InterruptedException {
            return get("/hazelcast/rest/wan/sync/progress/" + syncId);
        }

        private WanReplicationService greenWanService() {
            HazelcastInstanceImpl instance = ((HazelcastInstanceProxy) green).getOriginal();
            return instance.node.getNodeEngine().getWanReplicationService();
        }

        private String greenWanReplicationName() {
            return greenName + "-wan-rep";
        }

        private String greenPublisherId() {
            return greenName + "-" + blueName;
        }

        private HttpJsonResponse post(String path, String body) throws IOException, InterruptedException {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://" + HOST + ":" + greenRestPort + path))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "text/plain; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return new HttpJsonResponse(response.statusCode(), Json.parse(response.body()).asObject());
        }

        private HttpJsonResponse get(String path) throws IOException, InterruptedException {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://" + HOST + ":" + greenRestPort + path))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return new HttpJsonResponse(response.statusCode(), Json.parse(response.body()).asObject());
        }

        private static String joinEncoded(String... values) {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < values.length; i++) {
                if (i > 0) {
                    builder.append('&');
                }
                builder.append(URLEncoder.encode(values[i], StandardCharsets.UTF_8));
            }
            return builder.toString();
        }

        private static String endpoint(int port) {
            return HOST + ":" + port;
        }

        private static int availablePort() {
            try (ServerSocket socket = new ServerSocket(0)) {
                return socket.getLocalPort();
            } catch (IOException e) {
                throw new IllegalStateException("Unable to allocate test port", e);
            }
        }

        @Override
        public void close() {
            if (green != null) {
                green.shutdown();
                green = null;
            }
            if (blue != null) {
                blue.shutdown();
                blue = null;
            }
        }
    }
}
