import com.guicedee.client.services.lifecycle.IGuiceModule;
import com.guicedee.client.services.lifecycle.IGuicePostStartup;
import com.guicedee.client.services.lifecycle.IGuicePreStartup;
import com.guicedee.consul.ConsulModule;
import com.guicedee.consul.ConsulPostStartup;
import com.guicedee.consul.ConsulPreStartup;

module com.guicedee.consul {

    exports com.guicedee.consul;

    requires transitive com.guicedee.vertx;
    requires transitive io.vertx.core;
    requires transitive io.vertx.consul.client;
    requires com.google.guice;
    requires io.github.classgraph;
    requires static lombok;

    provides IGuicePreStartup with ConsulPreStartup;
    provides IGuicePostStartup with ConsulPostStartup;
    provides IGuiceModule with ConsulModule;

    opens com.guicedee.consul to com.google.guice, io.github.classgraph;
}

