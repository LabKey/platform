package org.labkey.list.model;

import org.labkey.api.data.JdbcType;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.exp.list.ListDefinition;

/**
 * User: Nick
 * Date: 5/9/13
 * Time: 12:57 PM
 */
public class IntegerListDomainKind extends ListDomainKind
{
    protected static final String NAMESPACE_PREFIX = "IntList";

    @Override
    public String getKindName()
    {
        return NAMESPACE_PREFIX;
    }


    @Override
    PropertyStorageSpec getKeyProperty(ListDefinition list)
    {
        PropertyStorageSpec key = new PropertyStorageSpec(list.getKeyName(), JdbcType.INTEGER);
        key.setPrimaryKey(true);

        if (list.getKeyType().equals(ListDefinition.KeyType.AutoIncrementInteger))
        {
            key.setAutoIncrement(true);
        }

        return key;
    }
}
