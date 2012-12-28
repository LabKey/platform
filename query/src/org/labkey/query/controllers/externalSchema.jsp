<%
/*
 * Copyright (c) 2009-2012 LabKey Corporation
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
<%@ page import="org.labkey.query.persist.ExternalSchemaDef" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    QueryController.ExternalSchemaBean bean = (QueryController.ExternalSchemaBean)HttpView.currentModel();
    ExternalSchemaDef def = bean.getSchemaDef();
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
<style type="text/css">
    .systemSchemaStyleClass{padding-right:5px !important}
    .x-panel-body{background-color: transparent;}
</style>

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

// Admin can only choose from the data sources in the drop down.  Selecting a data source updates the schemas drop down below.
var dataSourceCombo = new Ext.form.ComboBox({fieldLabel:'Data Source', mode:'local', store:store, valueField:'value', displayField:'name', hiddenName:'dataSource', editable:false, triggerAction:'all', helpPopup:{title:'Data Source', html:'<%=bean.getHelpHTML("DataSource")%>'}, value:dataSources[<%=coreIndex%>][0]});
var includeLabel = new Ext.form.Label({text:'Show System Schemas', align:"middle", padding:"4"});
var includeSystemCheckBox = new LABKEY.ext.Checkbox({name:'includeSystem', id:'myincludeSystem', boxLabel:'Show System Schemas'});
// Admin can choose one of the schemas listed or type in their own (e.g., admin might want to use a system schema that we're filtering out).
var sourceSchemaCombo = new Ext.form.ComboBox({name:'sourceSchemaName', fieldLabel:'Source Schema Name', store:dataSources[<%=coreIndex%>][3], editable:true, triggerAction:'all', allowBlank:false, helpPopup:{title:'Database Schema Name', html:'<%=bean.getHelpHTML("SourceSchemaName")%>'}, value:<%=q(def.getSourceSchemaName())%>});
var userSchemaText = new Ext.form.TextField({name:'userSchemaName', fieldLabel:'Schema Name', allowBlank:false, helpPopup:{title:'Schema Name', html:'<%=bean.getHelpHTML("UserSchemaName")%>'}, value:<%=q(def.getUserSchemaName())%>});
var editableCheckBox = new LABKEY.ext.Checkbox({name:'editable', id:'myeditable', fieldLabel:'Editable', helpPopup:{title:'Editable', html:'<%=bean.getHelpHTML("Editable")%>'}});
var indexableCheckBox = new LABKEY.ext.Checkbox({name:'indexable', /*id:'myeditable',*/ fieldLabel:'Index Schema Meta Data', helpPopup:{title:'Index Schema Meta Data', html:'<%=bean.getHelpHTML("Indexable")%>'}, checked:<%=def.isIndexable()%>});
var metaDataTextArea = new Ext.form.TextArea({name:'metaData', fieldLabel:'Meta Data', width:800, height:400, resizable:true, autoCreate:{tag:"textarea", style:"font-family:'Courier'", autocomplete:"off", wrap:"off"}, helpPopup:{title:'Meta Data', html:'<%=bean.getHelpHTML("MetaData")%>'}, value:<%=PageFlowUtil.jsString(def.getMetaData())%>});
var tableText = new Ext.form.TextField({name:'tables', hidden:true});

// create the data store
var tableStore = new Ext.data.JsonStore({
    url: 'getTables.api',
    autoDestroy: true,
    storeId: 'tables',
    idProperty: 'table',
    root: 'rows',
    fields: ['table']
});

var selModel = new Ext.grid.CheckboxSelectionModel();
selModel.addListener('rowselect', updateTableTitle);
selModel.addListener('rowdeselect', updateTableTitle);

var initialTables = '<%=def.getTables()%>';

// create the table grid
var grid = new Ext.grid.GridPanel({
    fieldLabel:'Tables',
    helpPopup:{title:'Tables', html:'<%=bean.getHelpHTML("Tables")%>'},
    title:'&nbsp;',
    store: tableStore,
    columns: [
        selModel,
        {id:'table', width: 160, sortable: false, dataIndex: 'table'}
    ],
    stripeRows: true,
    collapsed: true,
    collapsible: true,
    autoExpandColumn: 'table',
    autoHeight: true,
    width: 600,
    selModel: selModel
});

var DatabaseSchemaNamePanel = Ext.extend(Ext.Panel, {
    initComponent: function() {
        this.fieldLabel = 'Database Schema Name';
        this.layout = 'table';
        this.layoutConfig = {columns:2};
        this.items = [sourceSchemaCombo, includeSystemCheckBox];
        this.border = false;
        this.helpPopup = {title:'Source Schema Name', html:'<%=bean.getHelpHTML("SourceSchemaName")%>'};
        this.defaults = {cellCls:'systemSchemaStyleClass'};
        DatabaseSchemaNamePanel.superclass.initComponent.apply(this, arguments);
    }
});

var f = new LABKEY.ext.FormPanel({
    width:955,
    labelWidth:150,
    border:false,
    standardSubmit:true,
    items:[
        dataSourceCombo,
        new DatabaseSchemaNamePanel(),
        userSchemaText,
        editableCheckBox,
        indexableCheckBox,
        grid,
        metaDataTextArea,
        tableText
    ],
    buttons:[{text:'<%=(bean.isInsert() ? "Create" : "Update")%>', type:'submit', handler:submit}, <%=bean.isInsert() ? "" : "{text:'Delete', handler:function() {document.location = " + q(bean.getDeleteURL().toString()) + "}}, "%>{text:'Cancel', handler:function() {document.location = <%=q(bean.getReturnURL().toString())%>;}}],
    buttonAlign:'left'
});

Ext.onReady(function()
{
    f.render('form');
    new Ext.Resizable(metaDataTextArea.el, {handles:'se', wrap:true});
    dataSourceCombo.on('select', dataSourceCombo_onSelect);
    includeSystemCheckBox.on('check', includeSystemCheckBox_onCheck);
    sourceSchemaCombo.on('select', sourceSchemaCombo_onSelect);
    grid.on('expand', updateTableTitle);
    grid.on('collapse', updateTableTitle);
    initEditable(<%=def.isEditable()%>, <%=initialScope.getSqlDialect().isEditable()%>);
    loadTables();
});

// Populate the "Database Schema Name" combo box with new data source's schemas
function dataSourceCombo_onSelect()
{
    userSchemaText.setValue("");
    var dataSourceIndex = store.find("value", dataSourceCombo.getValue());
    sourceSchemaCombo.store.loadData(dataSources[dataSourceIndex][schemaIndex]);
    sourceSchemaCombo.setValue("");
    sourceSchemaCombo_onSelect();  // reset all fields that depend on database schema name
}

function includeSystemCheckBox_onCheck()
{
    schemaIndex = includeSystemCheckBox.getValue() ? 4 : 3;
    dataSourceCombo_onSelect();
}

// Default to schema name = database schema name, editable false, editable disabled for non-editable scopes, meta data blank
function sourceSchemaCombo_onSelect()
{
    userSchemaText.setValue(sourceSchemaCombo.getValue());
    var dataSourceIndex = store.find("value", dataSourceCombo.getValue());
    initEditable(false, dataSources[dataSourceIndex][2]);
    metaDataTextArea.setValue("");
    loadTables();
}

function initEditable(value, enabled)
{
    editableCheckBox.setValue(value);
    editableCheckBox.setDisabled(!enabled);
}

function loadTables()
{
    var dataSource = dataSourceCombo.getValue();
    var schemaName = sourceSchemaCombo.getValue();

    // dataSource and/or schemaName could be empty, but action handles this
    tableStore.load({
        params: {dataSource: dataSource, schemaName: schemaName},
        callback: tablesLoaded
    });
}

function tablesLoaded()
{
    if ('*' == initialTables)
    {
        grid.selModel.selectAll();
    }
    else
    {
        var tableNames = initialTables.split(',');
        var recordArray = [];

        for (var i = 0; i < tableNames.length; i++)
            recordArray.push(tableStore.getById(tableNames[i]));

        grid.selModel.selectRecords(recordArray);
        initialTables = '*';
    }

    updateTableTitle();
}

function submit()
{
    if (grid.selModel.getCount() == tableStore.getCount())
    {
        tableText.setValue('*');
    }
    else
    {
        var value = '';
        var sep = '';
        grid.selModel.each(function(record) {
                value = value + sep + record.get('table');
                sep = ',';
            });
        tableText.setValue(value);
    }

    f.getForm().submit();
}

function updateTableTitle()
{
    var selectedCount = grid.selModel.getCount();
    var title = "&nbsp;";

    if (sourceSchemaCombo.getValue() != '')
    {
        if (selectedCount == tableStore.getCount())
        {
            title = "All (" + selectedCount + ") tables";
        }
        else
        {
            if (0 == selectedCount)
                title = "No tables";
            else if (1 == selectedCount)
                title = "1 table";
            else
                title = selectedCount + " tables";
        }

        title += " in this schema will be published";

        if (grid.collapsed)
            title += "; click + to change the published tables";
    }

    grid.setTitle(title);
}
</script>
