// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.evidence;

/** Ontop's reformulation of one request sent to an Ontop source (a PLM or the Atelier core graph) into that source's native SQL. */
public interface OntopSql {
    /**
     * The SQL Ontop generates for {@code sparql} against the source's virtual graph, {@code "EMPTY"}
     * when the source maps none of the queried concepts, or null when not obtainable.
     */
    String reformulate(String source, String sparql);
}
