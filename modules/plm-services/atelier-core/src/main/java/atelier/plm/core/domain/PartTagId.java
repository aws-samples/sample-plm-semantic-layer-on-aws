// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.core.domain;

import java.io.Serializable;
import java.util.Objects;

/** Composite key of a part tag: the owning PLM's code and the part's key in that PLM. */
public class PartTagId implements Serializable {

    private String plm;
    private String nativeKey;

    protected PartTagId() {
    }

    public PartTagId(String plm, String nativeKey) {
        this.plm = plm;
        this.nativeKey = nativeKey;
    }

    public String getPlm() {
        return plm;
    }

    public String getNativeKey() {
        return nativeKey;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PartTagId that && plm.equals(that.plm) && nativeKey.equals(that.nativeKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(plm, nativeKey);
    }
}
