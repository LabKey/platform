/*
 * Copyright (c) 2013-2016 LabKey Corporation
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
    PropertyStorageSpec getKeyProperty(ListDefinition list)
    {
        PropertyStorageSpec key = new PropertyStorageSpec(list.getKeyName(), JdbcType.VARCHAR);
        key.setPrimaryKey(true);
        return key;
    }

    @Override
    public ListDefinition.KeyType getKeyType()
    {
        return ListDefinition.KeyType.Varchar;
    }
}
