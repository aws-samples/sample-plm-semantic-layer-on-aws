// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.validation;

import atelier.query.Atelier;
import org.apache.jena.graph.Node;

/**
 * One SHACL result: the rule name from the shape's ateliersh:rule, the source shape IRI, the severity
 * (local name of sh:resultSeverity: {@code Violation} for an interface rule or a product rule, {@code Warning} for a
 * data-quality shape), the message, the focus node IRI (a feature; a part for a data-quality shape; a product for a
 * product rule), sh:value (the mated feature, the interface, the quantity value node, for a reference rule the external
 * reference or, for massScale, a part of the product, depending on the rule; null when the result carries none) and the
 * sh:resultPath IRI (the quantity property, or atelier:cadFile) when present.
 */
public record RuleViolation(String rule, String shape, String severity, String message, String focusNode, Node value,
                            String path) {
    /** Whether the result bears on an interface's status: only a sh:Violation does; a sh:Warning is a finding. */
    public boolean failsInterface() {
        return "Violation".equals(severity);
    }

    /** What the result is a finding on: the part a product rule names as its sh:value, otherwise the focus node. */
    public String subject() {
        String part = value != null && value.isURI() ? value.getURI() : null;
        boolean onPart = part != null && Atelier.plmOf(part) != null && part.startsWith(Atelier.DATA + Atelier.plmOf(part) + "/part/");
        return focusNode != null && focusNode.startsWith(Atelier.PRODUCT) && onPart ? part : focusNode;
    }
}
