@ConsulOptions(
        value = "test-consul",
        host = "127.0.0.1",
        port = 8500,
        token = "test-token-123",
        datacenter = "dc1",
        configPrefix = "guicedee/test/myapp",
        registerService = true,
        serviceName = "test-service",
        serviceId = "test-service-1",
        serviceAddress = "10.0.0.1",
        servicePort = 9090,
        healthPath = "/health/live",
        healthInterval = "15s",
        deregisterAfter = "2m"
)
package com.guicedee.consul.test.annotated;

import com.guicedee.consul.ConsulOptions;

