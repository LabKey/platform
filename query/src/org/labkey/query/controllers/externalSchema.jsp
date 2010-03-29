<%
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
%>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page import="org.labkey.api.data.DbScope" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.query.controllers.QueryController" %>
<%@ page import="org.labkey.query.persist.DbUserSchemaDef" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    QueryController.ExternalSchemaBean bean = (QueryController.ExternalSchemaBean)HttpView.currentModel();
    DbUserSchemaDef def = bean.getSchemaDef();
    DbScope initialScope = null;

    try
    {
        initialScope = DbScope.getDbScope(def.getDataSource());
    }
    catch (Exception e)
    {
    }

    if (null == initialScope)
    {
        initialScope = DbScope.getLabkeyScope();
    }
%>
<labkey:errors/>
<div id="form"></div>

<script type="text/javascript">
    // TODO: js encode strings

    Ext.QuickTips.init();

    var dataSources = [
<%
    int coreIndex = 0;
    int i = 0;
    String sep = "";

    for (DbScope scope : bean.getScopes())
    {
        out.print(sep);
        out.print("        [");
        out.print("'" + scope.getDataSourceName() + "', ");
        out.print("'" + scope.getDisplayName() + "', ");
        out.print(scope.getSqlDialect().isEditable() + ", [");

        String sep2 = "";

        for (String schemaName : bean.getSchemaNames(scope, false))
        {
            out.print(sep2);
            out.print("'" + schemaName + "'");
            sep2 = ",";
        }

        out.print("], [");

        sep2 = "";

        for (String schemaName : bean.getSchemaNames(scope, true))
        {
            out.print(sep2);
            out.print("'" + schemaName + "'");
            sep2 = ",";
        }

        out.print("]]");

        if (scope == initialScope)
            coreIndex = i;

        sep = ",\n";
        i++;
    }
%>
    ];

var store = new Ext.data.SimpleStore({
    fields:['value', 'name', 'editable', 'schemas', 'schemasWithSystem'],
    data:dataSources
});

var schemaIndex = 3;
var dataSourceCombo;
var dbSchemaCombo;
var userSchemaText;
var editableCheckBox;
var metaDataTextArea;

var f = new LABKEY.ext.FormPanel({
        width:950,
        labelWidth:145,
        border:false,
        standardSubmit:true,
        items:[
            // Admin can only choose from the data sources in the drop down.  Selecting a data source updates the schemas drop down below.
            dataSourceCombo = new Ext.form.ComboBox({fieldLabel:'Data Source', mode:'local', store:store, valueField:'value', displayField:'name', hiddenName:'dataSource', editable:false, triggerAction:'all', helpPopup:{title:'Data Source', html:'<%=bean.getHelpHTML("DataSource")%>'}, value:dataSources[<%=coreIndex%>][0]}),
            includeSystemCheckBox = new LABKEY.ext.Checkbox({name:'includeSystem', id:'myincludeSystem', fieldLabel:'Include System Schemas', helpPopup:{title:'Include System Schemas', html:'<%=bean.getHelpHTML("includeSystem")%>'}}),
            // Admin can choose one of the schemas listed or type in their own (e.g., admin might want to use a system schema that we're filtering out).
            dbSchemaCombo = new Ext.form.ComboBox({name:'dbSchemaName', fieldLabel:'Database Schema Name', store:dataSources[<%=coreIndex%>][3], editable:true, triggerAction:'all', allowBlank:false, helpPopup:{title:'Database Schema Name', html:'<%=bean.getHelpHTML("DbSchemaName")%>'}, value:<%=q(h(def.getDbSchemaName()))%>}),
            userSchemaText = new Ext.form.TextField({name:'userSchemaName', fieldLabel:'Schema Name', allowBlank:false, helpPopup:{title:'Schema Name', html:'<%=bean.getHelpHTML("UserSchemaName")%>'}, value:<%=q(h(def.getUserSchemaName()))%>}),
            editableCheckBox = new LABKEY.ext.Checkbox({name:'editable', id:'myeditable', fieldLabel:'Editable', helpPopup:{title:'Editable', html:'<%=bean.getHelpHTML("Editable")%>'}}),
            metaDataTextArea = new Ext.form.TextArea({name:'metaData', fieldLabel:'Meta Data', width:800, height:400, resizable:true, autoCreate:{tag:"textarea", style:"font-family:'Courier'", autocomplete:"off", wrap:"off"}, helpPopup:{title:'Meta Data', html:'<%=bean.getHelpHTML("MetaData")%>'}, value:<%=PageFlowUtil.jsString(def.getMetaData())%>})
        ],
        buttons:[{text:'<%=(bean.isInsert() ? "Create" : "Update")%>', type:'submit', handler:function() {f.getForm().submit();}}, <%=bean.isInsert() ? "" : "{text:'Delete', handler:function() {document.location = " + q(bean.getDeleteURL().toString()) + "}}, "%>{text:'Cancel', handler:function() {document.location = <%=q(bean.getReturnURL().toString())%>;}}],
        buttonAlign:'left'
    });

Ext.onReady(function()
{
    f.render('form');
    new Ext.Resizable(metaDataTextArea.el, {handles:'se', wrap:true});
    dataSourceCombo.on('select', dataSourceCombo_onSelect);
    includeSystemCheckBox.on('check', includeSystemCheckBox_onCheck);
    dbSchemaCombo.on('select', dbSchemaCombo_onSelect);
    initEditable(<%=def.isEditable()%>, <%=initialScope.getSqlDialect().isEditable()%>);
});

// Populate the "Database Schema Name" combobox with new data source's schemas
function dataSourceCombo_onSelect()
{
    userSchemaText.setValue("");
    var dataSourceIndex = store.find("value", dataSourceCombo.getValue());
    dbSchemaCombo.store.loadData(dataSources[dataSourceIndex][schemaIndex]);
    dbSchemaCombo.setValue("");
    dbSchemaCombo_onSelect();  // reset all fields that depend on database schema name
}

function includeSystemCheckBox_onCheck()
{
    schemaIndex = includeSystemCheckBox.getValue() ? 4 : 3;
    dataSourceCombo_onSelect();
}

// Default to schema name = database schema name, editable false, editable disabled for non-editable scopes, meta data blank
function dbSchemaCombo_onSelect()
{
    userSchemaText.setValue(dbSchemaCombo.getValue());
    var dataSourceIndex = store.find("value", dataSourceCombo.getValue());
    initEditable(false, dataSources[dataSourceIndex][2]);
    metaDataTextArea.setValue("");
}

function initEditable(value, enabled)
{
    editableCheckBox.setValue(value);
    editableCheckBox.setDisabled(!enabled);
}
</script>
