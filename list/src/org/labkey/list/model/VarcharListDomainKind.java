package org.labkey.list.model;

import org.labkey.api.data.PropertyStorageSpec;

import java.sql.Types;

/**
 * User: Nick
 * Date: 5/9/13
 * Time: 12:57 PM
 */
public class VarcharListDomainKind extends ListDomainKind
{
    protected static final String NAMESPACE_PREFIX = "VarList";

    @Override
    public String getKindName()
    {
        return NAMESPACE_PREFIX;
    }


    @Override
    PropertyStorageSpec getKeyProperty(String keyColumnName)
    {
        return new PropertyStorageSpec(keyColumnName, Types.VARCHAR);
    }
}
