/*
 * Copyright (c) 2008-2012 LabKey Corporation
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

import org.labkey.api.module.Module;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.ContainerFilter;

/**
 * User: jeckels
 * Date: Nov 13, 2008
 */
public abstract class AbstractExpSchema extends UserSchema
{
    protected ContainerFilter _containerFilter = null;

    public AbstractExpSchema(String name, String description, User user, Container container, DbSchema dbSchema)
    {
        super(name, description, user, container, dbSchema);
        _cacheTableInfos = false;
    }

    protected <T extends ExpTable> T setupTable(T table)
    {
        if (_containerFilter != null)
            table.setContainerFilter(_containerFilter);
        table.populate();
        return table;
    }

    public void setContainerFilter(ContainerFilter filter)
    {
        _containerFilter = filter;
    }
}
