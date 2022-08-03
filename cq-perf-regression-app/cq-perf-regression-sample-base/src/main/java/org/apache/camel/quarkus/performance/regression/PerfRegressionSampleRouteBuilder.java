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
        from("platform-http:/hello")
        .process(new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {

                if (atlasContextFactory == null) {
                    atlasContextFactory = DefaultAtlasContextFactory.getInstance();
                    atlasContextFactory.addClassLoader(getCamelContext().getApplicationContextClassLoader());
                }

                if (atlasContext == null) {
                    try (InputStream is = ResourceHelper.resolveMandatoryResourceAsInputStream(getCamelContext(), "request.adm")) {
                        atlasContext = atlasContextFactory.createContext(ADM, is);
                    }
                }

                AtlasSession atlasSession = atlasContext.createSession();
                populateSourceDocuments(exchange, atlasSession);
                atlasSession.getAtlasContext().process(atlasSession);
            }
        
            private void populateSourceDocuments(Exchange exchange, AtlasSession session) {
                if (session.getMapping().getDataSource() == null) {
                    return;
                }

                Message inMessage = exchange.getIn();
                CamelAtlasPropertyStrategy propertyStrategy = new CamelAtlasPropertyStrategy();
                propertyStrategy.setCurrentSourceMessage(inMessage);
                propertyStrategy.setTargetMessage(exchange.getMessage());
                propertyStrategy.setExchange(exchange);
                session.setAtlasPropertyStrategy(propertyStrategy);

                DataSource[] sourceDataSources = session.getMapping().getDataSource().stream().filter(ds -> ds.getDataSourceType() == DataSourceType.SOURCE).toArray(DataSource[]::new);
                if (sourceDataSources.length == 0) {
                    session.setDefaultSourceDocument(inMessage.getBody());
                    return;
                }

                if (sourceDataSources.length == 1) {
                    String docId = sourceDataSources[0].getId();
                    Object payload = extractPayload(sourceDataSources[0], inMessage);
                    if (docId == null || docId.isEmpty()) {
                        session.setDefaultSourceDocument(payload);
                    } else {
                        session.setSourceDocument(docId, payload);
                        propertyStrategy.setSourceMessage(docId, inMessage);
                    }
                    return;
                }

                Map<String, Message> sourceMessages = null;
                Map<String, Object> sourceDocuments = null;
                /*if (sourceMapName != null) {
                    sourceMessages = exchange.getProperty(sourceMapName, Map.class);
                }*/
                if (sourceMessages == null) {
                    Object body = inMessage.getBody();
                    if (body instanceof Map) {
                        sourceDocuments = (Map<String, Object>)body;
                    } else {
                        session.setDefaultSourceDocument(body);
                    }
                }
                for (DataSource ds : sourceDataSources) {
                    String docId = ds.getId();
                    if (docId == null || docId.isEmpty()) {
                        Object payload = extractPayload(ds, inMessage);
                        session.setDefaultSourceDocument(payload);
                    } else if (sourceMessages != null) {
                        Object payload = extractPayload(ds, sourceMessages.get(docId));
                        session.setSourceDocument(docId, payload);
                        propertyStrategy.setSourceMessage(docId, sourceMessages.get(docId));
                    } else if (sourceDocuments != null) {
                        Object payload = sourceDocuments.get(docId);
                        session.setSourceDocument(docId, payload);
                    } else if (inMessage.getHeaders().containsKey(docId)) {
                        Object payload = inMessage.getHeader(docId);
                        session.setSourceDocument(docId, payload);
                    } else if (exchange.getProperties().containsKey(docId)) {
                        Object payload = exchange.getProperty(docId);
                        session.setSourceDocument(docId, payload);
                    } else {
                        LOG.warn("Ignoring missing source document: '{}(ID:{})'", ds.getName(), ds.getId());
                    }
                }
            }

            private Object extractPayload(final DataSource dataSource, Message message) {
                if (dataSource == null || message == null) {
                    return null;
                }
                Object body = null;

                if (dataSource.getUri() != null && !(dataSource.getUri().startsWith("atlas:core") || dataSource.getUri().startsWith("atlas:java"))) {
                    body = message.getBody(String.class);
                } else {
                    body = message.getBody();
                }

                // Just in case, prepare for future calls
                MessageHelper.resetStreamCache(message);

                return body;
            }
        
        });
    }

}
