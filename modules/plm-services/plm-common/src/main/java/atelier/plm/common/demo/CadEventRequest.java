// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.common.demo;

import org.springframework.http.HttpStatus;

/**
 * Body of {@code POST /{plm}/demo/events/cad}: the native id of a part of this PLM and the key of its
 * STEP file in the CAD bucket ({@code cad/<product>/<name>.stp}).
 */
public record CadEventRequest(String part, String cadFile) {

    void requireFields() {
        if (isBlank(part) || isBlank(cadFile)) {
            throw new DemoRejectedException(HttpStatus.BAD_REQUEST, "part and cadFile are required", "missing-field");
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
