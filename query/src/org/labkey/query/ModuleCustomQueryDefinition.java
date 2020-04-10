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
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.moduleeditor.api.ModuleEditorService;
import org.labkey.api.query.QueryChangeListener;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.security.User;
import org.labkey.api.util.UnexpectedException;
import org.labkey.query.persist.QueryDef;
import org.labkey.query.persist.QueryManager;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.labkey.query.ModuleQueryDef.FILE_EXTENSION;
import static org.labkey.query.ModuleQueryDef.META_FILE_EXTENSION;

/*
* User: Dave
* Date: Jan 16, 2009
* Time: 11:41:33 AM
*/
public class ModuleCustomQueryDefinition extends CustomQueryDefinitionImpl
{
    private final String _moduleName;
    private final File _resourceSqlFile;
    private final File _resourceQueryXmlFile;

    public ModuleCustomQueryDefinition(ModuleQueryDef moduleQueryDef, SchemaKey schemaKey, User user, Container container)
    {
        super(user, container, moduleQueryDef.toQueryDef(container, schemaKey));
        _moduleName = moduleQueryDef.getModule().getName();

        File queryXML = null;
        File querySQL = ModuleEditorService.get().getFileForModuleResource(moduleQueryDef.getModule(), moduleQueryDef.getPath());

        if (null == querySQL || !querySQL.getName().endsWith(FILE_EXTENSION))
        {
            querySQL = null;
        }
        else
        {
            //public static final String META_FILE_EXTENSION))
            if (null != moduleQueryDef.getMetadataDef())
                queryXML = ModuleEditorService.get().getFileForModuleResource(moduleQueryDef.getModule(), moduleQueryDef.getMetadataDef().getPath());
            if (null == queryXML)
            {
                String path = querySQL.getPath();
                queryXML = new File(path.substring(0, path.length()-FILE_EXTENSION.length()) + META_FILE_EXTENSION);
            }
        }
        _resourceSqlFile = querySQL;
        _resourceQueryXmlFile = queryXML;
    }

    public File getSqlFile()
    {
        return _resourceSqlFile;
    }

    public File getModuleXmlFile()
    {
        return _resourceQueryXmlFile;
    }

    @Override
    public boolean isSqlEditable()
    {
        return null != _resourceSqlFile && _resourceSqlFile.canWrite();
    }

    @Override
    public boolean isMetadataEditable()
    {
        return null != _resourceQueryXmlFile && (
                _resourceQueryXmlFile.exists() ?
                        _resourceQueryXmlFile.canWrite() :
                        _resourceQueryXmlFile.getParentFile().canWrite());
    }

    @Override
    public boolean canEdit(User user)
    {
        return user.isPlatformDeveloper() && isSqlEditable();
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

        // no transactions here, so double check that we think this will work
        if (!isSqlEditable())
            throw new RuntimeException("File is not writable: " + _resourceSqlFile.toPath());
        if (!isMetadataEditable())
            throw new RuntimeException("File is not writable: " + _resourceQueryXmlFile.toPath());

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

            try (var fwSql = new FileWriter(_resourceSqlFile))
            {
                IOUtils.write(_queryDef.getSql(), fwSql);
                // CONSIDER: QueryService.clearCachedQueryDefs(module, _resourceFile);

                if (isBlank(_queryDef.getMetaData()))
                {
                    if (_resourceQueryXmlFile.exists())
                        _resourceQueryXmlFile.delete();
                }
                else
                {
                    if (!_resourceQueryXmlFile.exists())
                        _resourceQueryXmlFile.createNewFile();
                    try (var fwXml = new FileWriter(_resourceQueryXmlFile))
                    {
                        IOUtils.write(_queryDef.getMetaData(), fwXml);
                    }
                }
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
            QueryServiceImpl.get().uncacheModuleResources(ModuleLoader.getInstance().getModule(_moduleName));
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
