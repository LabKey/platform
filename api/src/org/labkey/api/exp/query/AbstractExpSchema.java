/*
 * Copyright (c) 2009-2019 LabKey Corporation
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
package org.labkey.api.exp.query;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DbSchema;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;

/**
 * User: jeckels
 * Date: Nov 13, 2008
 */
public abstract class AbstractExpSchema extends UserSchema
{
    protected ContainerFilter _containerFilter = null;

    public AbstractExpSchema(String name, String description, User user, Container container, DbSchema dbSchema)
    {
        this(SchemaKey.fromParts(name), description, user, container, dbSchema);
    }

    public AbstractExpSchema(SchemaKey path, String description, User user, Container container, DbSchema dbSchema)
    {
        super(path, description, user, container, dbSchema, null);
        _cacheTableInfos = false;
    }

    protected <T extends ExpTable> T setupTable(T table)
    {
        // Note, this is 'legal' since a schema can modify its own tables during construction
        // Then again, if the CF is not set during TableInfo construction, we may not be able to set of construct FK's correctly
        // TODO ContainerFilter -- is this still necessary, or is CF set up in constructor in all cases?
        if (_containerFilter != null)
            table.setContainerFilter(_containerFilter);
        table.populate();
        return table;
    }

    public void setContainerFilter(ContainerFilter filter)
    {
        _containerFilter = filter;
    }

    @Override
    public @NotNull ContainerFilter getDefaultContainerFilter()
    {
        if (null != _containerFilter)
            return _containerFilter;
        return super.getDefaultContainerFilter();
    }
}
