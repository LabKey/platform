/*
 * Copyright (c) 2009-2017 LabKey Corporation
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
package org.labkey.query;

import org.labkey.api.module.Module;
import org.labkey.api.query.QueryChangeListener;
import org.labkey.api.query.SchemaKey;
import org.labkey.query.persist.QueryDef;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;

import java.sql.SQLException;
import java.util.Collection;

/*
* User: Dave
* Date: Jan 16, 2009
* Time: 11:41:33 AM
*/
public class ModuleCustomQueryDefinition extends CustomQueryDefinitionImpl
{
    private final String _moduleName;

    public ModuleCustomQueryDefinition(ModuleQueryDef moduleQueryDef, SchemaKey schemaKey, User user, Container container)
    {
        super(user, container, moduleQueryDef.toQueryDef(container, schemaKey));
        _moduleName = moduleQueryDef.getModule().getName();
    }

    @Override
    public boolean isSqlEditable()
    {
        return false;
    }

    @Override
    public boolean isMetadataEditable()
    {
        return false;
    }

    @Override
    public boolean canEdit(User user)
    {
        return false;
    }

    @Override
    public String getModuleName()
    {
        return _moduleName;
    }

    @Override
    public Collection<QueryChangeListener.QueryPropertyChange> save(User user, Container container) throws SQLException
    {
        throw new UnsupportedOperationException("Module-based queries are read-only!");
    }

    @Override
    public Collection<QueryChangeListener.QueryPropertyChange> save(User user, Container container, boolean fireChangeEvent) throws SQLException
    {
        throw new UnsupportedOperationException("Module-based queries are read-only!");
    }

    @Override
    public void delete(User user) throws SQLException
    {
        throw new UnsupportedOperationException("Module-based queries are read-only!");
    }

    @Override
    protected QueryDef edit()
    {
        throw new UnsupportedOperationException("Module-based queries are read-only!");
    }
}
