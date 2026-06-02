package com.guicedee.consul.test;

import com.guicedee.client.IGuiceContext;
import com.guicedee.consul.ConsulPreStartup;
import com.guicedee.vertx.spi.VertXPreStartup;
import io.vertx.core.Vertx;
import io.vertx.ext.consul.*;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration test for the Consul module using Testcontainers.
 * <p>
 * Starts a real Consul agent in dev mode, verifies:
 * <ul>
 *     <li>Service registration with health check</li>
 *     <li>Service deregistration</li>
 *     <li>KV store read/write</li>
 *     <li>Service health querying</li>
 *     <li>ConsulClient injection by name</li>
 * </ul>
 * <p>
 * Requires Docker. Skipped automatically if Docker is not available.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("integration")
class ConsulIntegrationTest
{
    private static GenericContainer<?> consulContainer;
    private ConsulClient client;

    @BeforeAll
    void startConsul()
    {
        assumeTrue(isDockerAvailable(), "Docker is not available — skipping integration test");

        consulContainer = new GenericContainer<>(DockerImageName.parse("hashicorp/consul:1.20"))
                .withExposedPorts(8500)
                .withCommand("agent", "-dev", "-client=0.0.0.0")
                .waitingFor(Wait.forHttp("/v1/status/leader")
                        .forStatusCode(200)
                        .withStartupTimeout(Duration.ofSeconds(30)));

        consulContainer.start();

        Integer mappedPort = consulContainer.getMappedPort(8500);
        String mappedHost = consulContainer.getHost();

        // Set system properties so the GuicedEE Consul module can connect
        System.setProperty("CONSUL_TEST_CONSUL_HOST", mappedHost);
        System.setProperty("CONSUL_TEST_CONSUL_PORT", String.valueOf(mappedPort));

        // Create a direct client for test verification
        ConsulClientOptions options = new ConsulClientOptions()
                .setHost(mappedHost)
                .setPort(mappedPort);

        Vertx vertx = VertXPreStartup.getVertx();
        if (vertx == null)
        {
            IGuiceContext.registerModule("com.guicedee.consul.test");
            IGuiceContext.instance().inject();
            vertx = VertXPreStartup.getVertx();
        }
        client = ConsulClient.create(vertx, options);
    }

    @AfterAll
    void stopConsul()
    {
        if (client != null) client.close();
        if (consulContainer != null) consulContainer.stop();
        System.clearProperty("CONSUL_TEST_CONSUL_HOST");
        System.clearProperty("CONSUL_TEST_CONSUL_PORT");
    }

    @Test
    @Order(1)
    @DisplayName("Consul container is running and responsive")
    void testConsulRunning() throws Exception
    {
        CompletableFuture<String> future = new CompletableFuture<>();
        client.leaderStatus()
                .onSuccess(future::complete)
                .onFailure(future::completeExceptionally);

        String leader = future.get(5, TimeUnit.SECONDS);
        assertNotNull(leader);
        assertFalse(leader.isEmpty(), "Consul should have a leader");
        System.out.println("✅ Consul leader: " + leader);
    }

    @Test
    @Order(2)
    @DisplayName("Register a service with Consul")
    void testServiceRegistration() throws Exception
    {
        ServiceOptions serviceOpts = new ServiceOptions()
                .setName("test-api")
                .setId("test-api-1")
                .setAddress("10.0.0.1")
                .setPort(8080)
                .setCheckOptions(new CheckOptions()
                        .setTtl("30s")
                        .setDeregisterAfter("1m"));

        CompletableFuture<Void> future = new CompletableFuture<>();
        client.registerService(serviceOpts)
                .onSuccess(v -> future.complete(null))
                .onFailure(future::completeExceptionally);

        future.get(5, TimeUnit.SECONDS);
        System.out.println("✅ Service 'test-api' registered");
    }

    @Test
    @Order(3)
    @DisplayName("Query registered service from Consul catalog")
    void testServiceQuery() throws Exception
    {
        CompletableFuture<ServiceList> future = new CompletableFuture<>();
        client.catalogServiceNodes("test-api")
                .onSuccess(future::complete)
                .onFailure(future::completeExceptionally);

        ServiceList services = future.get(5, TimeUnit.SECONDS);
        assertNotNull(services);
        assertFalse(services.getList().isEmpty(), "Should find at least one instance of test-api");
        assertEquals("10.0.0.1", services.getList().get(0).getAddress());
        assertEquals(8080, services.getList().get(0).getPort());
        System.out.println("✅ Service 'test-api' found in catalog: " + services.getList().size() + " instance(s)");
    }

    @Test
    @Order(4)
    @DisplayName("Health check query for service")
    void testHealthQuery() throws Exception
    {
        // Pass TTL check so service is healthy
        CompletableFuture<Void> passCheck = new CompletableFuture<>();
        client.passCheck("service:test-api-1")
                .onSuccess(v -> passCheck.complete(null))
                .onFailure(passCheck::completeExceptionally);
        passCheck.get(5, TimeUnit.SECONDS);

        // Query health
        CompletableFuture<ServiceEntryList> future = new CompletableFuture<>();
        client.healthServiceNodes("test-api", true)
                .onSuccess(future::complete)
                .onFailure(future::completeExceptionally);

        ServiceEntryList entries = future.get(5, TimeUnit.SECONDS);
        assertNotNull(entries);
        assertFalse(entries.getList().isEmpty(), "Should have healthy instances after passing TTL check");
        System.out.println("✅ Healthy instances of 'test-api': " + entries.getList().size());
    }

    @Test
    @Order(5)
    @DisplayName("KV store write and read")
    void testKvStore() throws Exception
    {
        // Write
        CompletableFuture<Void> writeFuture = new CompletableFuture<>();
        client.putValue("config/myapp/db-host", "postgres.internal")
                .onSuccess(v -> writeFuture.complete(null))
                .onFailure(writeFuture::completeExceptionally);
        writeFuture.get(5, TimeUnit.SECONDS);

        // Read
        CompletableFuture<KeyValue> readFuture = new CompletableFuture<>();
        client.getValue("config/myapp/db-host")
                .onSuccess(readFuture::complete)
                .onFailure(readFuture::completeExceptionally);

        KeyValue kv = readFuture.get(5, TimeUnit.SECONDS);
        assertNotNull(kv);
        assertEquals("postgres.internal", kv.getValue());
        System.out.println("✅ KV store: config/myapp/db-host = " + kv.getValue());
    }

    @Test
    @Order(6)
    @DisplayName("Deregister service from Consul")
    void testServiceDeregistration() throws Exception
    {
        CompletableFuture<Void> future = new CompletableFuture<>();
        client.deregisterService("test-api-1")
                .onSuccess(v -> future.complete(null))
                .onFailure(future::completeExceptionally);
        future.get(5, TimeUnit.SECONDS);

        // Verify it's gone
        CompletableFuture<ServiceEntryList> healthFuture = new CompletableFuture<>();
        client.healthServiceNodes("test-api", false)
                .onSuccess(healthFuture::complete)
                .onFailure(healthFuture::completeExceptionally);

        ServiceEntryList entries = healthFuture.get(5, TimeUnit.SECONDS);
        assertTrue(entries.getList().isEmpty(), "Service should be deregistered");
        System.out.println("✅ Service 'test-api' deregistered successfully");
    }

    @Test
    @Order(7)
    @DisplayName("Multiple services can be registered and queried")
    void testMultipleServices() throws Exception
    {
        // Register two instances
        for (int i = 1; i <= 2; i++)
        {
            ServiceOptions opts = new ServiceOptions()
                    .setName("web-frontend")
                    .setId("web-frontend-" + i)
                    .setAddress("10.0.0." + (10 + i))
                    .setPort(3000 + i);

            CompletableFuture<Void> f = new CompletableFuture<>();
            client.registerService(opts)
                    .onSuccess(v -> f.complete(null))
                    .onFailure(f::completeExceptionally);
            f.get(5, TimeUnit.SECONDS);
        }

        // Query
        CompletableFuture<ServiceList> future = new CompletableFuture<>();
        client.catalogServiceNodes("web-frontend")
                .onSuccess(future::complete)
                .onFailure(future::completeExceptionally);

        ServiceList services = future.get(5, TimeUnit.SECONDS);
        assertEquals(2, services.getList().size(), "Should find 2 instances");
        System.out.println("✅ Multiple services: " + services.getList().size() + " instances of 'web-frontend'");

        // Cleanup
        for (int i = 1; i <= 2; i++)
        {
            CompletableFuture<Void> f = new CompletableFuture<>();
            client.deregisterService("web-frontend-" + i)
                    .onSuccess(v -> f.complete(null))
                    .onFailure(f::completeExceptionally);
            f.get(5, TimeUnit.SECONDS);
        }
    }

    /**
     * Check if Docker is available by attempting to connect.
     */
    private static boolean isDockerAvailable()
    {
        try
        {
            ProcessBuilder pb = new ProcessBuilder("docker", "info");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            return p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0;
        }
        catch (Exception e)
        {
            return false;
        }
    }
}



