/*
 * Copyright (c) 2010-2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
/*
    A pop-up, ext-style schema/query/view picker.  Just call chooseView(), specifying a dialog title, some help text,
    a separator character, and a function to call when the user clicks submit.  The function parameter is a String
    containing schema, query, and (optional) view separated by the separator character.

    Originally developed by britt.  Generalized into a reusable widget by adam.
*/
var dataFieldName = 'name';
var dataUrlFieldName = 'viewDataUrl';
                                    // schema, query, view, column, folder, rootFolder, folderTypes
var initialValues = new Array();        // TODO: Select these values in combos

function populateSchemas(schemaCombo, queryCombo, viewCombo, schemasInfo, includeSchema, columnCombo, folderCombo)
{
    var schemas;

    if (includeSchema)
    {
        schemas = new Array();
        var len = schemasInfo.schemas.length;

        for (var i = 0; i < len; i++)
            if (includeSchema(schemasInfo.schemas[i]))
                schemas.push(schemasInfo.schemas[i]);
    }
    else
    {
        schemas = schemasInfo.schemas;
    }

    schemaCombo.store.removeAll();
    schemaCombo.store.loadData(getArrayArray(schemas));
    schemaCombo.on("select", function(combo, record, index)
    {
        queryCombo.clearValue();
        viewCombo.clearValue();
        if (columnCombo)
            columnCombo.clearValue();
        LABKEY.Query.getQueries({
            schemaName: record.data[record.fields.first().name],
            containerPath: folderCombo.getValue(),
            successCallback: function(details) { populateQueries(schemaCombo, queryCombo, viewCombo, details, columnCombo, folderCombo); }
        });
    });

    if (initialValues[0])
    {
        var index = schemaCombo.getStore().findExact('name', initialValues[0]);

        if (-1 != index)
        {
            var record = schemaCombo.getStore().getAt(index);
            schemaCombo.setValue(initialValues[0]);
            schemaCombo.fireEvent('select', schemaCombo, record, index);
        }

        initialValues[0] = null;
    }
}

function populateQueries(schemaCombo, queryCombo, viewCombo, queriesInfo, columnCombo, folderCombo)
{
    var records = [];
    for (var i = 0; i < queriesInfo.queries.length; i++)
    {
        var queryInfo = queriesInfo.queries[i];
        records[i] = [queryInfo.name, queryInfo.viewDataUrl];
    }

    queryCombo.store.removeAll();
    queryCombo.store.loadData(records);
    queryCombo.on("select", function(combo, record, index)
    {
        viewCombo.clearValue();
        if (columnCombo)
            columnCombo.clearValue();
        var queryName = record.data[record.fields.first().name];
        var schemaName = schemaCombo.getValue();
        LABKEY.Query.getQueryViews({
            containerPath: folderCombo.getValue(),
            schemaName: schemaName,
            queryName: queryName,
            successCallback: function(details)
            {
                populateViews(schemaCombo, queryCombo, viewCombo, details, columnCombo, folderCombo);
                if (columnCombo)
                {
                    LABKEY.Query.getQueryDetails({
                        containerPath: folderCombo.getValue(),
                        schemaName: schemaName,
                        queryName: queryName,
                        initializeMissingView: true,
                        successCallback: function(details) { populateColumns(columnCombo, details); }
                    });
                }
            }
        })
    });

    if (initialValues[1])
    {
        var queryComboIndex = queryCombo.getStore().findExact('name', initialValues[1]);

        if (-1 != queryComboIndex)
        {
            var record = queryCombo.getStore().getAt(queryComboIndex);
            queryCombo.setValue(initialValues[1]);
            queryCombo.fireEvent('select', queryCombo, record, queryComboIndex);
        }

        initialValues[1] = null;
    }
}

var defaultViewLabel = "[default view]";

function populateViews(schemaCombo, queryCombo, viewCombo, queryViews, columnCombo, folderCombo)
{
    var records = [[defaultViewLabel]];

    for (var i = 0; i < queryViews.views.length; i++)
    {
        var viewInfo = queryViews.views[i];
        if (viewInfo.name != null && viewInfo.name != "")
            records[records.length] = [viewInfo.name, viewInfo.viewDataUrl];
    }

    viewCombo.store.removeAll();
    viewCombo.store.loadData(records);

    if (columnCombo)
    {
        viewCombo.on("select", function(combo, record, index)
        {
            columnCombo.clearValue();
            LABKEY.Query.getQueryDetails({
                containerPath: folderCombo.getValue(),
                schemaName: schemaCombo.getValue(),
                queryName: queryCombo.getValue(),
                initializeMissingView: true,
                successCallback: function(details) { populateColumns(columnCombo, details); }
            });
        });
    }

    var initialView = defaultViewLabel;
    var viewComboIndex = viewCombo.getStore().findExact('name', initialView);
    if (initialValues[2])
    {
        viewComboIndex = viewCombo.getStore().findExact('name', initialValues[2]);

        if (-1 != viewComboIndex)
        {
            initialView = initialValues[2];
        }

        initialValues[2] = null;
    }

    viewCombo.setValue(initialView);
//    if (columnCombo)
//    {
//        var record = viewCombo.getStore().getAt(viewComboIndex);
//        viewCombo.fireEvent('select', viewCombo, record, viewComboIndex);
//    }
}

function populateColumns(columnCombo, details)
{
    var records = [];

    var columns = details.columns;
    for (var i = 0; i < columns.length; i++)
    {
        var name = columns[i].name;
        records[records.length] = [name, columns[i].fieldKey];
    }

    columnCombo.store.removeAll();
    columnCombo.store.loadData(records);

    if (initialValues[3])
    {
        var queryColumnIndex = columnCombo.getStore().findExact('name', initialValues[3]);

        if (-1 != queryColumnIndex)
        {
            var record = columnCombo.getStore().getAt(queryColumnIndex);
            columnCombo.setValue(initialValues[3]);
            columnCombo.fireEvent('select', columnCombo, record, queryColumnIndex);
        }

        initialValues[3] = null;
    }
}

function populateFolders(schemaCombo, queryCombo, viewCombo, columnCombo, folderCombo, details, includeSchema)
{
    var records = [["[current folder]", ""]];

    var folders = details.containers;
    if (folders && folders.length > 0)
        populateFoldersWithTree(folders[0], records);       // Just 1 at the root

    folderCombo.store.removeAll();
    folderCombo.store.loadData(records);
    folderCombo.on("select", function(combo, record, index)
    {
        schemaCombo.clearValue();
        queryCombo.clearValue();
        viewCombo.clearValue();
        if (columnCombo)
            columnCombo.clearValue();
        LABKEY.Query.getSchemas({
            containerPath: folderCombo.getValue(),
            successCallback: function(schemasInfo)
            {
                populateSchemas(schemaCombo, queryCombo, viewCombo, schemasInfo, includeSchema, columnCombo, folderCombo);
            }
        });
    });

    var initialFolder = records[0].path;
    var folderComboIndex = 0;
    if (initialValues[4])
    {
        folderComboIndex = folderCombo.getStore().findExact('path', initialValues[4]);

        if (-1 != folderComboIndex)
        {
            initialFolder = initialValues[4];
        }

        initialValues[4] = null;
    }

    folderCombo.setValue(initialFolder);
    var record = folderCombo.getStore().getAt(folderComboIndex);
    folderCombo.fireEvent('select', folderCombo, record, folderComboIndex);
}

function populateFoldersWithTree(folder, records)
{
    var name = folder.name;
    if ("/" == folder.path)
        name = "[root]";
    records[records.length] = [name, folder.path];
    if (folder.children)
    {
        for (var i = 0; i < folder.children.length; i++)
        {
            if (folder.children[i])
                populateFoldersWithTree(folder.children[i], records);
        }
    }
}

function populateRootFolder(folderCombo, details)
{
    var records = [["[current folder]", ""]];

    var folders = details.containers;
    if (folders && folders.length > 0)
        populateFoldersWithTree(folders[0], records);       // Just 1 at the root

    folderCombo.store.removeAll();
    folderCombo.store.loadData(records);

    var initialFolder = records[0].path;
    var folderComboIndex = 0;
    if (initialValues[5])
    {
        folderComboIndex = folderCombo.getStore().findExact('path', initialValues[5]);

        if (-1 != folderComboIndex)
        {
            initialFolder = initialValues[5];
        }

        initialValues[5] = null;
    }

    folderCombo.setValue(initialFolder);
    var record = folderCombo.getStore().getAt(folderComboIndex);
    folderCombo.fireEvent('select', folderCombo, record, folderComboIndex);
}

function populateFolderTypes(details, folderTypesCombo, rootFolderCombo, schemaCombo, queryCombo, viewCombo, columnCombo, folderCombo, includeSchema)
{
    var records = [["[all]",""]];

    for (var folderType in details)
        records[records.length] = [folderType, folderType];

    folderTypesCombo.store.removeAll();
    folderTypesCombo.store.loadData(records);

    var initialFolder = records[0].name;
    var folderTypesComboIndex = 0;
    if (initialValues[6])
    {
        folderTypesComboIndex = folderTypesCombo.getStore().findExact('name', initialValues[6]);

        if (-1 != folderTypesComboIndex)
        {
            initialFolder = initialValues[6];
        }

        initialValues[6] = null;
    }

    folderTypesCombo.setValue(initialFolder);
    var record = folderTypesCombo.getStore().getAt(folderTypesComboIndex);
    folderTypesCombo.fireEvent('select', folderTypesCombo, record, folderTypesComboIndex);

    LABKEY.Security.getContainers({
        container: ["/"],
        includeSubfolders: true,
        successCallback: function(details)
        {
            populateRootFolder(rootFolderCombo, details);
            populateFolders(schemaCombo, queryCombo, viewCombo, columnCombo, folderCombo, details, includeSchema);
        }
    });
}

function getArrayArray(simpleArray)
{
    var arrayArray = [];
    for (var i = 0; i < simpleArray.length; i++)
    {
        arrayArray[i] = [];
        arrayArray[i][0] = simpleArray[i];
    }
    return arrayArray;
}

var s;

function createCombo(fieldLabel, name, id, allowBlank, width)
{
    var combo = new Ext.form.ComboBox({
        typeAhead: false,
        store: new Ext.data.ArrayStore({
            fields: [{
                name: dataFieldName,
                sortType: function(value) { return value.toLowerCase(); }
            }],
            sortInfo: { field: dataFieldName }
        }),
        valueField: dataFieldName,
        displayField: dataFieldName,
        fieldLabel: fieldLabel,
        name: name,
        id: id,
        allowBlank: allowBlank,
        readOnly:false,
        editable:false,
        mode:'local',
        triggerAction: 'all',
        lazyInit: false
    });

    if(width){
        combo.setWidth(width);
    }
    return combo;
}

function createFolderCombo(fieldLabel, name, id, allowBlank, width)
{
    var combo = new Ext.form.ComboBox({
        typeAhead: false,
        store: new Ext.data.ArrayStore({
            fields: [{
                name: dataFieldName,
                sortType: function(value) { return value.toLowerCase(); }
            },{
                name: 'path'
            }],
            sortInfo: { field: dataFieldName }
        }),
        valueField: 'path',
        displayField: dataFieldName,
        fieldLabel: fieldLabel,
        name: name,
        id: id,
        allowBlank: allowBlank,
        readOnly:false,
        editable:false,
        mode:'local',
        triggerAction: 'all',
        lazyInit: false
    });

    if(width){
        combo.setWidth(width);
    }

    return combo;
}
function createSchemaCombo(width)
{
    return createCombo("Schema", "schema", "userQuery_schema", false, width);
}

function createQueryCombo(width)
{
    return createCombo("Query", 'query', 'userQuery_query', false, width);
}

function createViewCombo(width)
{
    return createCombo("View", "view", "userQuery_view", true, width);
}

function createBasicFolderCombo(width)
{
    return createFolderCombo("Folder", "folders", "userQuery_folders", true, width);
}

function createColumnCombo(width)
{
    return createCombo("Title Column", "column", "userQuery_Column", false, width);
}

function createRootFolderCombo(width)
{
    return createFolderCombo("Root Folder", "rootFolder", "userQuery_rootFolder", true, width);
}

function createFolderTypesCombo(width)
{
    return createCombo("Folder Types", "folderTypes", "userQuery_folderTypes", true, width);
}

// current value is an optional string parameter that provides string containing the current value.
// includeSchema is an optional function that determines if passed schema name should be included in the schema drop-down.
function chooseView(title, helpText, sep, submitFunction, currentValue, includeSchema)
{
    if (currentValue)
        initialValues = currentValue.split(sep);

    var schemaCombo = createSchemaCombo();
    s = schemaCombo;
    var queryCombo = createQueryCombo();
    var viewCombo = createViewCombo();

    LABKEY.Query.getSchemas({
        successCallback: function(schemasInfo) { populateSchemas(schemaCombo, queryCombo, viewCombo, schemasInfo, includeSchema); }
    });

    var labelStyle = 'border-bottom:1px solid #AAAAAA;margin:3px';

    var queryLabel = new Ext.form.Label({
        html: '<div style="' + labelStyle +'">' + helpText + '<\/div>'
    });

    var formPanel = new Ext.form.FormPanel({
        padding: 5,
        timeout: Ext.Ajax.timeout,
        items: [queryLabel, schemaCombo, queryCombo, viewCombo]});

    var win = new Ext.Window({
        title: title,
        layout:'fit',
        border: false,
        width: 475,
        height: 270,
        closeAction:'close',
        modal: true,
        items: formPanel,
        resizable: false,
        buttons: [{
            text: 'Submit',
            id: 'btn_submit',
            handler: function(){
                var form = formPanel.getForm();

                if (form && !form.isValid())
                {
                    Ext.Msg.alert(title, 'Please complete all required fields.');
                    return false;
                }

                var viewName = viewCombo.getValue();
                if (viewName == defaultViewLabel)
                    viewName = "";

                submitFunction(schemaCombo.getValue() + sep +
                               queryCombo.getValue() + sep +
                               viewName);
                win.close();
            }
        },{
            text: 'Cancel',
            id: 'btn_cancel',
            handler: function(){win.close();}
        }],
        bbar: [{ xtype: 'tbtext', text: '', id:'statusTxt'}]
    });
    win.show();
}

function customizeMenu(submitFunction, cancelFunction, renderToDiv, currentValue, includeSchema)
{
    var schemaCombo = createSchemaCombo(380);
    s = schemaCombo;
    var queryCombo = createQueryCombo(380);
    var viewCombo = createViewCombo(380);
    var columnCombo = createColumnCombo(380);
    var folderCombo = createBasicFolderCombo(380);

    var title = "";
    var schemaName = "";
    var queryName = "";
    var viewName = "";
    var columnName = "";
    var folderName = "";
    var url = "";
    var urlBottom = "";
    var isChoiceListQuery = true;
    var includeAllDescendants = true;
    var rootFolder = "";
    var folderType = "";
    var pageId = null;
    var webPartIndex = 0;

    if (currentValue)
    {
        title = currentValue.title;
        schemaName = currentValue.schemaName;
        queryName = currentValue.queryName;
        viewName = currentValue.viewName;
        columnName = currentValue.columnName;
        folderName = currentValue.folderName;
        url = currentValue.url;
        isChoiceListQuery = currentValue.choiceListQuery;
        includeAllDescendants = currentValue.includeAllDescendants;
        rootFolder = currentValue.rootFolder;
        folderType = currentValue.folderTypes;
        initialValues = [schemaName, queryName, viewName, columnName, folderName, rootFolder, folderType];

        pageId = currentValue.pageId;
        webPartIndex = currentValue.webPartIndex;
    }

    LABKEY.Security.getFolderTypes({
        successCallback: function(details)
        {
            populateFolderTypes(details, folderTypesCombo, rootFolderCombo, schemaCombo, queryCombo, viewCombo,
                    columnCombo, folderCombo, includeSchema);
        }
    });

    var formSQV = new Ext.form.FormPanel({
        border: false,
        hidden: !isChoiceListQuery,
        layout: 'form',
        labelSeparator: '',
        items: [folderCombo, schemaCombo, queryCombo, viewCombo, columnCombo]
    });

    var titleField = new Ext.form.TextField({
        name: 'title',
        fieldLabel: 'Title',
        value: title,
        width : 380
    });

    var urlField = new Ext.form.TextField({
        name: 'url',
        fieldLabel: 'URL',
        value: url,
        width : 380
    });

    var includeAllDescendantsCheckbox = new Ext.form.Checkbox({
        name: 'includeAllDescendants',
        boxLabel: 'Include All Descendants',
        height: 30,
        value: true,
        checked: includeAllDescendants,
        width: 380
    });

    var rootFolderCombo = createRootFolderCombo(380);
    var folderTypesCombo = createFolderTypesCombo(380);

    var formFolders = new Ext.form.FormPanel({
        border: false,
        labelSeparator: '',
        timeout: Ext.Ajax.timeout,
        hidden: isChoiceListQuery,
        items: [includeAllDescendantsCheckbox, rootFolderCombo, folderTypesCombo]
    });

    var queryRadio = new Ext.form.Radio({
        boxLabel: 'Create from List or Query',
        name: 'menuSelect',
        inputValue: 'list',
        width: 200,
        checked: isChoiceListQuery,
        listeners: {
            scope: this,
            check: function(checkbox, checked){
                if (checked){
                    formSQV.setVisible(true);
                } else {
                    formSQV.setVisible(false);
                }
            }
        },
        id: 'query-radio'
    });

    var folderRadio = new Ext.form.Radio({
        boxLabel: 'Folders',
        name: 'menuSelect',
        inputValue: 'folders',
        checked: !isChoiceListQuery,
        listeners: {
            scope: this,
            check: function(checkbox, checked){
                if (checked){
                    formFolders.setVisible(true);
                } else {
                    formFolders.setVisible(false);
                }
            }
        },
        id: 'folder-radio'
    });

    var menuRadioGroup = new Ext.form.RadioGroup({
        fieldLabel: 'Menu Items',
        width: 380,
        vertical: false,
        columns: [.75, .25],
        items: [queryRadio, folderRadio]
    });

    var formMenuSelectPanel = new Ext.form.FormPanel({
        border: false,
        items: [menuRadioGroup]
    });

    var formWinPanel = new Ext.Panel({
        border: false,
        layout: 'form',
        timeout: Ext.Ajax.timeout,
        labelSeparator: '',
        items: [titleField, formMenuSelectPanel, formSQV, formFolders, urlField]
    });

    var win = new Ext.Panel({
        renderTo: renderToDiv,
        border: false,
        items: formWinPanel,
        resizable: true,
        buttons: [{
            text: 'Submit',
            id: 'btn_submit',
            handler: function(){
                var isChoiceListQuery = menuRadioGroup.getValue().getRawValue() === 'list';
                var form = null;
                if(isChoiceListQuery){
                    form = formSQV.getForm();
                } else {
                    form = formFolders.getForm();
                }

                if (form && !form.isValid()){
                    Ext.Msg.alert(title, 'Please complete all required fields.');
                    return false;
                }

                var viewName = "";
                var viewRecord = viewCombo.getValue();
                if (viewRecord != defaultViewLabel)
                    viewName = viewRecord;

                submitFunction({
                    schemaName : schemaCombo.getValue(),
                    queryName : queryCombo.getValue(),
                    viewName: viewName,
                    folderName: folderCombo.getValue(),
                    columnName: columnCombo.getValue(),
                    title: titleField.getValue(),
                    url: urlField.getValue(),
                    choiceListQuery: isChoiceListQuery,
                    rootFolder: rootFolderCombo.getValue(),
                    folderTypes: folderTypesCombo.getValue(),
                    includeAllDescendants: includeAllDescendantsCheckbox.checked,
                    pageId: pageId,
                    webPartIndex: webPartIndex
                });
            }
        },{
            text: 'Cancel',
            id: 'btn_cancel',
            handler: function(){cancelFunction(); formWinPanel.doLayout();}
        }]
    });

    return win;
}
