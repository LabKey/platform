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
package org.labkey.query.controllers;

import org.apache.commons.lang3.StringUtils;
import org.json.old.JSONArray;
import org.json.old.JSONObject;
import org.labkey.api.action.Action;
import org.labkey.api.action.ActionType;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ReadOnlyApiAction;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.springframework.validation.BindException;

import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * User: dave
 * Date: Sep 3, 2009
 * Time: 1:09:00 PM
 *
 * This class is used by the schema explorer query tree only
 */

@RequiresPermission(ReadPermission.class)
@Action(ActionType.SelectMetaData.class)
public class GetSchemaQueryTreeAction extends ReadOnlyApiAction<GetSchemaQueryTreeAction.Form>
{
    @Override
    protected long getLastModified(Form form)
    {
        return QueryService.get().metadataLastModified();
    }

    @Override
    public ApiResponse execute(Form form, BindException errors) throws Exception
    {
        JSONArray respArray = new JSONArray();
        Container container = getContainer();
        User user = getUser();
        DefaultSchema defSchema = DefaultSchema.get(user, container);

        if ("root".equals(form.getNode()))
        {
            final Map<DbScope, JSONArray> map = new LinkedHashMap<>();

            // Initialize a JSONArray for each scope; later, we'll enumerate and skip the scopes that aren't actually
            // used in this folder.  This approach ensures we order the scopes naturally (i.e., labkey scope first).
            for (DbScope scope : DbScope.getDbScopes())
                map.put(scope, new JSONArray());

            // return list of top-level schemas grouped by datasource
            for (String name : defSchema.getUserSchemaNames(true))
            {
                QuerySchema schema = DefaultSchema.get(user, container).getSchema(name);
                if (null == schema || null == schema.getDbSchema())
                    continue;

                if (schema.isHidden() && !form.isShowHidden())
                    continue;

                DbScope scope = schema.getDbSchema().getScope();
                JSONArray schemas = map.get(scope);

                SchemaKey schemaName = new SchemaKey(null, schema.getName());
                JSONObject schemaProps = getSchemaProps(schemaName, schema);

                schemas.put(schemaProps);
            }

            for (Map.Entry<DbScope, JSONArray> entry : map.entrySet())
            {
                DbScope scope = entry.getKey();
                JSONArray schemas = entry.getValue();

                if (schemas.length() > 0)
                {
                    String dsName = scope.getDataSourceName();
                    JSONObject ds = new JSONObject();
                    ds.put("text", "Schemas in " + scope.getDisplayName());
                    ds.put("description", "Schemas in data source '" + dsName + "'");
                    ds.put("expanded", true);
                    ds.put("children", schemas);
                    ds.put("name", dsName);
                    ds.put("dataSourceName", dsName);

                    respArray.put(ds);
                }
            }
        }
        else
        {
            if (null != form.getSchemaName())
            {
                SchemaKey schemaPath = form.getSchemaName();
                UserSchema uschema = QueryService.get().getUserSchema(user, container, schemaPath);

                if (null != uschema)
                {

                    // Add any children schemas
                    for (UserSchema child : uschema.getUserSchemas(true))
                    {
                        if (child.isHidden() && !form.isShowHidden())
                            continue;

                        SchemaKey childPath = new SchemaKey(schemaPath, child.getName());
                        JSONObject schemaProps = getSchemaProps(childPath, child);
                        respArray.put(schemaProps);
                    }

                    Map<String, JSONObject> queries = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
                    if (form.isShowBuiltInTables())
                    {
                        //get built-in queries
                        List<String> queryNames = new ArrayList<>(form.isShowHidden() ? uschema.getTableNames() : uschema.getVisibleTableNames());

                        for (String qname : queryNames)
                        {
                            TableInfo tinfo = null;
                            try
                            {
                                // Try to get the TableInfo so we can send back its description
                                tinfo = uschema.getTable(qname);
                            }
                            catch (QueryException ignored)
                            {
                            }

                            String label = qname;
                            if (null != tinfo)
                                label = tinfo.getTitle();           // Display title defaults to name, but uses label if set

                            // If there's an error, still include the table in the tree
                            addQueryToList(qname, schemaPath, qname, label, tinfo == null ? null : tinfo.getDescription(), false, queries, true, false);
                        }
                    }

                    if (form.isShowModuleDefined() || form.isShowUserDefined())
                    {
                        //get user-defined queries
                        Map<String, QueryDefinition> queryDefMap = uschema.getQueryDefs();
                        List<String> queryNames = new ArrayList<>(queryDefMap.keySet());

                        for (String qname : queryNames)
                        {
                            QueryDefinition qdef = queryDefMap.get(qname);
                            if (!qdef.isTemporary())
                            {
                                if (qdef.isHidden() && !form.isShowHidden())
                                    continue;

                                boolean fromModule = !StringUtils.isBlank(qdef.getModuleName());

                                if ((!fromModule && form.isShowUserDefined()) ||
                                        (fromModule && form.isShowModuleDefined()))
                                {
                                    // LinkedSchemaQueryDefinitions should be considered to be tables, and we can distinguish them
                                    // based on how the report isUserDefined()
                                    addQueryToList(qname, schemaPath, qname, qname, qdef.getDescription(), qdef.isHidden(), queries, !qdef.isUserDefined(), fromModule);
                                }
                            }
                        }

                        for (JSONObject value : queries.values())
                        {
                            respArray.put(value);
                        }
                    }
                }
            }
            else if (form.isAllUserDefined())
            {
                Map<String, JSONObject> queries = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
                for (QueryDefinition queryDef : QueryService.get().getQueryDefs(user, container))
                {
                    if (!queryDef.isTemporary())
                    {
                        if (queryDef.isHidden() && !form.isShowHidden())
                            continue;

                        addQueryToList(queryDef.getQueryKey(), queryDef.getSchemaPath(), queryDef.getName(), queryDef.getName(), queryDef.getDescription(), queryDef.isHidden(), queries, !queryDef.isUserDefined(), false);
                    }
                }

                for (JSONObject value : queries.values())
                {
                    respArray.put(value);
                }
            }
        }

        HttpServletResponse resp = getViewContext().getResponse();
        resp.setContentType("application/json");
        resp.getWriter().write(respArray.toString());

        return null;
    }


    protected JSONObject getSchemaProps(SchemaKey schemaName, QuerySchema schema)
    {
        JSONObject schemaProps = new JSONObject();
        schemaProps.put("text", schema.getName());
        schemaProps.put("description", schema.getDescription());
        schemaProps.put("name", schema.getName());
        schemaProps.put("schemaName", schemaName);
        schemaProps.put("hidden", schema.isHidden());
        return schemaProps;
    }

    protected void addQueryToList(String qkey, SchemaKey schemaName, String qname, String label, String description, boolean hidden, Map<String, JSONObject> list, boolean table, boolean fromModule)
    {
        JSONObject qprops = new JSONObject();
        qprops.put("key", qkey);
        qprops.put("schemaName", schemaName);
        qprops.put("queryName", qname);
        qprops.put("queryLabel", label);
        String text = qname;
        if (!qname.equalsIgnoreCase(label))
            text += " (" + label + ")";
        qprops.put("text", text);
        qprops.put("leaf", true);
        qprops.put("table", table);
        if (!table)
        {
            qprops.put("fromModule", fromModule);
        }
        if (null != description)
        {
            qprops.put("description", description);
        }
        qprops.put("hidden", hidden);
        list.put(qkey, qprops);
    }

    public static class Form
    {
        private String _node;
        private SchemaKey _schemaName;
        private boolean _showHidden;
        private boolean _allUserDefined = false; // used to get all user defined queries across all schemas for a container
        private boolean _showUserDefined = true;
        private boolean _showModuleDefined = true;
        private boolean _showBuiltInTables = true;

        public String getNode()
        {
            return _node;
        }

        public void setNode(String node)
        {
            _node = node;
        }

        public SchemaKey getSchemaName()
        {
            return _schemaName;
        }

        public void setSchemaName(SchemaKey schemaName)
        {
            _schemaName = schemaName;
        }

        public boolean isShowHidden()
        {
            return _showHidden;
        }

        public void setShowHidden(boolean showHidden)
        {
            _showHidden = showHidden;
        }

        public boolean isAllUserDefined()
        {
            return _allUserDefined;
        }

        public void setAllUserDefined(boolean allUserDefined)
        {
            _allUserDefined = allUserDefined;
        }

        public boolean isShowUserDefined()
        {
            return _showUserDefined;
        }

        public void setShowUserDefined(boolean showUserDefined)
        {
            _showUserDefined = showUserDefined;
        }

        public boolean isShowModuleDefined()
        {
            return _showModuleDefined;
        }

        public void setShowModuleDefined(boolean showModuleDefined)
        {
            _showModuleDefined = showModuleDefined;
        }

        public boolean isShowBuiltInTables()
        {
            return _showBuiltInTables;
        }

        public void setShowBuiltInTables(boolean showBuiltInTables)
        {
            _showBuiltInTables = showBuiltInTables;
        }
    }
}
