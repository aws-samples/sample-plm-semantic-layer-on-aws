// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.mcp;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /query/mcp/tools}: the MCP server's identity and registered tools over plain REST, no
 * MCP session needed. The tools are the ones {@link AtelierTools} hands the MCP server, so this listing
 * and {@code tools/list} never disagree; the Architecture tab reads it for the tool interface's
 * health and count.
 */
@RestController
public class McpToolsController {
    private final AtelierTools tools;

    public McpToolsController(AtelierTools tools) {
        this.tools = tools;
    }

    @GetMapping(McpServerConfig.ENDPOINT + "/tools")
    public Listing tools() {
        List<Entry> entries = tools.tools().stream().map(tool -> new Entry(tool.name(), tool.description())).toList();
        return new Listing(new Server(McpServerConfig.SERVER_NAME, McpServerConfig.SERVER_VERSION), entries, entries.size());
    }

    public record Server(String name, String version) {}

    public record Entry(String name, String description) {}

    public record Listing(Server server, List<Entry> tools, int count) {}
}
