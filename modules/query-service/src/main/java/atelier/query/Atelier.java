// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query;

import java.math.BigInteger;
import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** IRIs of the integration contract and helpers to read native keys back out of them. */
public final class Atelier {
    public static final String ONT = "https://example.com/atelier/ontology#";
    public static final String DATA = "https://example.com/atelier/";
    /** Interfaces are minted per product: {@code interface/{productKey}/{id}}, the id being unique within its product. */
    public static final String INTERFACE = DATA + "interface/";
    /** Products are Atelier's own subjects, minted by the core graph: no PLM segment. */
    public static final String PRODUCT = DATA + "product/";
    public static final String LINKS_GRAPH = DATA + "graph/links";
    public static final String FILE_INDEX_GRAPH = DATA + "graph/fileindex";
    public static final String LABELS_GRAPH = DATA + "graph/labels";
    /** The variant groups of the products, their options and what exists only under an option. */
    public static final String OPTIONS_GRAPH = DATA + "graph/options";
    /** Variant groups are minted per product, {@code variant/{productKey}/{group}}; options by code, {@code option/{code}}. */
    public static final String VARIANT = DATA + "variant/";
    public static final String OPTION = DATA + "option/";
    /** What exists only when an option is taken: a site's row with the option code, or a link-store fact of the options graph. */
    public static final String APPLIES_UNDER_OPTION = ONT + "appliesUnderOption";
    /** The connection point of its host part a feature serves, the same under every option. */
    public static final String PORT = ONT + "port";
    /** Predicates of the options graph; a hidden item keeps them, as it keeps its link-store facts. */
    public static final List<String> OPTION_PREDICATES = List.of(APPLIES_UNDER_OPTION, ONT + "optionOf", ONT + "defaultOption",
            ONT + "applicability", ONT + "portNotModelled");
    public static final String QUDT = "http://qudt.org/schema/qudt/";
    public static final String UNIT = "http://qudt.org/vocab/unit/";
    public static final String SHAPES = "https://example.com/atelier/shapes#";
    public static final String SKOS = "http://www.w3.org/2004/02/skos/core#";

    public static final List<String> PLMS = List.of("fr", "de", "uk", "es");

    /** Feature kinds as reported in JSON, keyed by the IRI path segment that carries them. */
    public static final List<String> KINDS = List.of("plug", "fastener", "coupling");

    /** Ontology class of each feature kind. */
    public static final Map<String, String> FEATURE_CLASSES = Map.of(
            "plug", ONT + "Plug", "fastener", ONT + "Fastener", "coupling", ONT + "HydraulicCoupling");

    /** The product an interface belongs to, from the links graph. */
    public static final String OF_PRODUCT = ONT + "ofProduct";

    /** Predicates whose triples come from the link store's links graph; kept on redacted subjects. */
    public static final List<String> LINK_PREDICATES = List.of(ONT + "betweenPart", ONT + "declaresFeature",
            ONT + "declaresPlug", ONT + "matesWith", ONT + "toleranceMm", OF_PRODUCT);

    /**
     * Functional edges are the layer's own, minted per product in the link store: {@code drive/{productKey}/{n}}, each an
     * atelier:Drive from its driver to its driven part.
     */
    public static final String DRIVE = DATA + "drive/";

    /** The predicates of the link store's functional edges and rated speeds, read by the path and flow answers. */
    public static final List<String> FUNCTION_PREDICATES = List.of(ONT + "driver", ONT + "driven", ONT + "flow",
            ONT + "viaInterface", ONT + "reactionPart", ONT + "drives", ONT + "ratedSpeedRpm");

    /** Stations and sections are the layer's own, minted per product in the link store: {@code station|section/{product}/{id}}. */
    public static final String STATION = DATA + "station/";
    public static final String SECTION = DATA + "section/";

    /**
     * The predicates of the link store's stations and sections and of the product's station axis, read by the stations and
     * sections answers; the stations' notation, source and quantity values are stated under the station and section IRIs.
     */
    public static final List<String> STATION_PREDICATES = List.of(ONT + "stationAxis", ONT + "stationsSymmetric",
            ONT + "stationMeasure", ONT + "sectionOf", ONT + "stationPosition", ONT + "stationTolerance", ONT + "stationBasis",
            ONT + "hasStation", ONT + "sectionSide", ONT + "sectionPart");

    /** Where a part's CAD file is, from the file-index graph. */
    public static final String CAD_FILE = ONT + "cadFile";

    /** The supplier that built a part, from the file-index graph; a part the PLM's own plant builds carries none. */
    public static final String BUILT_BY = ONT + "builtBy";

    /** The predicates of the file-index graph. */
    public static final List<String> FILE_INDEX_PREDICATES = List.of(CAD_FILE, BUILT_BY);

    /** An item's native name in its site's language and its English name, from the labels graph. */
    public static final String PREF_LABEL = SKOS + "prefLabel";
    public static final String ALT_LABEL = SKOS + "altLabel";

    /** A user's confirmation, in the links graph, that two parts of different sites are one item. */
    public static final String SAME_AS = "http://www.w3.org/2002/07/owl#sameAs";

    /** What the layer states about an item beyond the interfaces and the file index: its labels and its confirmed equivalents. */
    public static final List<String> LAYER_PREDICATES = List.of(PREF_LABEL, ALT_LABEL, SAME_AS);

    /** The concept scheme of the products' glossary, published in the labels graph. */
    public static final String TERMS = ONT + "Terms";

    /** Predicates of a part's export-control tag, from the Atelier core graph. */
    public static final List<String> TAG_PREDICATES = List.of(ONT + "jurisdiction", ONT + "releasableTo",
            ONT + "taggedBy", ONT + "taggedAt");

    /** The product a part belongs to, from the Atelier core graph. */
    public static final String PART_OF = ONT + "partOf";

    /** Every predicate the Atelier core graph states about a part: its tag and its product membership. */
    public static final List<String> CORE_PREDICATES = List.of(ONT + "jurisdiction", ONT + "releasableTo",
            ONT + "taggedBy", ONT + "taggedAt", PART_OF);

    /** The part's mass, a qudt:QuantityValue minted under the part IRI ({@code <part IRI>/mass}). */
    public static final String MASS = ONT + "mass";

    /**
     * Predicates a PLM states about its own parts: the description and the part attributes. They come
     * from the Ontop endpoint of the PLM the part IRI names, as do the quantity values minted under it.
     */
    public static final List<String> PART_PREDICATES = List.of(ONT + "identifier", ONT + "label", ONT + "sourceFileRef",
            ONT + "revision", ONT + "lifecycleLabel", MASS, ONT + "material", ONT + "partType", ONT + "ownedBy",
            ONT + "standard", ONT + "nominalDiameter", ONT + "nominalLength", ONT + "toothCount", ONT + "gearModule",
            ONT + "itemClass", ONT + "innerDiameter", ONT + "crossSection", ONT + "compound", ONT + "legend", ONT + "itemWidth",
            ONT + "itemHeight", ONT + "facestock", ONT + "adhesive", ONT + "baseWidth", ONT + "itemDepth", ONT + "contourWidth",
            ONT + "shellMaterial", ONT + "outerDiameter", ONT + "sectionWidth", ONT + "rimDiameter", ONT + "plyRating",
            ONT + "heatStackDiameter", ONT + "rotorCount", ONT + "shelfLifeMonths");

    /** The concept scheme of canonical lifecycle states; each PLM's own word is a skos:altLabel of a concept. */
    public static final String LIFECYCLE = ONT + "Lifecycle";

    public static final List<String> AXES = List.of("x", "y", "z");

    /** A supplier's offer for a part, minted per PLM as {@code {plm}/offer/{id}}; its supplier is {@code {plm}/supplier/{key}}. */
    public static final String SUPPLIER_OFFER = ONT + "SupplierOffer";

    /** The concept scheme of item classes; each class names the attributes that identify an item of the class. */
    public static final String ITEM_CLASSES = ONT + "ItemClasses";

    /** A bill-of-materials line, minted per PLM as {@code {plm}/bomline/{parent}/{child}}. */
    public static final String BOM_LINE = ONT + "BomLine";

    /** One use of a line's child with its placement, minted per PLM as {@code {plm}/occurrence/{parent}/{child}/{index}}. */
    public static final String OCCURRENCE = ONT + "Occurrence";

    /** A part's use of another site's part, minted per PLM as {@code {plm}/ref/{id}}; the target is named by URN only. */
    public static final String EXTERNAL_REFERENCE = ONT + "ExternalReference";
    public static final String FROM_PART = ONT + "fromPart";
    public static final String REMOTE_URN = ONT + "remoteUrn";
    public static final String EXPECTED_REVISION = ONT + "expectedRevision";

    private static final Pattern RESOURCE =
            Pattern.compile("^" + Pattern.quote(DATA) + "([a-z]+)/(part|plug|fastener|coupling|ref|supplier|offer|bomline/[^/]+)/([^/]+)$");

    /** Product keys are placed into IRIs and SPARQL text, so only plain key characters are accepted. */
    private static final Pattern PRODUCT_KEY = Pattern.compile("[A-Za-z0-9._-]{1,32}");

    private Atelier() {}

    /** IRI of the interface {@code id} of the product {@code productKey}; a key outside the key alphabet is refused. */
    public static String interfaceIri(String productKey, String id) {
        if (!PRODUCT_KEY.matcher(productKey).matches()) {
            throw new IllegalArgumentException("invalid product key");
        }
        return INTERFACE + productKey + "/" + id;
    }

    /** Variant group and option keys are placed into IRIs and SPARQL text, so only plain key characters are accepted. */
    private static final Pattern OPTION_KEY = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    /** True for a variant group key or option code of the key alphabet. */
    public static boolean isKey(String key) {
        return key != null && OPTION_KEY.matcher(key).matches();
    }

    /** IRI of the variant group {@code group} of the product {@code productKey}; a key outside the key alphabet is refused. */
    public static String variantIri(String productKey, String group) {
        if (!PRODUCT_KEY.matcher(productKey).matches() || !isKey(group)) {
            throw new IllegalArgumentException("invalid product or variant group key");
        }
        return VARIANT + productKey + "/" + group;
    }

    /** Key of the product an interface IRI belongs to: the path segment before its id; null when the IRI has none. */
    public static String productOf(String interfaceIri) {
        String rest = interfaceIri.substring(INTERFACE.length());
        int slash = rest.lastIndexOf('/');
        return slash < 0 ? null : rest.substring(0, slash);
    }

    /** IRI of the product with the given key; a key outside the key alphabet is refused. */
    public static String productIri(String key) {
        if (!PRODUCT_KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("invalid product key");
        }
        return PRODUCT + key;
    }

    public static String positionProperty(String axis) {
        return ONT + "position" + axis.toUpperCase();
    }

    /**
     * IRI of the part {@code id} of the PLM, the id percent-encoded as an R2RML template encodes a column value: every
     * byte but the unreserved characters (letters, digits, {@code - . _ ~}).
     */
    public static String partIri(String plm, String id) {
        StringBuilder out = new StringBuilder(DATA).append(plm).append("/part/");
        for (byte b : id.getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
            char c = (char) (b & 0xff);
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || "-._~".indexOf(c) >= 0) {
                out.append(c);
            } else {
                out.append('%').append(String.format("%02X", b & 0xff));
            }
        }
        return out.toString();
    }

    /** Native key of a part, feature or interface IRI, percent-decoded. */
    public static String nativeId(String iri) {
        String last = iri.substring(iri.lastIndexOf('/') + 1);
        return URI.create("x:/" + last).getPath().substring(1);
    }

    /** Lower-case PLM code of a part, feature, external reference, bill-of-materials line, supplier or supplier offer IRI, or null for other IRIs. */
    public static String plmOf(String iri) {
        Matcher m = RESOURCE.matcher(iri);
        return m.matches() ? m.group(1) : null;
    }

    /** Feature kind ("plug", "fastener", "coupling") of a feature IRI, or null for other IRIs. */
    public static String kindOf(String iri) {
        Matcher m = RESOURCE.matcher(iri);
        return m.matches() && KINDS.contains(m.group(2)) ? m.group(2) : null;
    }

    private static final Pattern DIGITS_OR_NOT = Pattern.compile("\\d+|\\D+");

    /**
     * Native ids in reading order: runs of digits compare by their value, the rest as text, so IF-99 comes before
     * IF-100 and IF-9 before IF-10. Ids equal under that order (IF-7 and IF-07) compare as text.
     */
    public static final Comparator<String> BY_ID = (a, b) -> {
        List<String> x = DIGITS_OR_NOT.matcher(a).results().map(MatchResult::group).toList();
        List<String> y = DIGITS_OR_NOT.matcher(b).results().map(MatchResult::group).toList();
        for (int i = 0; i < Math.min(x.size(), y.size()); i++) {
            String p = x.get(i);
            String q = y.get(i);
            int c = Character.isDigit(p.charAt(0)) && Character.isDigit(q.charAt(0))
                    ? new BigInteger(p).compareTo(new BigInteger(q))
                    : p.compareTo(q);
            if (c != 0) return c;
        }
        int c = Integer.compare(x.size(), y.size());
        return c != 0 ? c : a.compareTo(b);
    };

    public static String localName(String iri) {
        return iri.substring(Math.max(iri.lastIndexOf('/'), iri.lastIndexOf('#')) + 1);
    }
}
