// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.common.demo;

/**
 * Body of a refused demo-control request: {@code error} says what was wrong in words, {@code reason}
 * is the stable category (e.g. {@code not-allowed}, {@code key-column}, {@code row-not-found}).
 */
public record DemoError(String error, String reason) {
}
