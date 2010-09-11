/*
    A pop-up, ext-style schema/query/view picker.  Just call chooseView(), specifying a dialog title, some help text,
    a separator character, and a function to call when the user clicks submit.  The function parameter is a String
    containing schema, query, and (optional) view separated by the separator character.

    Originally developed by britt.  Generalized into a reusable widget by adam.
*/
var dataFieldName = 'name';
var dataUrlFieldName = 'viewDataUrl';

function populateSchemas(schemaCombo, queryCombo, viewCombo, schemasInfo)
{
    schemaCombo.store.removeAll();
    schemaCombo.store.loadData(getArrayArray(schemasInfo.schemas));
    schemaCombo.on("select", function(combo, record, index)
    {
        queryCombo.clearValue();
        viewCombo.clearValue();
        LABKEY.Query.getQueries({
            schemaName: record.data[record.fields.first().name],
            successCallback: function(queriesInfo) { populateQueries(queryCombo, viewCombo, queriesInfo); }
        })
    });
}

var selectedQueryURL = "";
var selectedViewURL = "";

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

function chooseView(title, helpText, sep, submitFunction)
{
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
            triggerAction: 'all'
        });

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
            triggerAction: 'all'
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
            triggerAction: 'all'
        });

    LABKEY.Query.getSchemas({
        successCallback: function(schemasInfo) { populateSchemas(schemaCombo, queryCombo, viewCombo, schemasInfo); }
    });

    var labelStyle = 'border-bottom:1px solid #AAAAAA;margin:3px';

    var queryLabel = new Ext.form.Label({
        html: '<div style="' + labelStyle +'">' + helpText + '<\/div>'
    });

    var formPanel = new Ext.form.FormPanel({
        padding: 5,
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
