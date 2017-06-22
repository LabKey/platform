/*
 * Copyright (c) 2008-2017 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.FileSqlScriptProvider;
import org.labkey.api.data.Filter;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlScriptRunner.SqlScriptException;
import org.labkey.api.data.SqlScriptRunner.SqlScriptProvider;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.WebPartFactory;
import org.springframework.web.servlet.mvc.Controller;

import javax.servlet.http.HttpServletRequest;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/*
* User: Dave
* Date: Dec 3, 2008
* Time: 4:08:20 PM
*/

/**
 * Used for simple, entirely file-based modules
 */
public class SimpleModule extends SpringModule
{
    private static final Logger _log = Logger.getLogger(ModuleUpgrader.class);

    public static String NAMESPACE_PREFIX = "ExtensibleTable";
    public static String DOMAIN_NAMESPACE_PREFIX_TEMPLATE = NAMESPACE_PREFIX + "-${SchemaName}";
    public static String DOMAIN_LSID_TEMPLATE = "${FolderLSIDBase}:${TableName}";

    public static String PROPERTY_NAMESPACE_PREFIX_TEMPLATE = NAMESPACE_PREFIX + "-${SchemaName}-${TableName}";
    public static String PROPERTY_LSID_TEMPLATE = "${FolderLSIDBase}:${GUID}";

    public SimpleModule()
    {
    }

    @Deprecated
    public SimpleModule(String name)
    {
        setName(name);
    }

    protected void init()
    {
        if (getName() == null || getName().length() == 0)
            throw new ConfigurationException("Simple module must have a name");

        addController(getName().toLowerCase(), SimpleController.class);
    }

    @Override
    public Controller getController(@Nullable HttpServletRequest request, Class controllerClass)
    {
        return new SimpleController(getName().toLowerCase());
    }

    @NotNull
    protected Collection<? extends WebPartFactory> createWebPartFactories()
    {
        return Collections.emptyList();
    }

    @Override
    public boolean hasScripts()
    {
        SqlScriptProvider provider = new FileSqlScriptProvider(this);

        for (DbSchema schema : provider.getSchemas())
        {
            try
            {
                if (!provider.getScripts(schema).isEmpty())
                    return true;
            }
            catch (SqlScriptException e)
            {
                ExceptionUtil.logExceptionToMothership(null, e);
            }
        }

        return false;
    }

    @Override
    @NotNull
    public Collection<String> getSchemaNames()
    {
        List<String> schemaNames = DbScope.getSchemaNames(this);
        Collections.sort(schemaNames);  // Alphabetical is better than random

        return Collections.unmodifiableList(schemaNames);
    }

    @Override
    protected void startupAfterSpringConfig(ModuleContext moduleContext)
    {
        registerSchemas();
        registerContainerListeners();
    }

    protected void registerContainerListeners()
    {
        ContainerManager.addContainerListener(new SimpleModuleContainerListener(this));
    }

    protected void registerSchemas()
    {
        for (final String schemaName : getSchemaNames())
        {
            DbSchema dbSchema = DbSchema.get(schemaName);
            DefaultSchema.registerProvider(dbSchema.getQuerySchemaName(), new DefaultSchema.SchemaProvider(this)
            {
                public QuerySchema createSchema(final DefaultSchema schema, Module module)
                {
                    DbSchema dbSchema = DbSchema.get(schemaName);
                    return QueryService.get().createSimpleUserSchema(dbSchema.getQuerySchemaName(), null, schema.getUser(), schema.getContainer(), dbSchema);
                }
            });
        }
    }

    @Override
    public void setResourcePath(String resourcePath)
    {
        if (StringUtils.isNotEmpty(resourcePath))
        {
            // initialized to null, only gets a value if set in module.properties or .xml file
            _resourcePath = resourcePath;
        }
    }

    @Override
    public ActionURL getTabURL(Container c, User user)
    {
        return SimpleController.getBeginViewUrl(this, c);
    }

    @NotNull
    @Override
    public Collection<String> getSummary(Container c)
    {
        Collection<String> summary = new LinkedList<>();

        User user = HttpView.currentContext().getUser();

        Filter containerFilter = SimpleFilter.createContainerFilter(c);
        Filter folderFilter = new SimpleFilter(new FieldKey(null, "Folder"), c);

        for (String schemaName : getSchemaNames())
        {
            UserSchema schema = QueryService.get().getUserSchema(user, c, schemaName);
            if (schema != null && !schema.isHidden())
            {
                for (String tableName : schema.getVisibleTableNames())
                {
                    TableInfo table = schema.getTable(tableName, false);
                    if (table != null)
                    {
                        Filter filter = null;
                        if (table.getColumn("Container") != null)
                            filter = containerFilter;
                        else if (table.getColumn("Folder") != null)
                            filter = folderFilter;

                        if (filter != null)
                        {
                            long count = new TableSelector(table, containerFilter, null).getRowCount();
                            if (count > 0)
                                summary.add(String.format("%d %s from %s.%s", count, (count == 1 ? "row" : "rows"), schema.getSchemaPath().toDisplayString(), table.getName()));
                        }
                    }
                }
            }
        }

        return summary;
    }
}
