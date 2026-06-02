package com.guicedee.consul;

import java.lang.annotation.Annotation;

/**
 * Concrete implementation of {@link ConsulOptions} with env-override resolution at access time.
 * Uses a named class instead of anonymous inner class to avoid JVM closure capture issues in JPMS.
 */
public class WrappedConsulOptions implements ConsulOptions
{
    private final String name;
    private final String host;
    private final int port;
    private final boolean ssl;
    private final String token;
    private final String datacenter;
    private final String configPrefix;
    private final boolean registerService;
    private final String serviceName;
    private final String serviceId;
    private final String serviceAddress;
    private final int servicePort;
    private final String healthPath;
    private final String healthInterval;
    private final String deregisterAfter;

    public WrappedConsulOptions(String name, String host, int port, boolean ssl, String token,
                                String datacenter, String configPrefix, boolean registerService,
                                String serviceName, String serviceId, String serviceAddress,
                                int servicePort, String healthPath, String healthInterval, String deregisterAfter)
    {
        this.name = name;
        this.host = host;
        this.port = port;
        this.ssl = ssl;
        this.token = token;
        this.datacenter = datacenter;
        this.configPrefix = configPrefix;
        this.registerService = registerService;
        this.serviceName = serviceName;
        this.serviceId = serviceId;
        this.serviceAddress = serviceAddress;
        this.servicePort = servicePort;
        this.healthPath = healthPath;
        this.healthInterval = healthInterval;
        this.deregisterAfter = deregisterAfter;
    }

    @Override public Class<? extends Annotation> annotationType() { return ConsulOptions.class; }
    @Override public String value() {
        // Only check name-scoped override, never global fallback (avoids first-default-wins pollution)
        String normalizedName = name.toUpperCase().replace('-', '_').replace('.', '_');
        String scopedKey = "CONSUL_" + normalizedName + "_NAME";
        String scopedValue = com.guicedee.client.Environment.getSystemPropertyOrEnvironment(scopedKey, null);
        if (scopedValue != null && !scopedValue.isBlank()) return scopedValue;
        return name;
    }
    @Override public String host() { return ConsulPreStartup.envForName(name, "HOST", host); }
    @Override public int port() { return Integer.parseInt(ConsulPreStartup.envForName(name, "PORT", String.valueOf(port))); }
    @Override public boolean ssl() { return Boolean.parseBoolean(ConsulPreStartup.envForName(name, "SSL", String.valueOf(ssl))); }
    @Override public String token() { return ConsulPreStartup.envForName(name, "TOKEN", token); }
    @Override public String datacenter() { return ConsulPreStartup.envForName(name, "DATACENTER", datacenter); }
    @Override public String configPrefix() { return ConsulPreStartup.envForName(name, "CONFIG_PREFIX", configPrefix); }
    @Override public boolean registerService() { return Boolean.parseBoolean(ConsulPreStartup.envForName(name, "REGISTER_SERVICE", String.valueOf(registerService))); }
    @Override public String serviceName() { return ConsulPreStartup.envForName(name, "SERVICE_NAME", serviceName); }
    @Override public String serviceId() { return ConsulPreStartup.envForName(name, "SERVICE_ID", serviceId); }
    @Override public String serviceAddress() { return ConsulPreStartup.envForName(name, "SERVICE_ADDRESS", serviceAddress); }
    @Override public int servicePort() { return Integer.parseInt(ConsulPreStartup.envForName(name, "SERVICE_PORT", String.valueOf(servicePort))); }
    @Override public String healthPath() { return ConsulPreStartup.envForName(name, "HEALTH_PATH", healthPath); }
    @Override public String healthInterval() { return ConsulPreStartup.envForName(name, "HEALTH_INTERVAL", healthInterval); }
    @Override public String deregisterAfter() { return ConsulPreStartup.envForName(name, "DEREGISTER_AFTER", deregisterAfter); }
}


