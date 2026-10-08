// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.common.sql;

import java.util.List;

/**
 * Body of a 400 answer of {@code POST /{plm}/sql}: {@code error} says what was wrong in words,
 * {@code reason} is the stable category (e.g. {@code unknown-column}, {@code not-a-select},
 * {@code system-catalogue}), {@code suggestions} the closest catalogue names when a name was unknown.
 */
public record SqlError(String error, String reason, List<String> suggestions) {
}
