package com.guicedee.consul;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares Consul connection and service registration configuration for a package or class.
 * <p>
 * Place on {@code package-info.java} to define a Consul connection for all classes in that
 * package subtree. All values support environment variable override using the pattern:
 * {@code CONSUL_{NORMALIZED_NAME}_{PROPERTY}} or {@code CONSUL_{PROPERTY}} as global fallback.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PACKAGE, ElementType.TYPE})
public @interface ConsulOptions
{
    /**
     * @return Logical name for this Consul configuration (used in env var lookups and @Named bindings).
     */
    String value() default "default";

    /**
     * @return Consul agent host.
     */
    String host() default "localhost";

    /**
     * @return Consul agent HTTP port.
     */
    int port() default 8500;

    /**
     * @return Whether to use SSL for Consul connection.
     */
    boolean ssl() default false;

    /**
     * @return Consul ACL token for authentication.
     */
    String token() default "";

    /**
     * @return Consul datacenter to query.
     */
    String datacenter() default "";

    /**
     * @return KV prefix for configuration lookups.
     */
    String configPrefix() default "";

    /**
     * @return Whether to register this application as a Consul service.
     */
    boolean registerService() default false;

    /**
     * @return Service name for registration.
     */
    String serviceName() default "";

    /**
     * @return Unique service instance ID.
     */
    String serviceId() default "";

    /**
     * @return Service address (defaults to host address if empty).
     */
    String serviceAddress() default "";

    /**
     * @return Service port for registration.
     */
    int servicePort() default 0;

    /**
     * @return Health check HTTP path.
     */
    String healthPath() default "/health/ready";

    /**
     * @return Health check interval (e.g. "10s", "30s").
     */
    String healthInterval() default "10s";

    /**
     * @return Time after which a critical service is deregistered (e.g. "1m", "5m").
     */
    String deregisterAfter() default "1m";
}

