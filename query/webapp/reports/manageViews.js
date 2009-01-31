/*
 * Copyright (c) 2004-2009 Fred Hutchinson Cancer Research Center
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
LABKEY.requiresExtJs(true);

Ext.grid.RowExpander = function(config){
    Ext.apply(this, config);

    this.addEvents({
        beforeexpand : true,
        expand: true,
        beforecollapse: true,
        collapse: true
    });

    Ext.grid.RowExpander.superclass.constructor.call(this);

    if(this.tpl){
        if(typeof this.tpl == 'string'){
            this.tpl = new Ext.Template(this.tpl);
        }
        this.tpl.compile();
    }

    this.state = {};
    this.bodyContent = {};
};

Ext.extend(Ext.grid.RowExpander, Ext.util.Observable, {
    header: "",
    width: 20,
    sortable: false,
    fixed:true,
    menuDisabled:true,
    dataIndex: '',
    id: 'expander',
    lazyRender : true,
    enableCaching: true,

    getRowClass : function(record, rowIndex, p, ds){
        p.cols = p.cols-1;
        var content = this.bodyContent[record.id];
        if(!content && !this.lazyRender){
            content = this.getBodyContent(record, rowIndex);
        }
        if(content){
            p.body = content;
        }
        return this.state[record.id] ? 'x-grid3-row-expanded' : 'x-grid3-row-collapsed';
    },

    init : function(grid){
        this.grid = grid;

        var view = grid.getView();
        view.getRowClass = this.getRowClass.createDelegate(this);

        view.enableRowBody = true;

        grid.on('render', function(){
            view.mainBody.on('mousedown', this.onMouseDown, this);
        }, this);
    },

    getBodyContent : function(record, index){
        if(!this.enableCaching){
            return this.tpl.apply(record.data);
        }
        var content = this.bodyContent[record.id];
        if(!content){
            content = this.tpl.apply(record.data);
            this.bodyContent[record.id] = content;
        }
        return content;
    },

    onMouseDown : function(e, t){
        if(t.className == 'x-grid3-row-expander'){
            e.stopEvent();
            var row = e.getTarget('.x-grid3-row');
            this.toggleRow(row);
        }
    },

    renderer : function(v, p, record){
        p.cellAttr = 'rowspan="2"';
        return '<div class="x-grid3-row-expander">&#160;</div>';
    },

    beforeExpand : function(record, body, rowIndex){
        if(this.fireEvent('beforeexpand', this, record, body, rowIndex) !== false){
            if(this.tpl && this.lazyRender){
                body.innerHTML = this.getBodyContent(record, rowIndex);
            }
            return true;
        }else{
            return false;
        }
    },

    toggleRow : function(row){
        if(typeof row == 'number'){
            row = this.grid.view.getRow(row);
        }
        this[Ext.fly(row).hasClass('x-grid3-row-collapsed') ? 'expandRow' : 'collapseRow'](row);
    },

    expandRow : function(row){
        if(typeof row == 'number'){
            row = this.grid.view.getRow(row);
        }
        var record = this.grid.store.getAt(row.rowIndex);
        var body = Ext.DomQuery.selectNode('tr:nth(2) div.x-grid3-row-body', row);
        if(this.beforeExpand(record, body, row.rowIndex)){
            this.state[record.id] = true;
            Ext.fly(row).replaceClass('x-grid3-row-collapsed', 'x-grid3-row-expanded');
            this.fireEvent('expand', this, record, body, row.rowIndex);
        }
    },

    collapseRow : function(row){
        if(typeof row == 'number'){
            row = this.grid.view.getRow(row);
        }
        var record = this.grid.store.getAt(row.rowIndex);
        var body = Ext.fly(row).child('tr:nth(1) div.x-grid3-row-body', true);
        if(this.fireEvent('beforecollapse', this, record, body, row.rowIndex) !== false){
            this.state[record.id] = false;
            Ext.fly(row).replaceClass('x-grid3-row-expanded', 'x-grid3-row-collapsed');
            this.fireEvent('collapse', this, record, body, row.rowIndex);
        }
    }
});

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

    // an array of menu items to insert into the create dropdown button
    createMenu : undefined,

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
     * The row expander object, display when the row is expanded
     */
    expander : new Ext.grid.RowExpander({
        tpl : new Ext.XTemplate(
                '<table>',
                    '<tpl if="description != undefined"><tr><td><b>description</b></td><td>{description}</td></tr></tpl>',
                    '<tr><td><b>query name</b></td><td>{query}</td></tr>',
                    '<tpl if="schema != undefined"><tr><td><b>schema name</b></td><td>{schema}</td></tr></tpl>',
                    '<tr><td><b>permissions</b></td><td>{permissions}</td>',
                    '<tr><td><b>links</b></td><td>',
                        '<tpl if="runUrl != undefined">&nbsp;[<a href="{runUrl}">display</a>]</tpl>',
                        '<tpl if="editUrl != undefined">&nbsp;[<a href="{editUrl}">edit</a>]</tpl></td></tr>',
                '</table>')
        }),

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
                        {name:'createdBy'},
                        {name:'created'},
                        {name:'modifiedBy'},
                        {name:'modified'},
                        {name:'permissions'},
                        {name:'description', defaultValue:undefined},
                        {name:'editable', type:'boolean'},
                        {name:'icon', defaultValue:undefined},
                        {name:'runUrl', defaultValue:undefined},
                        {name:'editUrl', defaultValue:undefined},
                        {name:'reportId', defaultValue:undefined},
                        {name:'type'}]),
            proxy: this.getConnectionProxy(),
            autoLoad: true,
            sortInfo: {field:'query', direction:"ASC"},
/*
            listeners: {
                load: function(store, records) {
                    if (records.length == 0)
                        Ext.Msg.alert("Manage Views", "You have no views in this container.");
                }
            },
*/
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
            plugins: this.expander,
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

        var buttons = [
            {text:'Expand All', tooltip: {text:'Expands all groups', title:'Expand All'}, listeners:{click:function(button, event) {this.grid.view.expandAllGroups();}, scope:this}},
            {text:'Collapse All', tooltip: {text:'Collapses all groups', title:'Collapse All'}, listeners:{click:function(button, event) {this.grid.view.collapseAllGroups();}, scope:this}},
            {text:'Delete Selected', id: 'btn_deleteView', tooltip: {text:'Delete selected view', title:'Delete Views'}, listeners:{click:function(button, event) {deleteSelections(this.grid);}, scope:this}},
            {text:'Rename', id: 'btn_editView', tooltip: {text:'Rename or edit the description of an existing view (you can also double click on the view to edit)', title:'Rename View'}, listeners:{click:function(button, event) {this.editSelected(button);}, scope:this}}
        ];

        if (this.createMenu != undefined)
        {
            var item = new Ext.Button({
                text:'Create',
                id: 'btn_createView',
                menu: {
                    id: 'mainMenu',
                    cls:'extContainer',
                    items: this.createMenu }
            });
            buttons.splice(0,0,item);
        }
        return buttons;
    },

    /**
     * Create the grid columns
     */
    getColumns : function() {
        return [
            this.expander,    
            //this.selModel,
            {header:'Title', dataIndex:'name', width:200},
            {header:'Type', dataIndex:'type', renderer:typeRenderer},
            {header:'Created By', dataIndex:'createdBy'},
            {header:'Created', dataIndex:'created', hidden:true},
            {header:'Modified By', dataIndex:'modifiedBy', hidden:true},
            {header:'Modified', dataIndex:'modified', hidden:true},
            {header:'Permissions', dataIndex:'permissions'},
            {header:'Query', dataIndex:'query'},
            {header:'Description', dataIndex:'description', hidden:true},
            {header:'Schema', dataIndex:'schema', hidden:true}
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
            Ext.Msg.alert("Rename Views", "There are no views selected");
            return false;
        }

        if (selections.length > 1)
        {
            Ext.Msg.alert("Rename Views", "Only one view can be edited at a time");
            return false;
        }

        if (selections[0].data.reportId == undefined)
        {
            Ext.Msg.alert("Rename Views", "Only reports can be renamed, to convert a custom query to a report expand the row and click on the [convert to report] link");
            return false;
        }
        editRecord(button, this.grid, selections[0].data);
    }
};


function typeRenderer(value, p, record)
{
    if (record.data.icon != undefined)
        return '<img src="' + record.data.icon + '">&nbsp;' + value;
    else
        return value;
}

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

        Ext.Msg.show({
                title : 'Delete Views',
                msg : msg,
                buttons: Ext.Msg.YESNO,
                icon: Ext.Msg.QUESTION,
                fn: function(btn, text) {
                    if (btn == 'yes')
                    {
                        Ext.Ajax.request({

                            url: LABKEY.ActionURL.buildURL("reports", "manageViewsDeleteReports") + '?' + params.join('&'),
                            method: "POST",
                            scope: this,
                            success: function(){grid.store.load();},
                            failure: function(){Ext.Msg.alert("Delete Views", "Deletion Failed");}
                        });
                    }},
                id: 'delete_views'
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
        }]
/*
            ,{
            name: 'editUrl',
            xtype: 'button',
            text: 'Edit Source...',
            dest: record.editUrl,
            tooltip: {text:'Some view types support advanced editing capabilities, if they do this button will navigate you to the alternate view', title:'Edit Source'},
            disabled: record.editUrl ? false : true,
            handler: function(){doAdvancedEdit(this);}
        }]

*/
    });
    var win = new Ext.Window({
        title: 'Edit View',
        layout:'form',
        border: false,
        width: 450,
        height: 185,
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

