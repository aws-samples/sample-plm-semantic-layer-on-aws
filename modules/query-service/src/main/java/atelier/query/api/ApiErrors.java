// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.api;

import atelier.query.AmbiguousInterface;
import atelier.query.federation.EndpointFailure;
import atelier.query.federation.UnknownItem;
import atelier.query.federation.UnknownProduct;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Error answers of the REST API: a failing federated endpoint is 502 naming the endpoint (its
 * detail stays in the log), a product no interface's part belongs to is 404, a subtree root that names no item of
 * the product is 404, an interface id that
 * several products have is 400 naming them, a reference the request cannot mean is 400.
 */
@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler(EndpointFailure.class)
    public ResponseEntity<Map<String, String>> endpointFailure(EndpointFailure ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(UnknownProduct.class)
    public ResponseEntity<Map<String, String>> unknownProduct(UnknownProduct ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(UnknownItem.class)
    public ResponseEntity<Map<String, String>> unknownItem(UnknownItem ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }

    /** {@code { error, id, products }}: the id asked for and the keys of the products that have an interface of that id. */
    @ExceptionHandler(AmbiguousInterface.class)
    public ResponseEntity<Map<String, Object>> ambiguousInterface(AmbiguousInterface ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", ex.getMessage());
        body.put("id", ex.id());
        body.put("products", ex.products());
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badReference(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }
}
