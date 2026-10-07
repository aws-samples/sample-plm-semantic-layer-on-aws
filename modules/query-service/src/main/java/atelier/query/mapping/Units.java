// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.mapping;

import atelier.query.Atelier;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.apache.jena.rdf.model.Model;

/**
 * Converts stored quantity values with the QUDT multipliers of the units graph: lengths to
 * millimetres, pressures to bar, masses to kilograms. A multiplier converts a value to the SI unit of
 * its quantity kind (metre, pascal, kilogram).
 */
public final class Units {
    private static final BigDecimal MM_PER_METRE = BigDecimal.valueOf(1000);
    private static final BigDecimal PA_PER_BAR = BigDecimal.valueOf(100000);

    private final Map<String, BigDecimal> multiplierByUnit = new HashMap<>();

    public Units(Model units) {
        units.listStatements(null, units.createProperty(Atelier.QUDT + "conversionMultiplier"), (String) null)
                .forEachRemaining(s -> multiplierByUnit.put(s.getSubject().getURI(),
                        new BigDecimal(s.getLiteral().getLexicalForm())));
    }

    /** Value in mm (value x multiplier to metre x 1000), or null when the unit is absent or unknown. */
    public BigDecimal toMm(BigDecimal value, String unitIri) {
        BigDecimal si = toSi(value, unitIri);
        return si == null ? null : si.multiply(MM_PER_METRE);
    }

    /** Value in bar (value x multiplier to pascal / 100000), or null when the unit is absent or unknown. */
    public BigDecimal toBar(BigDecimal value, String unitIri) {
        BigDecimal si = toSi(value, unitIri);
        return si == null ? null : si.divide(PA_PER_BAR);
    }

    /** Value in kg (value x multiplier to kilogram), or null when the unit is absent or unknown. */
    public BigDecimal toKg(BigDecimal value, String unitIri) {
        return toSi(value, unitIri);
    }

    private BigDecimal toSi(BigDecimal value, String unitIri) {
        BigDecimal multiplier = unitIri == null ? null : multiplierByUnit.get(unitIri);
        return value == null || multiplier == null ? null : value.multiply(multiplier);
    }
}
