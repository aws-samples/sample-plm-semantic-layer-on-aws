// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.common.annotation;

/** Value forms several sites share, for {@link Accepts#pattern()}. */
public final class Forms {

    /** A part URN as every site writes it: {@code urn:plm:<site>:part:<part number>}. */
    public static final String URN = "urn:plm:(de|fr|es|uk):part:.+";

    /**
     * A revision in any site's form, since an expected revision is written in the target site's:
     * two digits (DE), one capital letter (FR), an integer (ES), P or C and a number (UK).
     */
    public static final String ANY_REVISION = "\\d{1,2}|[A-Z]|[PC]\\d+";

    private Forms() {
    }
}
