// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.es.domain;

import atelier.plm.common.annotation.Describe;
import atelier.plm.common.annotation.Maps;
import atelier.plm.common.annotation.OntologyClass;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * One line of a parent piece's {@code lista_materiales} document, read through the view
 * {@code linea_lista_materiales}: the semantic layer maps relations, not JSON, so the view is what the
 * mapping reads. Read-only: the document on the parent row is where a line is written.
 */
@Entity
@Immutable
@Table(name = "linea_lista_materiales")
@IdClass(LineaListaMateriales.Key.class)
@OntologyClass("atelier:BomLine")
@Describe("Línea de lista de materiales: one line of a parent piece's lista_materiales document, as a row of the view over the document")
public class LineaListaMateriales {

    @Id
    @Column(name = "padre")
    @Describe("Padre: the piece whose document holds the line (an assembly or the site kit)")
    @Maps("atelier:parent")
    private String padre;

    @Id
    @Column(name = "referencia")
    @Describe("Referencia: the piece the line uses")
    @Maps("atelier:child")
    private String referencia;

    @Column(name = "cantidad")
    @Describe("Cantidad: how many of the piece the parent uses")
    @Maps("atelier:quantity")
    private BigDecimal cantidad;

    protected LineaListaMateriales() {
    }

    public String getPadre() {
        return padre;
    }

    public String getReferencia() {
        return referencia;
    }

    public BigDecimal getCantidad() {
        return cantidad;
    }

    /** Key of a line: the parent and the piece it uses, once per document. */
    public static class Key implements Serializable {
        private String padre;
        private String referencia;

        public Key() {
        }

        public Key(String padre, String referencia) {
            this.padre = padre;
            this.referencia = referencia;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(padre, k.padre) && Objects.equals(referencia, k.referencia);
        }

        @Override
        public int hashCode() {
            return Objects.hash(padre, referencia);
        }
    }
}
