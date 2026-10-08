// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.policy;

import atelier.query.api.Json;
import java.util.List;

/**
 * Who asks: the viewer profile the answer is computed for, and the actor acting on the viewer's
 * behalf as the {@code x-atelier-actor} header names it ({@code user} when absent; an agent names
 * itself). The actor changes nothing in the answer; it is echoed in the answer's policy so the
 * acting agent stays distinguishable from the user.
 */
public record Caller(Policy.Profile profile, String actor) {
    public static final String USER = "user";

    /** The viewer acting in person. */
    public static Caller user(Policy.Profile profile) {
        return new Caller(profile, USER);
    }

    /** @param untagged IRIs of the parts the answer named that carry no tag. */
    public Json.Policy json(List<String> untagged) {
        return new Json.Policy(profile.name(), profile.releasable(), profile.filter(), untagged, actor);
    }
}
