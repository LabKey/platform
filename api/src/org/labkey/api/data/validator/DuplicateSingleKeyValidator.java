package org.labkey.api.data.validator;

import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.JdbcType;

import java.util.HashSet;
import java.util.Set;

/**
 * Validate that there are no incoming duplicates for unique key.
 * Does not validate against DB, just the import data.
 */
public class DuplicateSingleKeyValidator extends AbstractColumnValidator
{
    final boolean caseInsensitive;
    final JdbcType _jdbcType;

    Set _keys = null;

    public DuplicateSingleKeyValidator(String columnLabel, JdbcType jdbcType, boolean caseInsensitive)
    {
        super(columnLabel);
        this._jdbcType = jdbcType;
        this.caseInsensitive = caseInsensitive;
    }

    @Override
    public String _validate(int rowNum, Object key)
    {
        if (null == _keys)
        {
            if (caseInsensitive && _jdbcType.isText())
                _keys = new CaseInsensitiveHashSet();
            else
                _keys = new HashSet();
        }
        if (_keys.size() > 10000 || _keys.add(key))
            return null;
        return "Row " + rowNum + ": " + "The key field \"" + _columnLabel + "\" cannot have duplicate values.  The duplicate is: \"" + key + "\"";
    }
}
