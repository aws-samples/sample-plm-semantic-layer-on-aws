// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.evidence;

/** Test double: no reformulator is wired, every arm reports {@code sql: null}. */
public class NoOntopSql implements OntopSql {
    @Override
    public String reformulate(String plm, String sparql) {
        return null;
    }
}
