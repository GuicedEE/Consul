@ConsulOptions(
        value = "second-consul",
        host = "consul.cluster.local",
        port = 8501,
        ssl = true,
        datacenter = "dc2"
)
package com.guicedee.consul.test.second;

import com.guicedee.consul.ConsulOptions;

