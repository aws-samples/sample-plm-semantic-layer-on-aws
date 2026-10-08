// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.core.graph;

/** IRIs of the integration contract the core service writes: the ontology namespace and Atelier's named graphs. */
public final class Graphs {

    public static final String ONT = "https://example.com/atelier/ontology#";
    public static final String DATA = "https://example.com/atelier/";
    public static final String MATES_WITH = ONT + "matesWith";
    public static final String LINKS = DATA + "graph/links";
    public static final String FILE_INDEX = DATA + "graph/fileindex";
    public static final String LABELS = DATA + "graph/labels";
    public static final String SAME_AS = "http://www.w3.org/2002/07/owl#sameAs";

    private Graphs() {
    }
}
