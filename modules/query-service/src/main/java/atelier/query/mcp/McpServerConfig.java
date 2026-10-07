// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.mcp;

import atelier.query.api.QueryController;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.WebMvcStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * The MCP server at {@code /query/mcp} (streamable HTTP; API Gateway maps {@code /api/query/mcp} to
 * it): the SDK's Spring WebMVC transport mounted as a router function, and a synchronous server
 * offering the {@link AtelierTools}. Streamable HTTP carries headers on every POST; the transport's
 * context extractor copies {@code x-atelier-profile} and {@code x-atelier-actor} from each request into
 * that request's transport context, which is what the tools read, so the profile applies per call
 * exactly as on the screens and the actor is echoed per call. {@link McpToolsController} lists the
 * same server identity and tools over plain REST at {@code /query/mcp/tools}.
 */
@Configuration
public class McpServerConfig {
    public static final String ENDPOINT = "/query/mcp";
    public static final String SERVER_NAME = "atelier-semantic-layer";
    public static final String SERVER_VERSION = "0.1.0";

    @Bean
    public WebMvcStreamableServerTransportProvider mcpTransport() {
        return WebMvcStreamableServerTransportProvider.builder()
                .jsonMapper(new JacksonMcpJsonMapper(new ObjectMapper()))
                .mcpEndpoint(ENDPOINT)
                .contextExtractor(request -> {
                    Map<String, Object> headers = new LinkedHashMap<>();
                    for (String name : List.of(QueryController.PROFILE_HEADER, QueryController.ACTOR_HEADER)) {
                        String value = request.headers().firstHeader(name);
                        if (value != null) headers.put(name, value);
                    }
                    return McpTransportContext.create(headers);
                })
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> mcpRoutes(WebMvcStreamableServerTransportProvider transport) {
        return transport.getRouterFunction();
    }

    @Bean(destroyMethod = "closeGracefully")
    public McpSyncServer mcpServer(WebMvcStreamableServerTransportProvider transport, AtelierTools tools) {
        return McpServer.sync(transport)
                .serverInfo(SERVER_NAME, SERVER_VERSION)
                .capabilities(McpSchema.ServerCapabilities.builder().tools(false).build())
                .tools(tools.specifications())
                .build();
    }
}
