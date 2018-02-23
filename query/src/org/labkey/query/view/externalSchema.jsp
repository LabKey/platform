<%
/*
 * Copyright (c) 2009-2017 LabKey Corporation
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
<%@ page import="org.json.JSONArray" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.data.xml.externalSchema.TemplateSchemaType" %>
<%@ page import="org.labkey.query.controllers.QueryController.BaseExternalSchemaBean" %>
<%@ page import="org.labkey.query.controllers.QueryController.DataSourceInfo" %>
<%@ page import="org.labkey.query.persist.AbstractExternalSchemaDef" %>
<%@ page import="org.labkey.query.persist.AbstractExternalSchemaDef.SchemaType" %>
<%@ page import="org.labkey.query.persist.ExternalSchemaDef" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.Arrays" %>
<%@ page import="java.util.Collection" %>
<%@ page import="org.labkey.query.controllers.QueryController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("clientapi/ext3");
        dependencies.add("internal/jQuery");
    }
%>
<%
    Container c = getContainer();
    QueryController.ExternalSchemaBean bean = (QueryController.ExternalSchemaBean) HttpView.currentModel();
    AbstractExternalSchemaDef def = bean.getSchemaDef();
    DataSourceInfo initialSource = bean.getInitialSource();

    boolean isExternal = def instanceof ExternalSchemaDef;
%>
<style type="text/css">
    .systemSchemaStyleClass{padding-right:5px !important}
    .x-panel-body{background-color: transparent;}
    .tooltip{opacity: 1; width: 500px;}
    label{font-weight: normal;}
</style>

<labkey:errors/>
<div id="form"></div>

<script type="text/javascript">
+function($){
<%
    int coreIndex = 0;
    int i = 0;

    JSONArray dataSourcesJson = new JSONArray();
    Collection<DataSourceInfo> sources = bean.getSources();
    for (DataSourceInfo source : sources)
    {
        JSONArray sourceJson = new JSONArray();
        sourceJson.put(source.sourceName);
        sourceJson.put(source.displayName);
        sourceJson.put(source.editable);
        sourceJson.put(bean.getSchemaNames(source, false));
        sourceJson.put(bean.getSchemaNames(source, true));
        dataSourcesJson.put(sourceJson);

        if (source.sourceName.equals(initialSource.sourceName))
            coreIndex = i;
        i++;
    }

    String initialTemplateName = bean.getSchemaDef().getSchemaTemplate();
    TemplateSchemaType initialTemplate = bean.getSchemaDef().lookupTemplate(c);
%>
var dataSources = <%=text(dataSourcesJson.toString())%>;
var initialDataSourceIndex = <%=coreIndex%>;

var dataSourceStore = new Ext.data.SimpleStore({
    fields:['value', 'name', 'editable', 'schemas', 'schemasWithSystem'],
    data:dataSources
});

// create the tables data store
var tablesStore = new Ext.data.JsonStore({
    url: 'query-getTables.api',
    autoDestroy: true,
    storeId: 'tablesStore',
    idProperty: 'table',
    root: 'rows',
    fields: ['table']
});

// create the templates data store
var templatesStore = new Ext.data.JsonStore({
    autoload: false,
    autoDestroy: true,
    storeId: 'templatesStore',
    idProperty: 'name',
    root: 'templates',
    fields: ['name', 'sourceSchemaName', 'tables', 'metadata'],
    listeners: {
        load: templatesLoaded
    }
});

var schemaType = <%=q(isExternal ? SchemaType.external.name() : SchemaType.linked.name())%>;
var external = <%=isExternal%>;

var schemaIndex = 3;

schemaType = new Ext.form.Hidden({name:'schemaType', value:schemaType});
var userSchemaText = new Ext.form.TextField({name:'userSchemaName', fieldLabel:'Schema Name', allowBlank:false, helpPopup:{title:'Schema Name', html:<%=PageFlowUtil.qh(bean.getHelpHTML("UserSchemaName"))%>}, value:<%=q(def.getUserSchemaName())%>});

// Admin can only choose from the data sources in the drop down.  Selecting a data source updates the schemas drop down below.
var dataSourceCombo = new Ext.form.ComboBox({
    fieldLabel:external ? 'Data Source' : 'Source Container',
    mode:'local',
    store:dataSourceStore,
    valueField:'value',
    displayField:'name',
    hiddenName:'dataSource',
    editable:false,
    triggerAction:'all',
    value:dataSources[initialDataSourceIndex][0]
});

var templateComboBox = new LABKEY.ext.ComboBox({
    name:'schemaTemplate',
    fieldLabel:'Schema Template',
    store: templatesStore,
    mode: 'local',
    triggerAction: 'all',
    editable: false,
    autoSelect: true,
    valueField:'name',
    displayField:'name',
    hidden:external,
    helpPopup:{title:'Schema Template', html:<%=PageFlowUtil.qh(bean.getHelpHTML("SchemaTemplate"))%>},
    value: <%=q(initialTemplateName)%>
});

var includeLabel = new Ext.form.Label({text:'Show System Schemas', align:"middle", padding:"4"});
var includeSystemCheckBox = new LABKEY.ext.Checkbox({
    name:'includeSystem',
    id:'myincludeSystem',
    boxLabel:'Show System Schemas',
    disabled:<%=initialTemplate != null%>
});

// Admin can choose one of the schemas listed or type in their own (e.g., admin might want to use a system schema that we're filtering out).
var sourceSchemaCombo = new Ext.form.ComboBox({
    name:'sourceSchemaName',
    fieldLabel:external ? 'Database Schema Name' : 'LabKey Schema Name',
    store:dataSources[initialDataSourceIndex][3],
    editable:true,
    triggerAction:'all',
    helpPopup:{title:'Source Schema Name',
    html:<%=PageFlowUtil.qh(bean.getHelpHTML("SourceSchemaName"))%>},
    value:<%=q(def.getSourceSchemaName() != null ? def.getSourceSchemaName() : (initialTemplate != null ? initialTemplate.getSourceSchemaName() : ""))%>,
    disabled:<%=initialTemplate != null%>,
    tpl: '<tpl for="."><div class="x-combo-list-item">{field1:htmlEncode}</div></tpl>'
});

if (external)
{
    var editableCheckBox = new LABKEY.ext.Checkbox({
        name:'editable',
        id:'myeditable',
        fieldLabel:'Editable',
        helpPopup:{
            title:'Editable',
            html:<%=PageFlowUtil.qh(bean.getHelpHTML("Editable"))%>
        }
    });
    var indexableCheckBox = new LABKEY.ext.Checkbox({
        name:'indexable',
        fieldLabel:'Index Schema Meta Data',
        helpPopup:{
            title:'Index Schema Meta Data',
            html:<%=PageFlowUtil.qh(bean.getHelpHTML("Indexable"))%>
        },
        checked:<%=def.isIndexable()%>
    });
    var fastCacheRefreshCheckBox = new LABKEY.ext.Checkbox({
        name:'fastCacheRefresh',
        fieldLabel:'Fast Cache Refresh',
        helpPopup:{
            title:'Fast Cache Refresh',
            html:<%=PageFlowUtil.qh(bean.getHelpHTML("FastCacheRefresh"))%>
        },
        checked:<%=def.isFastCacheRefresh()%>
    });
}

var metaDataTextArea = new Ext.form.TextArea({
    name:'metaData',
    fieldLabel:'Meta Data',
    width:800, height:400,
    resizable:true,
    autoCreate:{tag:"textarea", style:"font-family:'Courier'", autocomplete:"off", wrap:"off"},
    helpPopup:{title:'Meta Data', html:<%=PageFlowUtil.qh(bean.getHelpHTML("MetaData"))%>},
    value: <%=q(def.getMetaData() != null ? def.getMetaData() : (initialTemplate != null && initialTemplate.getMetadata() != null ? initialTemplate.getMetadata().toString() : ""))%>,
    disabled:<%=initialTemplate != null%>
});

var tableText = new Ext.form.TextField({name:'tables', hidden:true});

var selModel = new Ext.grid.CheckboxSelectionModel();
selModel.addListener('rowselect', updateTableTitle);
selModel.addListener('rowdeselect', updateTableTitle);

<%
ArrayList<String> tables = new ArrayList<>();
if (def.getTables() != null && def.getTables().length() > 0)
{
    tables.addAll(Arrays.asList(def.getTables().split(",")));
}
else if (initialTemplate != null && initialTemplate.isSetTables())
{
    tables.addAll(Arrays.asList(initialTemplate.getTables().getTableNameArray()));
}
%>
var initialTables = <%=text(new JSONArray(tables).toString())%>;

// create the table grid
var grid = new Ext.grid.GridPanel({
    fieldLabel:'Tables',
    helpPopup:{title:'Tables', html:<%=PageFlowUtil.qh(bean.getHelpHTML("Tables"))%>},
    title:'&nbsp;',
    store: tablesStore,
    columns: [
        selModel,
        {id: 'table', header: "Table", width: 160, sortable: false, dataIndex: 'table', renderer: 'htmlEncode'}
    ],
    stripeRows: true,
    collapsed: true,
    collapsible: true,
    autoExpandColumn: 'table',
    autoHeight: true,
    width: 600,
    selModel: selModel,
    disabled: <%=initialTemplate != null%>
});

var DatabaseSchemaNamePanel = Ext.extend(Ext.Panel, {
    initComponent: function() {
        this.fieldLabel = external ? 'Database Schema Name' : 'LabKey Schema Name';
        this.layout = 'table';
        this.layoutConfig = {columns:2};
        this.items = [sourceSchemaCombo, includeSystemCheckBox];
        this.border = false;
        this.helpPopup = {title:'Source Schema Name', html:<%=PageFlowUtil.qh(bean.getHelpHTML("SourceSchemaName"))%>};
        this.defaults = {cellCls:'systemSchemaStyleClass'};
        DatabaseSchemaNamePanel.superclass.initComponent.apply(this, arguments);
    }
});

var f = new LABKEY.ext.FormPanel({
    width:955,
    labelWidth:170,
    border:false,
    standardSubmit:true,
    items:[
        schemaType,
        userSchemaText,
        dataSourceCombo,
        <% if (!isExternal) { %>
        templateComboBox,
        <% } %>
        new DatabaseSchemaNamePanel(),
        <% if (isExternal) { %>
        editableCheckBox,
        indexableCheckBox,
        fastCacheRefreshCheckBox,
        <% } %>
        grid,
        metaDataTextArea,
        tableText,
        {
            inputType: 'hidden',
            name: 'X-LABKEY-CSRF',
            value: LABKEY.CSRF
        }
    ],
    buttons:[
        {text:'<%=text(bean.isInsert() ? "Create" : "Update")%>', type:'submit', handler:submit},
        <% if (!bean.isInsert()) { %>
        {text:'Delete', handler:function() {document.location = <%=q(bean.getDeleteURL().toString())%>;}},
        <% } %>
        {text:'Cancel', handler:function() {document.location = <%=q(bean.getReturnURL().toString())%>;}}
    ],
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

    if (external)
    {
        initEditable(<%=def.isEditable()%>, <%=initialSource.editable%>);
    }
    else
    {
        templateComboBox.on('select', templateComboBox_onSelect);

        var sourceContainerId = dataSourceCombo.getValue();
        loadSchemaTemplateStore(sourceContainerId, templatesStore);
    }
    loadTables();

    // attach helpPopup as tooltips to field labels
    Ext.each(f.form.items.items, function(item) {
        if (item.rendered && Ext.isDefined(item.helpPopup)) {
            var labelEl = item.getEl().up('.x-form-item', 10, true).child('.x-form-item-label');
            if (labelEl) {
                var labelTxt = item.fieldLabel + ' <i class="fa fa-question-circle" data-toggle="tooltip" data-placement="right" title="' + item.helpPopup.html + '"></i> :';
                labelEl.update(labelTxt);
            }
        }
    });
    $('[data-toggle="tooltip"]').tooltip();
});

// Populate the "Database Schema Name" combo box with new data source's schemas
function dataSourceCombo_onSelect()
{
    var dataSourceIndex = dataSourceStore.find("value", dataSourceCombo.getValue());

    templateComboBox_onSelect();

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
    var schemaName = sourceSchemaCombo.getValue();
    var dataSourceIndex = dataSourceStore.find("value", dataSourceCombo.getValue());
    if (external) {
        if (userSchemaText.getValue() == "")
            userSchemaText.setValue(schemaName);
        initEditable(false, dataSources[dataSourceIndex][2]);
    } else {
        if (userSchemaText.getValue() == "") {
            // Add prefix to name if the source container is the current container
            if (schemaName.length > 0 && dataSources[dataSourceIndex][0] == LABKEY.container.id) {
                schemaName = "Linked" + schemaName[0].toUpperCase() + schemaName.substring(1);
            }
            userSchemaText.setValue(schemaName);
        }
        var sourceContainerId = dataSourceCombo.getValue();
        loadSchemaTemplateStore(sourceContainerId, templatesStore);
    }
    metaDataTextArea.setValue("");
    loadTables();
}

function templateComboBox_onSelect()
{
    var templateName = templateComboBox.getValue();
    var templateRecord = templateName ? templateComboBox.store.getById(templateName) : undefined;
    if (templateRecord)
    {
        sourceSchemaCombo.setValue(templateRecord.get("sourceSchemaName"));
        sourceSchemaCombo.setDisabled(true);

        selectTables(templateRecord.get("tables"));
        grid.setDisabled(true);

        metaDataTextArea.setValue(templateRecord.get("metadata"));
        metaDataTextArea.setDisabled(true);
    }
    else
    {
        sourceSchemaCombo.setValue("");
        sourceSchemaCombo.setDisabled(false);

        tablesStore.removeAll();
        grid.selModel.clearSelections();
        grid.setDisabled(false);

        metaDataTextArea.setValue("");
        metaDataTextArea.setDisabled(false);
    }
}

function initEditable(value, enabled)
{
    if (external)
    {
        editableCheckBox.setValue(value);
        editableCheckBox.setDisabled(!enabled);
    }
}

function loadTables()
{
    var dataSource = dataSourceCombo.getValue();
    var schemaName = sourceSchemaCombo.getValue();

    // dataSource and/or schemaName could be empty, but action handles this
    tablesStore.load({
        params: {dataSource: dataSource, schemaName: schemaName},
        callback: tablesLoaded
    });
}

function tablesLoaded()
{
    selectTables(initialTables);
    updateTableTitle();
}

function selectTables(tableNames)
{
    if ('*' == tableNames || tableNames == null)
    {
        grid.selModel.selectAll();
    }
    else
    {
        if (Ext.isString(tableNames))
            tableNames = tableNames.split(',');

        var recordArray = [];

        for (var i = 0; i < tableNames.length; i++)
        {
            // This should be case-insensitive, which is important, #19440
            var idx = tablesStore.find("table", tableNames[i]);

            if (-1 != idx)
                recordArray.push(tablesStore.getAt(idx));
        }

        grid.selModel.selectRecords(recordArray);
        initialTables = '*';
    }
}

function templatesLoaded(store, records, options)
{
    // insert a null record to make clearing the templateComboBox easier.
    // Copied from Store.js onLoad
    var data = {
        name: "[none]",
        sourceSchemaName: "",
        tables: [],
        metadata: ""
    };

    var recordConstructor = Ext.data.Record.create(templateComboBox.store.reader.meta.fields);
    var record = new recordConstructor(data, -1);

    templateComboBox.store.insert(0, record);
    templateComboBox.setValue('[none]');
}

function submit()
{
    var templateName = templateComboBox.getValue();
    var templateRecord = templateName ? templateComboBox.store.getById(templateName) : undefined;
    if (!templateRecord)
    {
        // if "[none]" is selected, clear the form value
        templateComboBox.setValue(null);

        if (grid.selModel.getCount() == tablesStore.getCount())
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

    }
    else
    {
        sourceSchemaCombo.setValue(null);
        sourceSchemaCombo.setDisabled(false);
        metaDataTextArea.setValue(null);
        metaDataTextArea.setDisabled(false);
        tableText.setValue(null);
    }

    f.getForm().submit();
}

function updateTableTitle()
{
    var selectedCount = grid.selModel.getCount();
    var title = "&nbsp;";

    if (sourceSchemaCombo.getValue() != '')
    {
        if (selectedCount == tablesStore.getCount())
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

function loadSchemaTemplateStore(sourceContainerId, schemaTemplateStore)
{
    Ext.Ajax.request({
        url: LABKEY.ActionURL.buildURL("query", "schemaTemplates.api", sourceContainerId),
        success: function(response){
            console.log('loading data');
            schemaTemplateStore.loadData(Ext.util.JSON.decode(response.responseText));
        },
        failure: function(){}
    });
}

}(jQuery);
</script>
