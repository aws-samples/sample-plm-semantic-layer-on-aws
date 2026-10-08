// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.common.sql;

/** Body of {@code POST /{plm}/sql}: the SELECT to run and what the caller wants it for (logged with the run). */
public record SqlRequest(String sql, String purpose) {
}
