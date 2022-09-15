package org.apache.camel.quarkus.performance.regression;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.atlasmap.CamelAtlasPropertyStrategy;
import org.apache.camel.support.MessageHelper;
import org.apache.camel.support.ResourceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.atlasmap.api.AtlasContextFactory.Format.ADM;

import io.atlasmap.api.AtlasContext;
import io.atlasmap.api.AtlasContextFactory;
import io.atlasmap.api.AtlasSession;
import io.atlasmap.core.DefaultAtlasContextFactory;
import io.atlasmap.v2.DataSource;
import io.atlasmap.v2.DataSourceType;

public class PerfRegressionSampleRouteBuilder extends RouteBuilder {

    private AtlasContextFactory atlasContextFactory = null;
    private AtlasContext atlasContext;

    private static final Logger LOG = LoggerFactory.getLogger(PerfRegressionSampleRouteBuilder.class);

    @Override
    public void configure() throws Exception {
        from("platform-http:/hello").to("atlasmap:request.adm");
    }
}
