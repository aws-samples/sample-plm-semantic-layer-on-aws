// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.common.demo;

import atelier.plm.common.annotation.Maps;
import atelier.plm.common.annotation.OntologyClass;
import atelier.plm.common.annotation.Unit;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.Metamodel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

/** The columns an entity's annotations leave uncorrectable, without a database. */
class UpdatableColumnTest {

    /** A part table whose lifecycle and revision declare no accepted values and whose unit column declares no stored unit. */
    @Table(name = "teil")
    @OntologyClass("atelier:Part")
    static class Unconstrained {
        @Id
        @Column(name = "nr")
        String nr;

        @Column(name = "status")
        @Maps("atelier:lifecycleLabel")
        String status;

        @Column(name = "revision")
        @Maps("atelier:revision")
        String revision;

        @Column(name = "material")
        @Maps("atelier:material")
        String material;

        @Column(name = "laenge")
        @Unit(column = "einheit")
        @Maps("atelier:nominalLength")
        BigDecimal laenge;

        @Column(name = "einheit")
        String einheit;

        @Column(name = "aktiv")
        @Maps("atelier:active")
        Boolean aktiv;
    }

    private static Metamodel metamodel() {
        EntityType<?> entity = mock(EntityType.class);
        doReturn(Unconstrained.class).when(entity).getJavaType();
        Metamodel metamodel = mock(Metamodel.class);
        doReturn(Set.of(entity)).when(metamodel).getEntities();
        return metamodel;
    }

    private static void notCorrectable(String column) {
        assertThatThrownBy(() -> UpdatableColumn.of(metamodel(), "teil", column))
                .isInstanceOfSatisfying(DemoRejectedException.class, e -> assertThat(e.toError().reason()).as(column).isEqualTo("not-correctable"));
    }

    @Test
    void aLifecycleOrRevisionWithoutAcceptedValuesIsNotCorrectable() {
        notCorrectable("status");
        notCorrectable("revision");
    }

    @Test
    void aUnitColumnWhoseFieldsDeclareNoStoredUnitIsNotCorrectable() {
        notCorrectable("einheit");
        assertThat(UpdatableColumn.of(metamodel(), "teil", "laenge").kind()).isEqualTo(UpdatableColumn.Kind.MEASURE);
    }

    @Test
    void textOrFlagTermsOutsideTheCorrectableOnesAreNotCorrectable() {
        notCorrectable("material");
        notCorrectable("aktiv");
    }
}
