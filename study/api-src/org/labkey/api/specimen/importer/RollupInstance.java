package org.labkey.api.specimen.importer;

import org.labkey.api.data.JdbcType;
import org.labkey.api.specimen.importer.Rollup;
import org.labkey.api.util.Pair;

public class RollupInstance<K extends Rollup> extends Pair<String, K>
{
    private final JdbcType _fromType;
    private final JdbcType _toType;

    public RollupInstance(String first, K second, JdbcType fromType, JdbcType toType)
    {
        super(first.toLowerCase(), second);
        _fromType = fromType;
        _toType = toType;
    }

    public JdbcType getFromType()
    {
        return _fromType;
    }

    public JdbcType getToType()
    {
        return _toType;
    }

    public boolean isTypeConstraintMet()
    {
        return second.isTypeConstraintMet(_fromType, _toType);
    }
}
