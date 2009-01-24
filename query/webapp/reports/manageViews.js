/*
 * Copyright (c) 2004-2009 Fred Hutchinson Cancer Research Center
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
LABKEY.requiresExtJs(true);

LABKEY.ViewsPanel = function(config) {
    config = config || {};

    Ext.apply(this, config);

    if (!this.container)
    {
        Ext.Msg.alert("Configuration Error", "config.container is a required parameter.");
        return;
    }

    if (!this.renderTo)
    {
        Ext.Msg.alert("Configuration Error", "config.renderTo is a required parameter.");
        return;
    }
};

LABKEY.ViewsPanel.prototype = {
    // the id of the div to render this panel to
    renderTo : undefined,

    // the container path for creating the connection object
    container : undefined,

    // the grid panel
    grid : undefined,

    // the grid selection model
    selModel : new Ext.grid.CheckboxSelectionModel(),

    /**
     * The connection object
     */
    getConnectionProxy : function() {
        return new Ext.data.HttpProxy(new Ext.data.Connection({
            url: LABKEY.ActionURL.buildURL("reports", "manageViewsSummary", this.container),
            method: 'GET'
        }));
    },

    /**
     * Create the data store used to parse view record information returned by the
     * connection proxy
     */
    getStore : function() {
        var store = new Ext.data.GroupingStore({
            reader: new Ext.data.JsonReader({root:'views',id:'reportId'},
                    [
                        {name:'query'},
                        {name:'schema'},
                        {name:'name'},
                        {name:'owner'},
                        {name:'public'},
                        {name:'security'},
                        {name:'displayName'},
                        {name:'description'},
                        {name:'editable'},
                        {name:'editUrl'},
                        {name:'reportId'},
                        {name:'type'}]),
            proxy: this.getConnectionProxy(),
            autoLoad: true,
            sortInfo: {field:'query', direction:"ASC"},
            listeners: {
                load: function(store, records) {
                    if (records.length == 0)
                        Ext.Msg.alert("Manage Views", "You have no views in this container.");
                }
            },
            groupField:'query'});

        return store;
    },

    /**
     * Create the config object for the grid panel
     */
    getGridConfig : function() {
        var gridConfig = {
            el: this.renderTo,
            autoScroll:false,
            autoHeight:true,
            width:800,
            store: this.getStore(),
            selModel: this.selModel,
            listeners: {
                rowdblclick: function(g, rowIndex, event) {
                    event.stopEvent();
                    this.editSelected(event);
                },
                scope:this
            },
            view: new Ext.grid.GroupingView({
                startCollapsed:false,
                hideGroupedColumn:true,
                forceFit:true,
                groupTextTpl: '{values.group}'
            }),
            buttons: this.getButtons(),
            buttonAlign:'center',
            columns: this.getColumns()
        };
        return gridConfig;
    },

    /**
     * Create the button at the bottom of the panel
     */
    getButtons : function() {
        this.editBtn = new Ext.Button({
            text:'Edit',
            id: 'btn_editView',
            tooltip: {text:'Edit an existing view (you can also double click on the view to edit)', title:'Edit View'},
            listeners:{click:function(button, event) {this.editSelected(button);}, scope:this}
        });

        return [
            {text:'Expand All', tooltip: {text:'Expands all groups', title:'Expand All'}, listeners:{click:function(button, event) {this.grid.view.expandAllGroups();}, scope:this}},
            {text:'Collapse All', tooltip: {text:'Collapses all groups', title:'Collapse All'}, listeners:{click:function(button, event) {this.grid.view.collapseAllGroups();}, scope:this}},
            {text:'Delete Selected', id: 'btn_deleteView', tooltip: {text:'Delete selected view', title:'Delete Views'}, listeners:{click:function(button, event) {deleteSelections(this.grid);}, scope:this}},
            {text:'Edit', id: 'btn_editView', tooltip: {text:'Edit an existing view (you can also double click on the view to edit)', title:'Edit View'}, listeners:{click:function(button, event) {this.editSelected(button);}, scope:this}}
        ];
    },

    /**
     * Create the grid columns
     */
    getColumns : function() {
        return [
            //this.selModel,
            {header:'Title', dataIndex:'displayName', width:200, renderer:renderRow},
            {header:'Type', dataIndex:'type', renderer:renderRow},
            {header:'Description', dataIndex:'description', hidden:true},
            {header:'Created By', dataIndex:'owner', renderer:renderRow},
            {header:'Shared', dataIndex:'public', renderer:renderRow},
            {header:'Schema', dataIndex:'schema', hidden:true},
            {header:'Query', dataIndex:'query'}
        ];
    },

    /**
     * Create and render this view panel
     */
    show : function() {

        this.grid = new Ext.grid.GridPanel(this.getGridConfig());
        this.grid.render();
    },

    /**
     * Edit the selected view
     * @param button
     * @param grid
     */
    editSelected : function(button) {
        var selections = this.grid.selModel.getSelections();

        if (selections.length == 0)
        {
            Ext.Msg.alert("Edit Views", "There are no views selected");
            return false;
        }

        if (selections.length > 1)
        {
            Ext.Msg.alert("Edit Views", "Only one view can be edited at a time");
            return false;
        }

        editRecord(button, this.grid, selections[0].data);
    }
};



function renderRow(value, p, record)
{
    var txt = 'ext:qtip="';

    txt = txt.concat('<b>title:</b> ' + record.data.name + '<br>');
    if (record.data.description != undefined)
        txt = txt.concat('<b>description:</b> ' + record.data.description + '<br>');
    if (record.data.query != undefined)
        txt = txt.concat('<b>query:</b> ' + record.data.query + '<br>');
    if (record.data.schema != undefined)
        txt = txt.concat('<b>schema:</b> ' + record.data.schema + '<br>');
    txt = txt.concat('"');
    p.attr = txt;

    return value;
}

/**
 * Delete the selected views
 */
function deleteSelections(grid)
{
    var selections = grid.selModel.getSelections();

    if (selections.length == 0)
    {
        Ext.Msg.alert("Delete Views", "There are no views selected");
        return false;
    }
    else
    {
        var msg = "Delete Selected Views:<br/>";
        var params = [];

        for (var i=0; i < selections.length; i++)
        {
            msg = msg.concat(selections[i].data.name);
            msg = msg.concat('<br/>');

            params.push("reportId=" + selections[i].id);
        }

        Ext.Msg.confirm('Delete Views', msg, function(btn, text) {
            if (btn == 'yes')
            {
                Ext.Ajax.request({

                    url: LABKEY.ActionURL.buildURL("reports", "manageViewsDeleteReports") + '?' + params.join('&'),
                    method: "POST",
                    scope: this,
                    success: function(){grid.store.load();},
                    failure: function(){Ext.Msg.alert("Delete Views", "Deletion Failed");}
                });
            }
        });
    }
}

function editRecord(button, grid, record)
{
    var formPanel = new Ext.FormPanel({
        bodyStyle:'padding:5px',
        defaultType: 'textfield',
        items: [{
            fieldLabel: 'View Name',
            name: 'viewName',
            allowBlank:false,
            width: 250,
            value: record.name
        },{
            fieldLabel: 'Description',
            name: 'description',
            xtype: 'textarea',
            width: 250,
            value: record.description
        },{
            name: 'reportId',
            xtype: 'hidden',
            value: record.reportId
        },{
            name: 'editUrl',
            xtype: 'button',
            text: 'Edit Source...',
            dest: record.editUrl,
            tooltip: {text:'Some view types support advanced editing capabilities, if they do this button will navigate you to the alternate view', title:'Edit Source'},
            disabled: record.editUrl ? false : true,
            handler: function(){doAdvancedEdit(this);}
        }]

    });
    var win = new Ext.Window({
        title: 'Edit View',
        layout:'form',
        border: false,
        width: 450,
        height: 220,
        closeAction:'close',
        modal: false,
        items: formPanel,
        buttons: [{
            text: 'Submit',
            id: 'btn_submit',
            handler: function(){submitForm(win, formPanel, grid);}
        },{
            text: 'Cancel',
            id: 'btn_cancel',
            handler: function(){win.close();}
        }]
    });

    win.show(button);
}

function doAdvancedEdit(config)
{
    Ext.Msg.confirm('Edit Source', "Do you want to navigate away from your current view to the advanced editor?", function(btn, text) {
        if (btn == 'yes')
        {
            window.location = config.dest;
        }
    });
}

function submitForm(win, panel, grid)
{
    var items = panel.items;

    // client side validation
    var form = panel.getForm();
    if (form && !form.isValid())
    {
        Ext.Msg.alert('Edit Views', 'Not all fields have been properly completed');
        return false;
    }

    form.submit({
        url: LABKEY.ActionURL.buildURL("reports", "manageViewsEditReports"),
        waitMsg:'Submiting Form...',
        method: 'POST',
        success: function(){
            win.close();
            grid.store.load();
        },
        failure: function(form, action){Ext.Msg.alert("Save Error", "An error occurred while saving the view");}
    });
}

function setFormFieldTooltip(component)
{
    var label = Ext.get('x-form-el-' + component.id).prev('label');
    Ext.QuickTips.register({
        target: label,
        text: component.tooltip.text,
        title: ''
    });
}
