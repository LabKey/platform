/*
 * Copyright (c) 2010-2011 LabKey Corporation
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
var initialValues = new Array();        // TODO: Select these values in combos

function populateSchemas(schemaCombo, queryCombo, viewCombo, schemasInfo, includeSchema)
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
        LABKEY.Query.getQueries({
            schemaName: record.data[record.fields.first().name],
            successCallback: function(queriesInfo) { populateQueries(queryCombo, viewCombo, queriesInfo); }
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

function populateQueries(queryCombo, viewCombo, queriesInfo)
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
        LABKEY.Query.getQueryViews({
            schemaName: queriesInfo.schemaName,
            queryName: record.data[record.fields.first().name],
            successCallback: function(queriesInfo) { populateViews(viewCombo, queriesInfo); }
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

function populateViews(viewCombo, queryViews)
{
    var records = [[defaultViewLabel]];

    for (var i = 0; i < queryViews.views.length; i++)
    {
        var viewInfo = queryViews.views[i];
        var name =  viewInfo.name != null ? viewInfo.name : defaultViewLabel;
        records[records.length] = [name, viewInfo.viewDataUrl];
    }

    viewCombo.store.removeAll();
    viewCombo.store.loadData(records);

    if (initialValues[2])
    {
        var viewComboIndex = viewCombo.getStore().findExact('name', initialValues[2]);

        if (-1 != viewComboIndex)
            viewCombo.setValue(initialValues[2]);

        initialValues[2] = null;
    }
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

// current value is an optional string parameter that provides string containing the current value.
// includeSchema is an optional function that determines if passed schema name should be included in the schema drop-down.
function chooseView(title, helpText, sep, submitFunction, currentValue, includeSchema)
{
    if (currentValue)
        initialValues = currentValue.split(sep);

    var schemaCombo = new Ext.form.ComboBox({
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
            fieldLabel: "Schema",
            name: 'schema',
            id: 'userQuery_schema',
            allowBlank:false,
            readOnly:false,
            editable:false,
            mode:'local',
            triggerAction: 'all',
            lazyInit: false
        });

    s = schemaCombo;
    var queryCombo = new Ext.form.ComboBox({
            typeAhead: false,
            store: new Ext.data.ArrayStore({
                fields: [{
                    name: dataFieldName,
                    sortType: function(value) { return value.toLowerCase(); }
                }, {
                    name: dataUrlFieldName
                }],
                sortInfo: { field: dataFieldName }
            }),
            valueField: dataFieldName,
            displayField: dataFieldName,
            fieldLabel: "Query",
            name: 'query',
            id: 'userQuery_query',
            allowBlank:false,
            readOnly:false,
            editable:false,
            mode:'local',
            triggerAction: 'all',
            lazyInit: false
        });

    var viewCombo = new Ext.form.ComboBox({
            typeAhead: false,
            store: new Ext.data.ArrayStore({
                fields: [{
                    name: dataFieldName,
                    sortType: function(value) { return value.toLowerCase(); }
                }, {
                    name: dataUrlFieldName
                }],
                sortInfo: { field: dataFieldName }
            }),
            valueField: dataFieldName,
            displayField: dataFieldName,
            fieldLabel: "View",
            name: 'view',
            id: 'userQuery_view',
            allowBlank:true,
            readOnly:false,
            editable:false,
            mode:'local',
            triggerAction: 'all',
            lazyInit: false
        });

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
