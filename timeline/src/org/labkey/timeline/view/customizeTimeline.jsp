<%
/*
 * Copyright (c) 2008-2016 LabKey Corporation
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
<%@ page import="org.apache.commons.beanutils.BeanUtils" %>
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="org.json.JSONObject" %>
<%@ page import="org.labkey.api.collections.CaseInsensitiveHashMap" %>
<%@ page import="org.labkey.api.query.DefaultSchema" %>
<%@ page import="org.labkey.api.query.QueryDefinition" %>
<%@ page import="org.labkey.api.query.QueryService"%>
<%@ page import="org.labkey.api.query.UserSchema" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.Portal" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.timeline.TimelineSettings" %>
<%@ page import="java.util.LinkedList" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.TreeMap" %>
<%@ page import="java.util.TreeSet" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("Ext3");
    }
%>
<%
    HttpView<Portal.WebPart> me = (HttpView<Portal.WebPart>) HttpView.currentView();
    Portal.WebPart part = me.getModelBean();
    ViewContext ctx = getViewContext();
    TimelineSettings settings = new TimelineSettings();
    BeanUtils.populate(settings, part.getPropertyMap());

%>
This webpart displays data from a query, list or dataset in a timeline format.<br>
A start date and title are required for each field. Note that "lookup" fields will be displayed using the key value. To show
a display value, select the display value from the linked table.
<br>

<%
    Map<String, String> schemaOptions = new TreeMap<>();

    Map<String, Map<String, List<String>>> schemaTableNames = new CaseInsensitiveHashMap<>();
    DefaultSchema defSchema = DefaultSchema.get(getUser(), getContainer());
    for (String name : defSchema.getUserSchemaNames(false))
    {
        schemaOptions.put(name, name);
        UserSchema schema = QueryService.get().getUserSchema(getUser(), getContainer(), name);
        Map<String, List<String>> tableNames = new CaseInsensitiveHashMap<>();
        for (String tableName : new TreeSet<>(schema.getTableAndQueryNames(true)))
        {
            List<String> viewNames = new LinkedList<>();
            viewNames.add(""); // default view

            QueryDefinition queryDef = schema.getQueryDefForTable(tableName);
            if (queryDef != null)
            {
                viewNames.addAll(queryDef.getCustomViews(getUser(), ctx.getRequest(), false, false).keySet());
            }

            tableNames.put(tableName, viewNames);
        }
        schemaTableNames.put(name, tableNames);
    }

%>
<script type="text/javascript">
var settings = {
    schemaName:<%=q(settings.getSchemaName())%>,
    queryName:  <%=q(settings.getQueryName())%>,
    viewName:  <%=q(settings.getViewName())%>,
    startField: <%=q(settings.getStartField())%>,
    endField: <%=q(settings.getEndField())%>,
    titleField: <%=q(settings.getTitleField())%>,
    descriptionField: <%=q(settings.getDescriptionField())%>
};

var schemaInfos = <%= new JSONObject(schemaTableNames).toString() %>;

function updateQueries(schemaName)
{
    var tableNames = schemaInfos[schemaName];
    var querySelect = document.getElementById('queryName');

    settings.schemaName = schemaName;
    querySelect.options.length = 0;
    for (var opt in tableNames)
    {
        querySelect.options[querySelect.options.length] = new Option(opt, opt);
    }
    updateViews(querySelect.value);
}

function updateViews(queryName)
{
    settings.queryName = queryName;
    var tableName = document.getElementById('schemaName').value;
    var viewNames = schemaInfos[tableName][queryName];
    var viewSelect = document.getElementById('viewName');

    viewSelect.options.length = 0;
    for (var i = 0; i < viewNames.length; i++)
    {
        var opt = viewNames[i];
        viewSelect.options[i] = new Option(opt == "" ? "<default view>" : opt, opt);
    }

    updateFields(settings.schemaName, settings.queryName, settings.viewName);
}

function updateFields(schemaName, queryName, viewName)
{
    if (!schemaName || !queryName)
        clearFieldPickers();
    else
        LABKEY.Query.selectRows({schemaName:schemaName, queryName:queryName, viewName:viewName, maxRows:1, success:populateFieldPickers});
}

var fieldPickers = [
    {id:"startField", dataType:"date", allowBlank:false},
    {id:"endField", dataType:"date", allowBlank:true},
    {id:"titleField", allowBlank:true},
    {id:"descriptionField", allowBlank:true}];

function populateFieldPickers(data)
{
    var fields = data.metaData.fields;

    function populatePicker(pickerId, selectedValue, dataType, allowBlank)
    {
        var elem = document.getElementById(pickerId);
        elem.options.length = 0;
        var selectedItem = 0;
        if (allowBlank)
            elem.options[0] = new Option("", "", false, null == selectedValue || "" == selectedValue);
        for (var i = 0; i < fields.length; i++)
        {
            var field = fields[i];
            if (dataType && dataType != field.type)
                continue;

            if (selectedValue == field.name)
                selectedItem = elem.options.length;

            elem.options[elem.options.length] = new Option(field.name,  field.name);
        }
        elem.selectedIndex = selectedItem;

    }


    for (var i = 0; i < fieldPickers.length; i++)
    {
        var id = fieldPickers[i].id;
        populatePicker(id, settings[id], fieldPickers[i].dataType, fieldPickers[i].allowBlank);
    }
}

function clearFieldPickers()
{
    for (var i = 0; i < fieldPickers.length; i++)
        document.getElementById(fieldPickers[i].id).length = 0;
}

Ext.onReady(function()
{
    updateFields(settings.schemaName, settings.queryName, settings.viewName);
});
</script>
<labkey:form name="frmCustomize" method="post" action="<%=part.getCustomizePostURL(ctx).getEncodedLocalURIString()%>">
    <table>
        <tr>
            <td class="ms-searchform">Web Part Title:</td>
            <td><input type="text" name="webPartTitle" size="40" value="<%=h(settings.getWebPartTitle())%>"></td>
        </tr>
        <tr>
            <td class="ms-searchform">Schema:</td>
            <td>
                <select name="schemaName" id="schemaName"
                        title="Select a Schema Name"
                        onchange="updateQueries(this.value);">
                    <labkey:options value="<%=settings.getSchemaName()%>" map="<%=schemaOptions%>" />
                </select>
            </td>
        </tr>
        <tr>
            <td class="ms-searchform" valign="top">Query and View:</td>
            <td>
                            <select name="queryName" id="queryName"
                                    title="Select a Table Name" onchange="updateViews(this.value);">
                                <%
                                Map<String, List<String>> tableNames = schemaTableNames.get(settings.getSchemaName());
                                if (tableNames != null)
                                {
                                    for (String queryName : new TreeSet<>(tableNames.keySet()))
                                    {
                                        %><option value="<%=h(queryName)%>"<%=selected(queryName.equals(settings.getQueryName()))%>><%=h(queryName)%></option><%
                                    }
                                }
                                %>
                            </select>
                            <br/>
                            <select name="viewName" id="viewName"
                                    title="Select a View Name" onchange="updateFields(this.value);">
                                <%
                                if (tableNames != null)
                                {
                                    List<String> viewNames = tableNames.get(settings.getQueryName());
                                    if (viewNames != null)
                                    {
                                        for (String viewName : viewNames)
                                        {
                                            viewName = StringUtils.trimToEmpty(viewName);
                                            String value = viewName.equals("") ? "<default view>" : viewName;
                                            %><option value="<%=h(viewName)%>"<%=selected(viewName.equals(settings.getViewName()))%>><%=h(value)%></option><%
                                        }
                                    }
                                }
                                %>
                            </select>
            </td>
        </tr>
        <tr>
            <td>Start:</td>
            <td>
                <select name="startField" id="startField" onchange="settings[this.id] = this.value;"></select>
            </td>
        </tr>
        <tr>
            <td>End:</td>
            <td>
                <select name="endField" id="endField" onchange="settings[this.id] = this.value;"></select>
            </td>
        </tr>
        <tr>
            <td>Title:</td>
            <td>
                <select name="titleField" id="titleField" onchange="settings[this.id] = this.value;"></select>
            </td>
        </tr>
        <tr>
            <td>Description:</td>
            <td>
                <select name="descriptionField" id="descriptionField" onchange="settings[this.id] = this.value;"></select>
            </td>
        </tr>
        <tr>
            <td/>
            <td><labkey:button text="Submit" /></td>
        </tr>
    </table>
</labkey:form>
