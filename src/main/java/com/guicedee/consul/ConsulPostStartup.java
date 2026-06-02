package com.guicedee.consul;

import com.guicedee.client.services.lifecycle.IGuicePostStartup;
import com.guicedee.vertx.spi.VertXPreStartup;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.consul.*;
import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.List;

/**
 * Post-startup hook that registers services with Consul and attaches health checks
 * based on discovered {@link ConsulOptions} configurations.
 */
@Log4j2
public class ConsulPostStartup implements IGuicePostStartup<ConsulPostStartup>
{
    @Override
    public List<Uni<Boolean>> postLoad()
    {
        return List.of(Uni.createFrom().item(() -> {
            var options = ConsulPreStartup.getNamedConsulOptions();
            for (var entry : options.entrySet())
            {
                String name = entry.getKey();
                ConsulOptions opts = entry.getValue();

                if (opts.registerService() && !opts.serviceName().isEmpty())
                {
                    registerService(name, opts);
                }
            }
            return true;
        }));
    }

    private void registerService(String name, ConsulOptions opts)
    {
        log.info("🚀 Registering service '{}' with Consul (name='{}')", opts.serviceName(), name);

        ConsulClientOptions clientOptions = new ConsulClientOptions()
                .setHost(opts.host())
                .setPort(opts.port())
                .setSsl(opts.ssl());
        if (!opts.token().isEmpty()) clientOptions.setAclToken(opts.token());
        if (!opts.datacenter().isEmpty()) clientOptions.setDc(opts.datacenter());

        ConsulClient client = ConsulClient.create(VertXPreStartup.getVertx(), clientOptions);

        ServiceOptions serviceOptions = new ServiceOptions()
                .setName(opts.serviceName())
                .setPort(opts.servicePort());

        if (!opts.serviceId().isEmpty())
        {
            serviceOptions.setId(opts.serviceId());
        }
        if (!opts.serviceAddress().isEmpty())
        {
            serviceOptions.setAddress(opts.serviceAddress());
        }

        // Register health check
        if (!opts.healthPath().isEmpty() && opts.servicePort() > 0)
        {
            String checkUrl = "http://" + (opts.serviceAddress().isEmpty() ? "localhost" : opts.serviceAddress())
                    + ":" + opts.servicePort() + opts.healthPath();
            CheckOptions check = new CheckOptions()
                    .setHttp(checkUrl)
                    .setInterval(opts.healthInterval())
                    .setDeregisterAfter(opts.deregisterAfter());
            serviceOptions.setCheckOptions(check);
        }

        client.registerService(serviceOptions)
                .onSuccess(v -> log.info("✅ Service '{}' registered with Consul", opts.serviceName()))
                .onFailure(err -> log.warn("⚠️ Failed to register service '{}' with Consul: {}",
                        opts.serviceName(), err.getMessage()));

        // Register shutdown hook to deregister
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            String serviceId = opts.serviceId().isEmpty() ? opts.serviceName() : opts.serviceId();
            client.deregisterService(serviceId)
                    .onSuccess(v -> log.info("👋 Service '{}' deregistered from Consul", serviceId))
                    .onFailure(err -> log.debug("Failed to deregister: {}", err.getMessage()));
        }));
    }

    @Override
    public Integer sortOrder()
    {
        return Integer.MIN_VALUE + 700;
    }
}

