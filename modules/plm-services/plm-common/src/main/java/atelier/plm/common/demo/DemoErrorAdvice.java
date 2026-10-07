// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.common.demo;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Answers every {@link DemoRejectedException} of the demo-control endpoints with its status and a {@link DemoError}. */
@RestControllerAdvice
public class DemoErrorAdvice {

    @ExceptionHandler(DemoRejectedException.class)
    public ResponseEntity<DemoError> rejected(DemoRejectedException e) {
        return ResponseEntity.status(e.status()).body(e.toError());
    }
}
