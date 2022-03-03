package org.apache.camel.quarkus.performance.regression;

import org.apache.camel.builder.RouteBuilder;

public class PerfRegressionSampleRouteBuilder extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        from("platform-http:/hello").to("atlasmap:request.adm");
    }

}
