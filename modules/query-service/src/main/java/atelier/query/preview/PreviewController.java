// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.preview;

import atelier.query.api.QueryController;
import atelier.query.policy.Policy;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /query/preview} with {@code { product, root?, cells: [ { plm, table, key, column, value } ] }}: what the
 * rules would say if the cells were released, for the caller's profile ({@link PreviewService}). It is a POST for its
 * body only; it writes nothing.
 */
@RestController
public class PreviewController {
    private final PreviewService preview;
    private final Policy policy;

    public PreviewController(PreviewService preview, Policy policy) {
        this.preview = preview;
        this.policy = policy;
    }

    @PostMapping("/query/preview")
    public PreviewJson.Answer preview(@RequestBody PreviewJson.Request request,
                                      @RequestHeader(name = QueryController.PROFILE_HEADER, required = false) String profile,
                                      @RequestHeader(name = QueryController.ACTOR_HEADER, required = false) String actor) {
        return preview.preview(policy.caller(profile, actor), request);
    }
}
