/*
 * Copyright (c) 2013-2018 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.list.model;

import org.labkey.api.data.JdbcType;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.exp.list.ListDefinition;

import java.util.Collection;
import java.util.List;

public class IntegerListDomainKind extends ListDomainKind
{
    public static final String NAMESPACE_PREFIX = "IntList";

    protected static final List<ListDefinition.KeyType> supportedTypes = List.of(
        ListDefinition.KeyType.AutoIncrementInteger,
        ListDefinition.KeyType.Integer
    );

    @Override
    public String getKindName()
    {
        return NAMESPACE_PREFIX;
    }


    @Override
    PropertyStorageSpec getKeyProperty(ListDefinition list, String storageColumnName)
    {
        // See Issue 52271. Consider: Are there any other PropertyDescriptor properties that need to be copied into this
        // PropertyStorageSpec?
        PropertyStorageSpec key = new PropertyStorageSpec(storageColumnName, JdbcType.INTEGER);
        key.setPrimaryKey(true);

        if (list.getKeyType().equals(ListDefinition.KeyType.AutoIncrementInteger))
        {
            key.setAutoIncrement(true);
        }

        return key;
    }

    @Override
    protected ListDefinition.KeyType getDefaultKeyType()
    {
        return ListDefinition.KeyType.AutoIncrementInteger;
    }

    @Override
    protected Collection<ListDefinition.KeyType> getSupportedKeyTypes()
    {
        return supportedTypes;
    }
}
