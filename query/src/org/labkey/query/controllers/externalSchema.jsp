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
<%@ page import="org.labkey.api.data.DbScope" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.query.controllers.QueryControllerSpring" %>
<%@ page import="org.labkey.query.persist.DbUserSchemaDef" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    QueryControllerSpring.ExternalSchemaBean bean = (QueryControllerSpring.ExternalSchemaBean)HttpView.currentModel();
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
        out.print("'" + getDisplayName(scope.getDataSourceName()) + "', ");
        out.print(scope.getSqlDialect().isEditable() + ", [");

        String sep2 = "";

        for (String schemaName : bean.getSchemaNames(scope))
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
    fields:['value', 'name', 'editable', 'schemas'],
    data:dataSources
});

//var values={dbSchemaName:'guest'};
var dataSourceCombo;
var dbSchemaCombo;
var userSchemaText;
var editableCheckBox;

var f = new LABKEY.ext.FormPanel({
//        values:values,
        border:false,
        standardSubmit:true,
        items:[
            // Admin can only choose from the data sources in the drop down.  Selecting a data source updates the schemas drop down below.
            dataSourceCombo = new Ext.form.ComboBox({fieldLabel:'Data Source', mode:'local', store:store, valueField:'value', displayField:'name', hiddenName:'dataSource', editable:false, triggerAction:'all', value:dataSources[<%=coreIndex%>][0]}),
            // Admin can choose one of the schemas listed or type in their own (e.g., admin might want to use a system schema that we're filtering out). 
            dbSchemaCombo = new Ext.form.ComboBox({name:'dbSchemaName', fieldLabel:'Database Schema Name', xtype:'combo', store:dataSources[<%=coreIndex%>][3], value:<%=q(h(def.getDbSchemaName()))%>, editable:true, triggerAction:'all'}),
            userSchemaText = new Ext.form.TextField({name:'userSchemaName', fieldLabel:'Schema Name', xtype:'textfield', value:<%=q(h(def.getUserSchemaName()))%>}),
            editableCheckBox = new Ext.form.Checkbox({name:'editable', id:'myeditable', fieldLabel:'Editable', xtype:'checkbox'}),
            {name:'@editable', xtype:'hidden'},
            metaDataTextArea = new Ext.form.TextArea({name:'metaData', fieldLabel:'Meta Data', xtype:'textarea', value:<%=PageFlowUtil.jsString(def.getMetaData())%>})
        ],
        buttons:[{text:'<%=(bean.isInsert() ? "Create" : "Update")%>', type:'submit', handler:function() {f.getForm().submit();}}, <%=bean.isInsert() ? "" : "{text:'Delete', handler:function() {document.location = " + q(bean.getDeleteURL().toString()) + "}}, "%>{text:'Cancel', handler:function() {document.location = <%=q(bean.getReturnURL().toString())%>;}}],
        buttonAlign:'left'
    });

Ext.onReady(function()
{
    f.render('form');
    dataSourceCombo.on('select', dataSourceCombo_onSelect);
    dbSchemaCombo.on('select', dbSchemaCombo_onSelect);
    initEditable(<%=def.isEditable()%>, <%=initialScope.getSqlDialect().isEditable()%>);
});

// Populate the "Database Schema Name" combobox with new data source's schemas
function dataSourceCombo_onSelect()
{
    userSchemaText.setValue("");
    var dataSourceIndex = store.find("value", dataSourceCombo.getValue());
    dbSchemaCombo.store.loadData(dataSources[dataSourceIndex][3]);
    dbSchemaCombo.setValue("");
    dbSchemaCombo_onSelect();  // reset all fields that depend on database schema name
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
<%!
    // Strip off "DataSource" to create friendly name.  TODO: Add UI to allow site admin to add friendly name to each data source.
    private String getDisplayName(String dsName)
    {
        if (dsName.endsWith("DataSource"))
            dsName = dsName.substring(0, dsName.length() - 10);

        return dsName;
    }
%>