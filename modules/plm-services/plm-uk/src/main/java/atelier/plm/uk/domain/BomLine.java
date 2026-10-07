// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.uk.domain;

import atelier.plm.common.annotation.Describe;
import atelier.plm.common.annotation.Maps;
import atelier.plm.common.annotation.OntologyClass;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/** One row of the British PLM's indented bill of materials, native {@code bom_line} table. */
@Entity
@Table(name = "bom_line")
@OntologyClass("atelier:BomLine")
@Describe("BOM line: one row of a site kit's indented bill of materials, the kit itself first on level 0")
public class BomLine {

    @Id
    @Column(name = "line_no")
    @Describe("Line number: the row's position in the indented listing, depth first")
    private Integer lineNo;

    @Column(name = "level")
    @Describe("Level: the row's depth below the site kit, 0 for the kit itself")
    private Integer level;

    @Column(name = "parent_part_no")
    @Describe("Parent part number: the component that uses the child, empty on the kit's own row")
    @Maps("atelier:parent")
    private String parentPartNo;

    @Column(name = "child_part_no")
    @Describe("Child part number: the component used")
    @Maps("atelier:child")
    private String childPartNo;

    @Column(name = "qty")
    @Describe("Quantity: how many of the child the parent uses, in the child's unit of issue")
    @Maps("atelier:quantity")
    private BigDecimal qty;

    protected BomLine() {
    }

    public Integer getLineNo() {
        return lineNo;
    }

    public Integer getLevel() {
        return level;
    }

    public String getParentPartNo() {
        return parentPartNo;
    }

    public String getChildPartNo() {
        return childPartNo;
    }

    public BigDecimal getQty() {
        return qty;
    }
}
