<%
/*
 * Copyright (c) 2006-2013 LabKey Corporation
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
%>
<%@ page import="org.apache.commons.lang3.StringUtils"%>
<%@ page import="org.json.JSONObject"%>
<%@ page import="org.labkey.api.collections.CaseInsensitiveHashMap" %>
<%@ page import="org.labkey.api.query.DefaultSchema" %>
<%@ page import="org.labkey.api.query.QueryDefinition" %>
<%@ page import="org.labkey.api.query.UserSchema" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.Collections" %>
<%@ page import="java.util.Comparator" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.TreeMap" %>
<%@ page import="java.util.TreeSet" %>
<%@ page import="org.labkey.api.query.QueryService" %>
<%@ page import="org.labkey.api.query.CustomView" %>
<%@ page import="org.labkey.api.query.SchemaKey" %>
<%@ page extends="org.labkey.query.view.EditQueryPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    Map<String, String> schemaOptions = new TreeMap<String, String>(new Comparator<String>(){
        public int compare(String o1, String o2)
        {
            return o1.compareToIgnoreCase(o2);
        }
    });

    Map<String, Map<String, List<String>>> schemaTableNames = new CaseInsensitiveHashMap<Map<String, List<String>>>();
    DefaultSchema defSchema = DefaultSchema.get(getUser(), getContainer());

    for (SchemaKey schemaKey : defSchema.getUserSchemaPaths())
    {
        schemaOptions.put(schemaKey.toString(), schemaKey.toString());
        UserSchema schema = QueryService.get().getUserSchema(getUser(), getContainer(), schemaKey);
        Map<String, List<String>> tableNames = new CaseInsensitiveHashMap<List<String>>();

        for (String tableName : new TreeSet<String>(schema.getTableAndQueryNames(true)))
        {
            List<String> viewNames = new ArrayList<String>();
            viewNames.add(""); // default view

            QueryDefinition queryDef = schema.getQueryDefForTable(tableName);

            if (queryDef != null)
            {
                for (Map.Entry<String, CustomView> entry : queryDef.getCustomViews(getUser(), getViewContext().getRequest(), false).entrySet())
                {
                    String viewName = entry.getKey();
                    // Filter out hidden views
                    if (viewName != null)
                        viewNames.add(viewName);
                }
            }

            Collections.sort(viewNames, new Comparator<String>(){
                public int compare(String o1, String o2)
                {
                    return o1.compareToIgnoreCase(o2);
                }
            });

            tableNames.put(tableName, viewNames);
        }

        schemaTableNames.put(schemaKey.toString(), tableNames);
    }

    Map<String, String> pm = getWebPart().getPropertyMap();
    String strAllowChooseQuery = pm.get("allowChooseQuery");
    boolean allowChooseQuery = false;
    if (strAllowChooseQuery != null)
    {
        allowChooseQuery = Boolean.valueOf(strAllowChooseQuery);
    }
    String strAllowChooseView = pm.get("allowChooseView");
    boolean allowChooseView = true;
    if (strAllowChooseView != null)
    {
        allowChooseView = Boolean.valueOf(strAllowChooseView);
    }

    boolean querySelected = pm.get("queryName") != null && !"".equals(pm.get("queryName").trim());

    String btnBarPosition = StringUtils.defaultString(pm.get("buttonBarPosition"), "BOTH");
    
%>
<script type="text/javascript">
    var schemaInfos = <%= new JSONObject(schemaTableNames).toString() %>;
    function updateQueries(schemaName)
    {
        var tableNames = schemaInfos[schemaName];
        var querySelect = document.getElementById('queryName');

        querySelect.options.length = 0;

        var names = [];
        for (var opt in tableNames)
        {
            names.push(opt);
        }
        names.sort(function(a,b){
          var a1 = a.toString().toLowerCase();
          var b1 = b.toString().toLowerCase();

          if (a1 > b1)
             return 1
          if (a1 < b1)
             return -1
          return 0;
        });
        for (var i = 0; i <names.length; i++)
        {
            querySelect.options[querySelect.options.length] = new Option(names[i], names[i]);
        }

        updateViews();
    }
    function updateViews()
    {
        var schemaName = document.getElementById('schemaName').value;
        var queryName = document.getElementById('queryName').value;
        var viewNames = schemaInfos[schemaName][queryName];
        var viewSelect = document.getElementById('viewName');

        viewSelect.options.length = 0;

        if (viewNames == undefined)
            return;

        for (var i = 0; i < viewNames.length; i++)
        {
            var opt = viewNames[i];
            viewSelect.options[i] = new Option(opt == "" ? "<default view>" : opt, opt);
        }
    }
    function disableQuerySelect(disable)
    {
        document.getElementById('queryName').disabled = disable;
        document.getElementById('viewName').disabled = disable;
    }

</script>

<form name="frmCustomize" method="post" action="<%=h(_part.getCustomizePostURL(getViewContext()))%>">
    <table>
        <tr>
            <td class="labkey-form-label">Web Part Title:</td>
            <td><input type="text" name="title" size="40" value="<%=h(pm.get("title"))%>"></td>
        </tr>
        <tr>
            <td class="labkey-form-label">Schema:</td>
            <td>
                <select name="schemaName" id="schemaName"
                        title="Select a Schema Name"
                        onchange="updateQueries(this.value)">
                    <labkey:options value='<%=pm.get("schemaName")%>' map="<%=schemaOptions%>" />
                </select>
            </td>
        </tr>
        <tr>
            <td class="labkey-form-label" valign="top">Query and View:</td>
            <td>
                <table>
                    <tr>
                        <td><input type="radio" id="selectQueryList" name="selectQuery" value="false" <% if (!querySelected) { %> checked <% } %> onchange="disableQuerySelect(this.value == 'false');"/></td>
                        <td><label for="selectQueryList">Show the list of tables in this schema.</label></td>
                    </tr>
                    <tr>
                        <td><input type="radio" id="selectQueryContents" name="selectQuery" value="true" <% if (querySelected) { %> checked <% } %>  onchange="disableQuerySelect(this.value == 'false');"/></td>
                        <td><label for="selectQueryContents">Show the contents of a specific table and view.</label></td>
                    </tr>
                    <tr>
                        <td></td>
                        <td>
                            <select name="queryName" id="queryName"
                                    title="Select a Table Name" onchange="updateViews()"
                                    <% if (!querySelected) { %> disabled="true" <% } %>>
                                <%
                                Map<String, List<String>> tableNames = schemaTableNames.get(pm.get("schemaName"));
                                if (tableNames != null)
                                {
                                    for (String queryName : new TreeSet<String>(tableNames.keySet()))
                                    {
                                        %><option value="<%=h(queryName)%>" <%=queryName.equals(pm.get("queryName")) ? "selected" : ""%>><%=h(queryName)%></option><%
                                    }
                                }
                                %>
                            </select>
                            <br/>
                            <select name="viewName" id="viewName" title="Select a View Name" <%= querySelected ? "" : "disabled=true" %>>
                                <%
                                if (tableNames != null)
                                {
                                    List<String> viewNames = tableNames.get(pm.get("queryName"));
                                    if (viewNames != null)
                                    {
                                        for (String viewName : viewNames)
                                        {
                                            viewName = StringUtils.trimToEmpty(viewName);
                                            String value = StringUtils.defaultIfEmpty(viewName, "<default view>");
                                            %><option value="<%=h(viewName)%>" <%=viewName.equals(pm.get("viewName")) ? "selected" : ""%>><%=h(value)%></option><%
                                        }
                                    }
                                }
                                %>
                            </select>
                        </td>
                    </tr>
                </table>
            </td>
        </tr>
        <tr>
            <td class="labkey-form-label">Allow user to choose query?</td>
            <td>
                <select name="allowChooseQuery">
                    <option value="true"<%=allowChooseQuery ? " selected" : ""%>>Yes</option>
                    <option value="false"<%=allowChooseQuery ? "" : " selected"%>>No</option>
                </select>                
            </td>
        </tr>
        <tr>
            <td class="labkey-form-label">Allow user to choose view?</td>
            <td>
                <select name="allowChooseView">
                    <option value="true"<%=allowChooseView ? " selected" : ""%>>Yes</option>
                    <option value="false"<%=allowChooseView ? "" : " selected"%>>No</option>
                </select>
            </td>
        </tr>
        <tr>
            <td class="labkey-form-label">Button bar position:</td>
            <td>
                <select name="buttonBarPosition">
                    <option value="BOTH"<%="BOTH".equalsIgnoreCase(btnBarPosition) ? " selected" : ""%>>Both</option>
                    <option value="TOP"<%="TOP".equalsIgnoreCase(btnBarPosition) ? " selected" : ""%>>Top</option>
                    <option value="BOTTOM"<%="BOTTOM".equalsIgnoreCase(btnBarPosition) ? " selected" : ""%>>Bottom</option>
                    <option value="NONE"<%="NONE".equalsIgnoreCase(btnBarPosition) ? " selected" : ""%>>None</option>
                </select>
            </td>
        </tr>
        <tr>
            <td/>
            <td><labkey:button text="Submit" /></td>
        </tr>
    </table>
</form>
