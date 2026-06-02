package com.guicedee.consul;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import com.guicedee.client.services.lifecycle.IGuiceModule;
import io.vertx.ext.consul.ConsulClient;
import io.vertx.ext.consul.ConsulClientOptions;
import com.guicedee.vertx.spi.VertXPreStartup;
import lombok.extern.log4j.Log4j2;

/**
 * Guice module that binds {@link ConsulClient} instances by name based on discovered
 * {@link ConsulOptions} configurations.
 */
@Log4j2
public class ConsulModule extends AbstractModule implements IGuiceModule<ConsulModule>
{
    @Override
    protected void configure()
    {
        var options = ConsulPreStartup.getNamedConsulOptions();
        for (var entry : options.entrySet())
        {
            String name = entry.getKey();
            ConsulOptions opts = entry.getValue();

            bind(ConsulClient.class)
                    .annotatedWith(Names.named(name))
                    .toProvider(() -> createClient(opts));
            log.debug("📋 Bound ConsulClient @Named(\"{}\")", name);
        }

        // Bind default (unnamed) if there's a "default" or exactly one
        if (options.containsKey("default"))
        {
            bind(ConsulClient.class)
                    .toProvider(() -> createClient(options.get("default")));
        }
        else if (options.size() == 1)
        {
            ConsulOptions only = options.values().iterator().next();
            bind(ConsulClient.class)
                    .toProvider(() -> createClient(only));
        }
    }

    private static ConsulClient createClient(ConsulOptions opts)
    {
        ConsulClientOptions clientOptions = new ConsulClientOptions()
                .setHost(opts.host())
                .setPort(opts.port())
                .setSsl(opts.ssl());

        if (!opts.token().isEmpty())
        {
            clientOptions.setAclToken(opts.token());
        }
        if (!opts.datacenter().isEmpty())
        {
            clientOptions.setDc(opts.datacenter());
        }

        return ConsulClient.create(VertXPreStartup.getVertx(), clientOptions);
    }
}

