// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.common.log;

/** The caller's stated purpose as one log field: control characters replaced, at most {@value #LENGTH} characters. */
public final class PurposeLog {

    public static final int LENGTH = 200;

    private PurposeLog() {
    }

    public static String safe(String purpose) {
        if (purpose == null) {
            return "";
        }
        String flat = purpose.replaceAll("\\p{Cntrl}", " ");
        return flat.length() > LENGTH ? flat.substring(0, LENGTH) : flat;
    }
}
