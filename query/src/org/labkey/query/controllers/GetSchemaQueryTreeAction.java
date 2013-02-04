/*
 * Copyright (c) 2009-2013 LabKey Corporation
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

import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.action.ApiAction;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.*;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.PageFlowUtil;
import org.springframework.validation.BindException;

import javax.servlet.http.HttpServletResponse;
import java.util.*;

/**
 * User: dave
 * Date: Sep 3, 2009
 * Time: 1:09:00 PM
 *
 * This class is used by the schema explorer query tree only
 */

@RequiresPermissionClass(ReadPermission.class)
public class GetSchemaQueryTreeAction extends ApiAction<GetSchemaQueryTreeAction.Form>
{
    // the schema browser behaves very badly if the table list gets too long, so we stop after a reasonable number
    // of tables.  The user can view the full list in the query window.
    private static final int MAX_TABLES_TO_LIST  = 100;
    public ApiResponse execute(Form form, BindException errors) throws Exception
    {
        JSONArray respArray = new JSONArray();
        Container container = getViewContext().getContainer();
        User user = getViewContext().getUser();
        DefaultSchema defSchema = DefaultSchema.get(user, container);

        if ("root".equals(form.getNode()))
        {
            final Map<DbScope, JSONArray> map = new LinkedHashMap<DbScope, JSONArray>();

            // Initialize a JSONArray for each scope; later, we'll enumerate and skip the scopes that aren't actually
            // used in this folder.  This approach ensures we order the scopes naturally (i.e., labkey scope first).
            for (DbScope scope : DbScope.getDbScopes())
                map.put(scope, new JSONArray());

            // return list of top-level schemas grouped by datasource
            for (String name : defSchema.getUserSchemaNames())
            {
                QuerySchema schema = DefaultSchema.get(user, container).getSchema(name);
                if (null == schema || null == schema.getDbSchema())
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
                    ds.put("qtip", "Schemas in data source '" + dsName + "'");
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
                    JSONArray userDefined = new JSONArray();
                    JSONArray builtIn = new JSONArray();

                    //get built-in queries
                    List<String> queryNames = new ArrayList<String>(uschema.getVisibleTableNames());
                    Collections.sort(queryNames, new Comparator<String>(){
                        public int compare(String name1, String name2)
                        {
                            return name1.compareToIgnoreCase(name2);
                        }
                    });

                    int addedQueryCount = 0;
                    for (int i = 0; i < queryNames.size() && addedQueryCount < MAX_TABLES_TO_LIST; i++)
                    {
                        String qname = queryNames.get(i);
                        TableInfo tinfo = null;
                        try
                        {
                            // Try to get the TableInfo so we can send back its description
                            tinfo = uschema.getTable(qname);
                        }
                        catch (QueryException ignored) {}

                        // If there's an error, still include the table in the tree
                        addQueryToList(schemaPath, qname, tinfo == null ? null : tinfo.getDescription(), builtIn);
                        addedQueryCount++;
                    }

                    if (addedQueryCount == MAX_TABLES_TO_LIST && addedQueryCount < queryNames.size())
                        addMoreLinkToList(schemaPath, builtIn);

                    //get user-defined queries
                    Map<String, QueryDefinition> queryDefMap = uschema.getQueryDefs();
                    queryNames = new ArrayList<String>(queryDefMap.keySet());
                    Collections.sort(queryNames, new Comparator<String>(){
                        public int compare(String name1, String name2)
                        {
                            return name1.compareToIgnoreCase(name2);
                        }
                    });

                    addedQueryCount = 0;
                    for (int i = 0; i < queryNames.size() && addedQueryCount < MAX_TABLES_TO_LIST; i++)
                    {
                        String qname = queryNames.get(i);
                        QueryDefinition qdef = queryDefMap.get(qname);
                        if (!qdef.isTemporary())
                        {
                            addQueryToList(schemaPath, qname, qdef.getDescription(), userDefined);
                            addedQueryCount++;
                        }
                    }

                    if (addedQueryCount == MAX_TABLES_TO_LIST && addedQueryCount < queryNames.size())
                        addMoreLinkToList(schemaPath, userDefined);

                    //group the user-defined and built-in queries into folders
                    if (userDefined.length() > 0)
                    {
                        JSONObject fldr = new JSONObject();
                        fldr.put("text", "user-defined queries");
                        fldr.put("qtip", "Custom queries created by you and those shared by others.");
                        fldr.put("expanded", true);
                        fldr.put("children", userDefined);
                        fldr.put("schemaName", schemaPath);
                        respArray.put(fldr);
                    }

                    if (builtIn.length() > 0)
                    {
                        JSONObject fldr = new JSONObject();
                        fldr.put("text", PageFlowUtil.filter("built-in queries & tables"));
                        fldr.put("qtip", "Queries and tables that are part of the schema by default.");
                        fldr.put("expanded", true);
                        fldr.put("children", builtIn);
                        fldr.put("schemaName", schemaPath);
                        respArray.put(fldr);
                    }

                    // Add any children schemas
                    for (UserSchema child : uschema.getUserSchemas())
                    {
                        SchemaKey childPath = new SchemaKey(schemaPath, child.getName());
                        JSONObject schemaProps = getSchemaProps(childPath, child);
                        respArray.put(schemaProps);
                    }
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
        schemaProps.put("text", PageFlowUtil.filter(schema.getName()));
        schemaProps.put("description", PageFlowUtil.filter(schema.getDescription()));
        schemaProps.put("qtip", PageFlowUtil.filter(schema.getDescription()));
        schemaProps.put("name", schema.getName());
        schemaProps.put("schemaName", schemaName);
        return schemaProps;
    }

    protected void addMoreLinkToList(SchemaKey schemaName, JSONArray list)
    {
        JSONObject props = new JSONObject();
        props.put("schemaName", schemaName);
        props.put("text", PageFlowUtil.filter("More..."));
        props.put("leaf", true);
        String description = "Only the first " + MAX_TABLES_TO_LIST +
                " queries are shown.  Click to view the full list in the main pane.";
        props.put("description", description);
        props.put("qtip", PageFlowUtil.filter(description));
        list.put(props);
    }


    protected void addQueryToList(SchemaKey schemaName, String qname, String description, JSONArray list)
    {
        JSONObject qprops = new JSONObject();
        qprops.put("schemaName", schemaName);
        qprops.put("queryName", qname);
        qprops.put("text", PageFlowUtil.filter(qname));
        qprops.put("leaf", true);
        if (null != description)
        {
            qprops.put("description", description);
            qprops.put("qtip", PageFlowUtil.filter(description));
        }
        list.put(qprops);
    }

    public static class Form
    {
        private String _node;
        private SchemaKey _schemaName;

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
    }
}
