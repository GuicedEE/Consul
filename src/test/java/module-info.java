open module com.guicedee.consul.test {
    requires transitive com.guicedee.consul;
    requires com.guicedee.guicedinjection;
    requires com.google.guice;
    requires io.vertx.core;
    requires io.vertx.consul.client;
    requires org.testcontainers;

    requires org.junit.jupiter;
}



