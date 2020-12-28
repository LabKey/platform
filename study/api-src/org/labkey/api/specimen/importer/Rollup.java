package org.labkey.api.specimen.importer;

import org.labkey.api.data.JdbcType;
import org.labkey.api.exp.PropertyDescriptor;

import java.util.List;

/**
 * Rollups from one table to another. Patterns specify what the To table must be to match,
 * where '%' is the full name of the From field name.
 */
public interface Rollup
{
    List<String> getPatterns();

    boolean isTypeConstraintMet(JdbcType from, JdbcType to);

    boolean match(PropertyDescriptor from, PropertyDescriptor to, boolean allowTypeMismatch);

    default boolean canPromoteNumeric(JdbcType from, JdbcType to)
    {
        return (from.isNumeric() && to.isNumeric() && JdbcType.promote(from, to) == to);
    }
}
