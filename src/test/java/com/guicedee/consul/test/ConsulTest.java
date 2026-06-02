package com.guicedee.consul.test;

import com.guicedee.client.IGuiceContext;
import com.guicedee.consul.ConsulOptions;
import com.guicedee.consul.ConsulPreStartup;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Consul module demonstrating:
 * <ul>
 *   <li>Package-level @ConsulOptions annotation scanning</li>
 *   <li>Environment variable override resolution</li>
 *   <li>Multiple named Consul configurations</li>
 *   <li>ConsulClient binding by name</li>
 * </ul>
 *
 * <h2>Usage Examples</h2>
 *
 * <h3>1. Basic package-info configuration</h3>
 * <pre>{@code
 * @ConsulOptions(
 *     value = "default",
 *     host = "consul.service.internal",
 *     port = 8500,
 *     token = "${CONSUL_ACL_TOKEN}",
 *     registerService = true,
 *     serviceName = "my-api",
 *     servicePort = 8080,
 *     healthPath = "/health/ready"
 * )
 * package com.myapp;
 * import com.guicedee.consul.ConsulOptions;
 * }</pre>
 *
 * <h3>2. Inject ConsulClient</h3>
 * <pre>{@code
 * @Inject @Named("default")
 * ConsulClient consulClient;
 *
 * // Use KV store
 * consulClient.getValue("config/my-key").onSuccess(kv -> {
 *     String value = kv.getValue();
 * });
 * }</pre>
 *
 * <h3>3. Environment variable overrides</h3>
 * <pre>
 * CONSUL_DEFAULT_HOST=consul.prod.internal
 * CONSUL_DEFAULT_PORT=8501
 * CONSUL_DEFAULT_TOKEN=prod-acl-token
 * CONSUL_TOKEN=fallback-token  (global fallback)
 * </pre>
 *
 * <h3>4. Multiple Consul instances</h3>
 * <pre>{@code
 * // In package com.myapp.primary
 * @ConsulOptions(value = "primary", host = "consul-1.internal")
 *
 * // In package com.myapp.secondary
 * @ConsulOptions(value = "secondary", host = "consul-2.internal")
 *
 * // Inject specific instances
 * @Inject @Named("primary") ConsulClient primaryClient;
 * @Inject @Named("secondary") ConsulClient secondaryClient;
 * }</pre>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ConsulTest
{
    @BeforeAll
    void setUp()
    {
        IGuiceContext.registerModule("com.guicedee.consul.test");
        IGuiceContext.instance().inject();
    }

    @AfterAll
    void tearDown()
    {
        IGuiceContext.instance().destroy();
    }

    @Test
    @Order(1)
    @DisplayName("Annotation scanning discovers @ConsulOptions from package-info")
    void testAnnotationScanningDiscoversPackageOptions()
    {
        var options = ConsulPreStartup.getNamedConsulOptions();
        assertTrue(options.containsKey("test-consul"),
                "Should discover 'test-consul' from annotated package-info. Found: " + options.keySet());
        System.out.println("✅ @ConsulOptions discovered from package-info");
    }

    @Test
    @Order(2)
    @DisplayName("Annotation values are correctly resolved")
    void testAnnotationValuesResolved()
    {
        ConsulOptions opts = ConsulPreStartup.getNamedConsulOptions().get("test-consul");
        assertNotNull(opts);
        assertEquals("127.0.0.1", opts.host());
        assertEquals(8500, opts.port());
        assertEquals("test-token-123", opts.token());
        assertEquals("dc1", opts.datacenter());
        assertEquals("guicedee/test/myapp", opts.configPrefix());
        assertTrue(opts.registerService());
        assertEquals("test-service", opts.serviceName());
        assertEquals("test-service-1", opts.serviceId());
        assertEquals("10.0.0.1", opts.serviceAddress());
        assertEquals(9090, opts.servicePort());
        assertEquals("/health/live", opts.healthPath());
        assertEquals("15s", opts.healthInterval());
        assertEquals("2m", opts.deregisterAfter());
        System.out.println("✅ All annotation values correctly resolved");
    }

    @Test
    @Order(3)
    @DisplayName("Multiple named Consul configurations discovered")
    void testMultipleNamedConfigurations()
    {
        var options = ConsulPreStartup.getNamedConsulOptions();
        assertTrue(options.containsKey("test-consul"), "Should have test-consul");
        assertTrue(options.containsKey("second-consul"), "Should have second-consul");

        ConsulOptions second = options.get("second-consul");
        assertEquals("consul.cluster.local", second.host());
        assertEquals(8501, second.port());
        assertTrue(second.ssl());
        assertEquals("dc2", second.datacenter());
        System.out.println("✅ Multiple named Consul configurations discovered");
    }

    @Test
    @Order(4)
    @DisplayName("Package-level options mapped correctly")
    void testPackageLevelMapping()
    {
        var pkgOptions = ConsulPreStartup.getPackageConsulOptions();
        assertTrue(pkgOptions.containsKey("com.guicedee.consul.test.annotated"),
                "Should map annotated package");
        assertTrue(pkgOptions.containsKey("com.guicedee.consul.test.second"),
                "Should map second package");
        System.out.println("✅ Package-level options mapped correctly");
    }

    @Test
    @Order(5)
    @DisplayName("Environment variable override - name-scoped")
    void testEnvOverrideNameScoped()
    {
        System.setProperty("CONSUL_TEST_CONSUL_HOST", "override.consul.io");
        try
        {
            String resolved = ConsulPreStartup.envForName("test-consul", "HOST", "fallback");
            assertEquals("override.consul.io", resolved,
                    "Should resolve CONSUL_TEST_CONSUL_HOST from system property");
            System.out.println("✅ Name-scoped env override works: CONSUL_TEST_CONSUL_HOST");
        }
        finally
        {
            System.clearProperty("CONSUL_TEST_CONSUL_HOST");
        }
    }

    @Test
    @Order(6)
    @DisplayName("Environment variable override - global fallback")
    void testEnvOverrideGlobalFallback()
    {
        System.setProperty("CONSUL_TOKEN", "global-fallback-token");
        try
        {
            String resolved = ConsulPreStartup.envForName("nonexistent", "TOKEN", "default");
            assertEquals("global-fallback-token", resolved,
                    "Should fall back to CONSUL_TOKEN global property");
            System.out.println("✅ Global env fallback works: CONSUL_TOKEN");
        }
        finally
        {
            System.clearProperty("CONSUL_TOKEN");
        }
    }

    @Test
    @Order(7)
    @DisplayName("Environment variable - annotation default used when no override")
    void testEnvDefaultsToAnnotationValue()
    {
        String resolved = ConsulPreStartup.envForName("test-consul", "PORT", "9999");
        assertEquals("9999", resolved,
                "Should return annotation default when no env override exists");
        System.out.println("✅ Annotation defaults work when no env override present");
    }

    @Test
    @Order(8)
    @DisplayName("Name normalization for env vars (hyphens/dots → underscores)")
    void testNameNormalization()
    {
        System.setProperty("CONSUL_MY_APP_SERVICE_HOST", "normalized.host");
        try
        {
            String resolved = ConsulPreStartup.envForName("my-app.service", "HOST", "x");
            assertEquals("normalized.host", resolved,
                    "Hyphens and dots should be normalized to underscores");
            System.out.println("✅ Name normalization works (my-app.service → MY_APP_SERVICE)");
        }
        finally
        {
            System.clearProperty("CONSUL_MY_APP_SERVICE_HOST");
        }
    }
}




