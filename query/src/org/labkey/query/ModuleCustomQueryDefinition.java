/*
 * Copyright (c) 2009-2018 LabKey Corporation
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

import org.apache.commons.io.IOUtils;
import org.labkey.api.data.Container;
import org.labkey.api.moduleeditor.api.ModuleEditorService;
import org.labkey.api.query.QueryChangeListener;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.security.User;
import org.labkey.api.util.UnexpectedException;
import org.labkey.query.persist.QueryDef;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;

/*
* User: Dave
* Date: Jan 16, 2009
* Time: 11:41:33 AM
*/
public class ModuleCustomQueryDefinition extends CustomQueryDefinitionImpl
{
    private final String _moduleName;
    private final File _resourceFile;

    public ModuleCustomQueryDefinition(ModuleQueryDef moduleQueryDef, SchemaKey schemaKey, User user, Container container)
    {
        super(user, container, moduleQueryDef.toQueryDef(container, schemaKey));
        _moduleName = moduleQueryDef.getModule().getName();
        _resourceFile = ModuleEditorService.get().getFileForModuleResource(moduleQueryDef.getModule(), moduleQueryDef.getPath());
    }

    @Override
    public boolean isSqlEditable()
    {
        return null != _resourceFile;
    }

    @Override
    public boolean isMetadataEditable()
    {
        return false;
    }

    @Override
    public boolean canEdit(User user)
    {
        return null != _resourceFile && user.isPlatformDeveloper();
    }

    @Override
    public String getModuleName()
    {
        return _moduleName;
    }

    @Override
    public Collection<QueryChangeListener.QueryPropertyChange> save(User user, Container container)
    {
        return save(user, container, true);
    }

    @Override
    protected boolean isNew()
    {
        return false;
    }

    @Override
    public Collection<QueryChangeListener.QueryPropertyChange> save(User user, Container container, boolean fireChangeEvent)
    {
        if (!_dirty)
            return null;

        if (isNew())
        {
            throw new UnsupportedOperationException("Module-based queries can not be created.");
        }
        else
        {
            // TODO handle metadata xml
            for (QueryChangeListener.QueryPropertyChange change : _changes)
            {
                throw new UnsupportedOperationException("Can't change property: " + change.getProperty());
            }

            try (var fw = new FileWriter(_resourceFile))
            {
                IOUtils.write(_queryDef.getSql(), fw);
                // CONSIDER: QueryService.clearCachedQueryDefs(module, _resourceFile);
            }
            catch (IOException x)
            {
                // TODO what is best exception to throw for caller?
                throw UnexpectedException.wrap(x);
            }

            if (fireChangeEvent)
            {
                // Fire change event for each property change.
                for (QueryChangeListener.QueryPropertyChange change : _changes)
                {
                    QueryService.get().fireQueryChanged(user, container, null, _queryDef.getSchemaPath(), change.getProperty(), Collections.singleton(change));
                }
            }
        }

        Collection<QueryChangeListener.QueryPropertyChange> changes = _changes;
        _changes = null;
        _dirty = false;
        return changes;
    }

    @Override
    public void delete(User user)
    {
        throw new UnsupportedOperationException("Module-based queries can not be deleted.");
    }

    @Override
    protected QueryDef edit()
    {
        if (isSqlEditable() || isMetadataEditable())
            return super.edit();
        throw new UnsupportedOperationException("Module-based query is not editable: " + getName());
    }
}
