/*
 * Copyright (c) 2009 LabKey Corporation
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
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.*;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.PageFlowUtil;
import org.springframework.validation.BindException;

import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

/**
 * Created by IntelliJ IDEA.
 * User: dave
 * Date: Sep 3, 2009
 * Time: 1:09:00 PM
 *
 * This class is used by the schema explorer query tree only
 */

@RequiresPermissionClass(ReadPermission.class)
public class GetSchemaQueryTreeAction extends ApiAction<GetSchemaQueryTreeAction.Form>
{

    public ApiResponse execute(Form form, BindException errors) throws Exception
    {
        JSONArray respArray = new JSONArray();
        Container container = getViewContext().getContainer();
        User user = getViewContext().getUser();
        DefaultSchema defSchema = DefaultSchema.get(user, container);

        if ("root".equals(form.getNode()))
        {
            //return list of schemas
            for (String name : defSchema.getUserSchemaNames())
            {
                QuerySchema schema = DefaultSchema.get(user, container).getSchema(name);
                if (null == schema)
                    continue;

                JSONObject schemaProps = new JSONObject();
                schemaProps.put("id", "s:" + name);
                schemaProps.put("text", PageFlowUtil.filter(name));
                schemaProps.put("description", PageFlowUtil.filter(schema.getDescription()));
                schemaProps.put("qtip", PageFlowUtil.filter(schema.getDescription()));
                schemaProps.put("schemaName", name);

                respArray.put(schemaProps);
            }

        }
        else
        {
            //node id is "s:<schema-name>"
            if (null != form.getNode() || form.getNode().startsWith("s:"))
            {
                String schemaName = form.getNode().substring(2);
                QuerySchema schema = defSchema.getSchema(schemaName);
                if (null != schema && schema instanceof UserSchema)
                {
                    UserSchema uschema = (UserSchema)schema;
                    JSONArray userDefined = new JSONArray();
                    JSONArray builtIn = new JSONArray();

                    //get built-in queries
                    List<String> queryNames = new ArrayList<String>(uschema.getVisibleTableNames());
                    Collections.sort(queryNames);
                    for (String qname : queryNames)
                    {
                        TableInfo tinfo = uschema.getTable(qname);
                        if (null == tinfo)
                            continue;
                        addQueryToList(schemaName, qname, tinfo.getDescription(), builtIn);
                    }

                    //get user-defined queries
                    Map<String,QueryDefinition> queryDefMap = QueryService.get().getQueryDefs(container, uschema.getSchemaName());
                    queryNames = new ArrayList<String>(queryDefMap.keySet());
                    Collections.sort(queryNames);
                    for (String qname : queryNames)
                    {
                        QueryDefinition qdef = queryDefMap.get(qname);
                        if (!qdef.isHidden())
                            addQueryToList(schemaName, qname, qdef.getDescription(), userDefined);
                    }

                    //group the user-defined and built-in queries into folders
                    if (userDefined.length() > 0)
                    {
                        JSONObject fldr = new JSONObject();
                        fldr.put("id", "s:" + schemaName + ":ud");
                        fldr.put("text", "user-defined queries");
                        fldr.put("qtip", "Custom queries created by you and those shared by others.");
                        fldr.put("expanded", true);
                        fldr.put("children", userDefined);
                        fldr.put("schemaName", schemaName);
                        respArray.put(fldr);
                    }

                    if (builtIn.length() > 0)
                    {
                        JSONObject fldr = new JSONObject();
                        fldr.put("id", "s:" + schemaName + ":bi");
                        fldr.put("text", "built-in queries");
                        fldr.put("qtip", "Queries that are part of the schema by default.");
                        fldr.put("expanded", true);
                        fldr.put("children", builtIn);
                        fldr.put("schemaName", schemaName);
                        respArray.put(fldr);
                    }
                }
            }
        }

        HttpServletResponse resp = getViewContext().getResponse();
        resp.setContentType("application/json");
        resp.getWriter().write(respArray.toString());

        return null;
    }

    protected void addQueryToList(String schemaName, String qname, String description, JSONArray list)
    {
        JSONObject qprops = new JSONObject();
        qprops.put("schemaName", schemaName);
        qprops.put("id", "q:" + qname);
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

        public String getNode()
        {
            return _node;
        }

        public void setNode(String node)
        {
            _node = node;
        }
    }
}
