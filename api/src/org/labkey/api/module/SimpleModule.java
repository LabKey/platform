/*
 * Copyright (c) 2008-2010 LabKey Corporation
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
package org.labkey.api.module;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.*;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.view.WebPartFactory;
import org.labkey.data.xml.TablesDocument;
import org.apache.xmlbeans.XmlException;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.*;
import java.beans.PropertyChangeEvent;

/*
* User: Dave
* Date: Dec 3, 2008
* Time: 4:08:20 PM
*/

/**
 * Used for simple, entirely file-based modules
 */
public class SimpleModule extends SpringModule implements ContainerManager.ContainerListener
{
    private static final Logger _log = Logger.getLogger(ModuleUpgrader.class);

    int _factorySetHash = 0;
    private Set<String> _schemaNames;

    public SimpleModule(@NotNull String name)
    {
        setName(name);
        addController(name.toLowerCase(), SimpleController.class);
    }

    protected void init()
    {
        getSchemaNames();
    }

    protected Collection<WebPartFactory> createWebPartFactories()
    {
        List<WebPartFactory> factories = new ArrayList<WebPartFactory>();
        for(File webPartFile : getWebPartFiles())
        {
            factories.add(new SimpleWebPartFactory(this, webPartFile));
        }
        _factorySetHash = calcFactorySetHash();
        return factories;
    }

    @NotNull
    protected File[] getWebPartFiles()
    {
        File viewsDir = new File(getExplodedPath(), SimpleController.VIEWS_DIRECTORY);
        return viewsDir.exists() && viewsDir.isDirectory() ? viewsDir.listFiles(SimpleWebPartFactory.webPartFileFilter) : new File[0];
    }

    public boolean isWebPartFactorySetStale()
    {
        return _factorySetHash != calcFactorySetHash();
    }

    protected int calcFactorySetHash()
    {
        return Arrays.hashCode(getWebPartFiles());
    }

    public boolean hasScripts()
    {
        return getSqlScripts(null, CoreSchema.getInstance().getSqlDialect()).size() > 0;
    }

    @Override
    public Set<String> getSchemaNames()
    {
        if (_schemaNames == null)
        {
            File schemasDir = new File(getExplodedPath(), "schemas");
            if (schemasDir.exists())
            {
                Set<String> schemaNames = new LinkedHashSet<String>();
                File[] schemaFiles = schemasDir.listFiles(new FilenameFilter() {
                    public boolean accept(File dir, String name)
                    {
                        boolean accept = false;
                        if (name.endsWith(".xml"))
                        {
                            try
                            {
                                TablesDocument.Factory.parse(new File(dir, name));
                                accept = true;
                            }
                            catch (XmlException e)
                            {
                                _log.error("Skipping '" + name + "' schema file: " + e.getMessage());
                            }
                            catch (IOException e)
                            {
                                _log.error("Skipping '" + name + "' schema file: " + e.getMessage());
                            }

                        }
                        return accept;
                    }
                });

                for (File schemaFile : schemaFiles)
                {
                    String schemaName = schemaFile.getName().substring(0, schemaFile.getName().length() - ".xml".length());
                    schemaNames.add(schemaName);
                }

                _schemaNames = Collections.unmodifiableSet(schemaNames);
            }
            else
            {
                _schemaNames = Collections.emptySet();
            }
        }
        return _schemaNames;
    }

    public void startup(ModuleContext moduleContext)
    {
        for (final String schemaName : getSchemaNames())
        {
            final DbSchema dbschema = DbSchema.get(schemaName);

            DefaultSchema.registerProvider(schemaName, new DefaultSchema.SchemaProvider()
            {
                public QuerySchema getSchema(final DefaultSchema schema)
                {
                    return new SimpleModuleUserSchema(schemaName, null, schema.getUser(), schema.getContainer(), dbschema);
                }
            });
        }
        ContainerManager.addContainerListener(this);

        File folderTypesDir = new File(getExplodedPath(), SimpleController.FOLDER_TYPES_DIRECTORY);
        for (FolderType folderType : SimpleFolderType.createFromDirectory(folderTypesDir))
            ModuleLoader.getInstance().registerFolderType(folderType);
        
        initWebApplicationContext();
    }

    @Override
    protected ContextType getContextType()
    {
        String realPath = ModuleLoader.getServletContext().getRealPath(getContextXMLPath());
        if (new File(realPath).exists())
        {
            return ContextType.config;
        }
        return ContextType.none;
    }

    protected String getResourcePath()
    {
        return null;
    }

    public ActionURL getTabURL(Container c, User user)
    {
        return SimpleController.getBeginViewUrl(this, c);
    }

    public void containerCreated(Container c)
    {
    }

    public void containerDeleted(Container c, User user)
    {
        // UNDONE: delete data from schemas
    }

    public void propertyChange(PropertyChangeEvent evt)
    {
    }
}