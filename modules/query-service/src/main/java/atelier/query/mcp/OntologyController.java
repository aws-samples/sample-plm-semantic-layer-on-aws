// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.mcp;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /query/ontology}: the answer of the {@code ontology} tool over plain REST, no MCP
 * session and no profile header needed, from the same {@link OntologyCatalogue} the tool reads, so
 * the two never disagree.
 */
@RestController
public class OntologyController {
    private final AtelierTools tools;

    public OntologyController(AtelierTools tools) {
        this.tools = tools;
    }

    @GetMapping("/query/ontology")
    public OntologyCatalogue.Description ontology() {
        return tools.ontology().describe();
    }
}
