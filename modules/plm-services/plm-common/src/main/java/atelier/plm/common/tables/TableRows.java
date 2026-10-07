// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.common.tables;

import java.util.List;

/**
 * Native rows of one table, as the database returns them: {@code columns} are the table's column
 * names in table order and each row holds the raw JDBC values in the same order (numbers as
 * numbers, NULL as null, strings as strings). {@code keyColumn} names the column the rows were
 * looked up by; {@code policy} is the clearance the rows were filtered with.
 */
public record TableRows(String table, String keyColumn, List<String> columns, List<List<Object>> rows, TablePolicy policy) {
}
