package com.guicedee.consul;

import com.guicedee.client.IGuiceContext;
import com.guicedee.client.services.lifecycle.IGuicePreStartup;
import com.guicedee.vertx.spi.VertXPreStartup;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.PackageInfo;
import io.github.classgraph.ScanResult;
import io.vertx.core.Future;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pre-startup scanner that discovers {@link ConsulOptions} annotations on packages and classes,
 * wraps them with environment variable resolution, and stores metadata for binding.
 */
@Log4j2
public class ConsulPreStartup implements IGuicePreStartup<ConsulPreStartup>
{
    @Getter
    private static final Map<String, ConsulOptions> packageConsulOptions = new ConcurrentHashMap<>();

    @Getter
    private static final Map<String, ConsulOptions> namedConsulOptions = new ConcurrentHashMap<>();

    @Override
    public List<Future<Boolean>> onStartup()
    {
        return List.of(VertXPreStartup.getVertx().executeBlocking(() -> {
            ScanResult scanResult = IGuiceContext.instance().getScanResult();
            scanPackageAnnotations(scanResult);
            scanClassAnnotations(scanResult);
            log.info("🔍 Discovered {} Consul configuration(s)", namedConsulOptions.size());
            return true;
        }));
    }

    private void scanPackageAnnotations(ScanResult scanResult)
    {
        for (PackageInfo packageInfo : scanResult.getPackageInfo())
        {
            var annotationInfo = packageInfo.getAnnotationInfo(ConsulOptions.class.getName());
            if (annotationInfo != null)
            {
                var params = annotationInfo.getParameterValues();
                Map<String, Object> snapshot = new java.util.HashMap<>();
                for (var pv : params)
                {
                    snapshot.put(pv.getName(), pv.getValue());
                }
                ConsulOptions syntheticAnnotation = buildFromSnapshot(snapshot);
                ConsulOptions wrapped = wrapOptions(syntheticAnnotation);
                packageConsulOptions.put(packageInfo.getName(), wrapped);
                namedConsulOptions.put(wrapped.value(), wrapped);
                log.debug("📋 Found @ConsulOptions on package '{}' (name='{}')",
                        packageInfo.getName(), wrapped.value());
                namedConsulOptions.put(wrapped.value(), wrapped);
                log.debug("📋 Found @ConsulOptions on package '{}' (name='{}')",
                        packageInfo.getName(), wrapped.value());
            }
        }
    }

    /**
     * Builds a ConsulOptions from a snapshot map of annotation parameter values.
     */
    private static ConsulOptions buildFromSnapshot(Map<String, Object> snapshot)
    {
        return new ConsulOptions()
        {
            @Override public Class<? extends Annotation> annotationType() { return ConsulOptions.class; }
            @Override public String value() { return snapStr(snapshot, "value", "default"); }
            @Override public String host() { return snapStr(snapshot, "host", "localhost"); }
            @Override public int port() { return snapInt(snapshot, "port", 8500); }
            @Override public boolean ssl() { return snapBool(snapshot, "ssl", false); }
            @Override public String token() { return snapStr(snapshot, "token", ""); }
            @Override public String datacenter() { return snapStr(snapshot, "datacenter", ""); }
            @Override public String configPrefix() { return snapStr(snapshot, "configPrefix", ""); }
            @Override public boolean registerService() { return snapBool(snapshot, "registerService", false); }
            @Override public String serviceName() { return snapStr(snapshot, "serviceName", ""); }
            @Override public String serviceId() { return snapStr(snapshot, "serviceId", ""); }
            @Override public String serviceAddress() { return snapStr(snapshot, "serviceAddress", ""); }
            @Override public int servicePort() { return snapInt(snapshot, "servicePort", 0); }
            @Override public String healthPath() { return snapStr(snapshot, "healthPath", "/health/ready"); }
            @Override public String healthInterval() { return snapStr(snapshot, "healthInterval", "10s"); }
            @Override public String deregisterAfter() { return snapStr(snapshot, "deregisterAfter", "1m"); }
        };
    }

    private static String snapStr(Map<String, Object> m, String key, String def)
    {
        Object val = m.get(key);
        return val != null ? val.toString() : def;
    }

    private static int snapInt(Map<String, Object> m, String key, int def)
    {
        Object val = m.get(key);
        if (val instanceof Number n) return n.intValue();
        if (val != null) try { return Integer.parseInt(val.toString()); } catch (NumberFormatException ignored) {}
        return def;
    }

    private static boolean snapBool(Map<String, Object> m, String key, boolean def)
    {
        Object val = m.get(key);
        if (val instanceof Boolean b) return b;
        if (val != null) return Boolean.parseBoolean(val.toString());
        return def;
    }

    private void scanClassAnnotations(ScanResult scanResult)
    {
        var classesWithAnnotation = scanResult.getClassesWithAnnotation(ConsulOptions.class);
        for (ClassInfo classInfo : classesWithAnnotation)
        {
            if (classInfo.getName().endsWith(".package-info")) continue;
            try
            {
                Class<?> clazz = classInfo.loadClass();
                ConsulOptions annotation = clazz.getAnnotation(ConsulOptions.class);
                if (annotation != null)
                {
                    ConsulOptions wrapped = wrapOptions(annotation);
                    packageConsulOptions.put(classInfo.getPackageName(), wrapped);
                    namedConsulOptions.put(wrapped.value(), wrapped);
                    log.debug("📋 Found @ConsulOptions on class '{}' (name='{}')",
                            classInfo.getName(), wrapped.value());
                }
            }
            catch (Exception e)
            {
                log.error("Error processing @ConsulOptions on class {}", classInfo.getName(), e);
            }
        }
    }

    static ConsulOptions wrapOptions(ConsulOptions source)
    {
        // Eagerly resolve all values to concrete strings
        final String name = source.value();
        final String host = source.host();
        final int port = source.port();
        final boolean ssl = source.ssl();
        final String token = source.token();
        final String datacenter = source.datacenter();
        final String configPrefix = source.configPrefix();
        final boolean registerService = source.registerService();
        final String serviceName = source.serviceName();
        final String serviceId = source.serviceId();
        final String serviceAddress = source.serviceAddress();
        final int servicePort = source.servicePort();
        final String healthPath = source.healthPath();
        final String healthInterval = source.healthInterval();
        final String deregisterAfter = source.deregisterAfter();

        return new WrappedConsulOptions(name, host, port, ssl, token, datacenter, configPrefix,
                registerService, serviceName, serviceId, serviceAddress, servicePort,
                healthPath, healthInterval, deregisterAfter);
    }

    /**
     * Resolves an environment variable scoped by Consul configuration name.
     * <p>
     * Lookup order:
     * <ol>
     *   <li>{@code CONSUL_{NORMALIZED_NAME}_{PROPERTY}}</li>
     *   <li>{@code CONSUL_{PROPERTY}}</li>
     *   <li>annotation default</li>
     * </ol>
     */
    public static String envForName(String name, String property, String defaultValue)
    {
        String normalizedName = name.toUpperCase().replace('-', '_').replace('.', '_');
        // Try name-scoped: CONSUL_{NAME}_{PROPERTY}
        String scopedKey = "CONSUL_" + normalizedName + "_" + property;
        String scopedValue = System.getProperty(scopedKey);
        if (scopedValue == null) scopedValue = System.getenv(scopedKey);
        if (scopedValue == null) scopedValue = System.getenv(scopedKey.toUpperCase());
        if (scopedValue != null && !scopedValue.isBlank())
        {
            return scopedValue;
        }
        // Try global: CONSUL_{PROPERTY}
        String globalKey = "CONSUL_" + property;
        String globalValue = System.getProperty(globalKey);
        if (globalValue == null) globalValue = System.getenv(globalKey);
        if (globalValue == null) globalValue = System.getenv(globalKey.toUpperCase());
        if (globalValue != null && !globalValue.isBlank())
        {
            return globalValue;
        }
        return defaultValue;
    }

    @Override
    public Integer sortOrder()
    {
        return Integer.MIN_VALUE + 85;
    }
}
















