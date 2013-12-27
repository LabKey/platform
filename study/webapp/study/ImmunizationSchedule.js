
Ext4.define('LABKEY.ext4.BaseVaccineDesignGrid', {

    extend : 'Ext.panel.Panel',

    grid : null,

    constructor : function(config) {

        Ext4.QuickTips.init();

        Ext4.applyIf(config, {
            title : 'Product',
            filterRole : null,
            hiddenColumns : ["RowId", "Role"],
            frame  : false
        });

        Ext4.define('StudyDesign.Product', {
            extend : 'Ext.data.Model',
            fields : [
                {name : 'RowId', type : 'int'},
                {name : 'Label'},
                {name : 'Role'},
                {name : 'Type'},
                {name : 'Antigens'}
            ]
        });

        this.callParent([config]);
    },

    initComponent : function() {

        this.items = [this.configureProductGrid()];

        this.callParent();
    },

    configureProductGrid : function() {
        var store = Ext4.create('Ext.data.Store', {
            model : 'StudyDesign.Product',
            proxy: {
                type: 'ajax',
                url : LABKEY.ActionURL.buildURL("study", "getStudyProducts", null, {role: this.filterRole}),
                reader: {
                    type: 'json',
                    root: 'products'
                }
            },
            sorters: [{ property: 'RowId', direction: 'ASC' }],
            autoLoad: true
        });

        this.grid = Ext4.create('LABKEY.ext4.GridPanel', {
            store: store,
            columns: this.getColumnConfig(),
            autoHeight: true,
            selType: 'rowmodel',
            multiSelect: false,
            forceFit: true,
            editable: true,
            emptyText: 'No study products defined',
            dockedItems: [{
                xtype: 'toolbar',
                dock: 'top',
                border: false,
                items: [
                    {
                        text : 'Insert New',
                        scope : this,
                        handler : function() { this.showInsertUpdate(); }
                    },
                    {
                        text: 'Delete',
                        itemId: 'deleteProductBtn',
                        disabled: true,
                        scope: this,
                        handler: function() { this.removeProduct(this.grid); }
                    }]
            }],
            listeners:
            {
                scope: this,
                selectionchange : function(view, records) {
                    this.grid.down('#deleteProductBtn').setDisabled(!records.length);
                }
            }
        });

        // block the default LABKEY.ext4.GridPanel cellediting using beforeedit and add our own double click event
        this.grid.on('beforeedit', function(){ return false; });
        this.grid.on('celldblclick', this.showInsertUpdate, this);

        return this.grid;
    },

    getColumnConfig : function() {
        var columns = [
            { header: 'Row ID', dataIndex: 'RowId', editable: false, menuDisabled: true },
            { header: 'Label', dataIndex: 'Label', editable: true, menuDisabled: true, minWidth: 150, renderer: 'htmlEncode' },
            { header: 'Role', dataIndex: 'Role', editable: false, menuDisabled: true, renderer: 'htmlEncode' },
            { header: 'Type', dataIndex: 'Type', editable: true, menuDisabled: true, minWidth: 150, renderer: 'htmlEncode' }
        ];

        // set hidden columns and add editors where necessary
        Ext4.each(columns, function(col){

            if (col.dataIndex == 'Label')
            {
                col.editor = {
                    xtype: 'textfield',
                    fieldLabel: col.header,
                    name: col.dataIndex
                }
            }
            else if (col.dataIndex == 'Type')
            {
                col.editor = {
                    xtype : 'labkey-combo',
                    fieldLabel: col.header,
                    name: col.dataIndex,
                    forceSelection : false, // allow usage of inactive types
                    editable : false,
                    queryMode : 'local',
                    displayField : 'Label',
                    valueField : 'Name',
                    store : Ext4.create('LABKEY.ext4.Store', {
                        schemaName: 'study',
                        queryName: 'StudyDesignImmunogenTypes',
                        columns: 'Name,Label',
                        filterArray: [LABKEY.Filter.create('Inactive', false)],
                        containerFilter: LABKEY.container.type == 'project' ? 'Current' : 'CurrentPlusProject',
                        sort: 'Label',
                        autoLoad: true,
                        listeners: {
                            load: function(store) {
                                store.insert(0, {Name: null});
                            }
                        }
                    })
                };
            }

            if (this.hiddenColumns.indexOf(col.dataIndex) > -1)
            {
                col.hidden = true;
                col.editable = false;
                col.editor = null;
            }
        }, this);

        return columns;
    },

    showInsertUpdate : function(g, td, cellIndex, record)
    {
        if (cellIndex == undefined || cellIndex < 2)
        {
            var formItems = [];
            Ext4.each(this.getColumnConfig(), function(column){
                if (column.editable && column.editor)
                {
                    var formItem = column.editor;
                    formItem.value = record ? record.get(column.dataIndex) : null;
                    formItems.push(formItem);
                }
            });

            var win = Ext4.create('Ext.window.Window', {
                title: (record ? 'Edit ' : 'Insert ') + this.filterRole,
                cls: 'data-window',
                modal: true,
                items: [{
                    xtype: 'form',
                    border: false,
                    bodyStyle: 'padding: 5px;',
                    defaults: {labelWidth: 50, width: 275},
                    items: formItems,
                    buttonAlign: 'center',
                    buttons: [{
                        text: 'Submit',
                        formBind: true,
                        handler: function() {
                            var form = win.down('.form').getForm();
                            var values = form.getValues();

                            // either update the given record or add a new one
                            var command = 'insert';
                            if (record)
                            {
                                Ext4.applyIf(values, record.data);
                                command = 'update';
                            }

                            // always set the product role
                            values.Role = this.filterRole;

                            LABKEY.Query.saveRows({
                                commands: [{
                                    schemaName: 'study',
                                    queryName: 'Product',
                                    command: command,
                                    rows: [values]
                                }],
                                success: function(data) {
                                    win.close();

                                    // reload the grid store
                                    this.grid.getStore().reload();
                                },
                                scope: this
                            });
                        },
                        scope: this
                    },{
                        text: 'Cancel',
                        handler: function() { win.close(); }
                    }]
                }]
            });
            win.show();
        }
        else
        {
            this.showProductAntigenEdit(record);
        }
    },

    removeProduct : function(grid) {
        var record = grid.getSelectionModel().getLastSelected();
        if (record != null && record.get("RowId"))
        {
            // delete the study.Product record and any associated study.ProductAntigen records
            var commands = [{schemaName: 'study', queryName: 'Product', command: 'delete', rows: [record.data]}];
            if (record.get("Antigens").length > 0)
            {
                var rows = [];
                Ext4.each(record.get("Antigens"), function(rec) {
                    rows.push(rec);
                });
                commands.push({schemaName: 'study', queryName: 'ProductAntigen', command: 'delete', rows: rows});
            }

            LABKEY.Query.saveRows({
                commands: commands,
                success: function() {
                    grid.getStore().reload();
                },
                scope: this
            });
        }
    },

    showProductAntigenEdit : function(record)
    {}
});

Ext4.define('LABKEY.ext4.ImmunogensGrid', {

    extend : 'LABKEY.ext4.BaseVaccineDesignGrid',

    constructor : function(config) {
        Ext4.applyIf(config, {
            title : 'Immunogens',
            filterRole : 'Immunogen',
            width: 1100
        });

        this.callParent([config]);
    },

    getColumnConfig : function() {
        var columns = this.callParent();

        columns.push({ header: 'HIV Antigens', dataIndex: 'Antigens', editable: false, menuDisabled: true, minWidth: 500, renderer: function(value, metadata) {
            if (value && value.length > 0)
            {
                metadata.tdAttr = 'data-qtip="Double click this cell to edit the HIV Antigens for this Immunogen."';
            }
            else
            {
                metadata.tdAttr = 'data-qtip="Double click this cell to add HIV Antigens for this Immunogen."';
            }

            return new LABKEY.ext4.ImmunogensGridHelper().getHIVAntigenDisplay(value);
        }, scope: this});

        return columns;
    },

    showProductAntigenEdit : function(record) {
        var rowEditing = Ext4.create('Ext.grid.plugin.RowEditing', {
            clicksToMoveEditor: 1,
            autoCancel: false
        });

        var store = Ext4.create('Ext.data.Store', {
            fields: ['RowId', 'ProductId', 'Gene', 'SubType', 'GenBankId', 'Sequence'],
            data: record.data,
            proxy: {
                type: 'memory',
                reader: {type: 'json', root: 'Antigens'}
            }
        });

        var grid = Ext4.create('Ext.grid.Panel', {
            minHeight: 110,
            maxHeight: 200,
            autoScroll: true,
            forceFit: true,
            plugins: [rowEditing],
            store: store,
            columns: [
                { text: 'Row Id', dataIndex: 'RowId', editable: false, hidden: true, menuDisabled: true },
                { text: 'Product Id', dataIndex: 'ProductId', editable: false, hidden: true, menuDisabled: true },
                { text: 'Gene', dataIndex: 'Gene', editable: true, editor: this.getAntigenFieldEditor('Gene', 'StudyDesignGenes'), menuDisabled: true, renderer: 'htmlEncode' },
                { text: 'SubType', dataIndex: 'SubType', editable: true, editor: this.getAntigenFieldEditor('SubType', 'StudyDesignSubTypes'), menuDisabled: true, renderer: 'htmlEncode' },
                { text: 'GenBank Id', dataIndex: 'GenBankId', editable: true, editor: this.getAntigenFieldEditor('GenBankId'), menuDisabled: true, renderer: 'htmlEncode' },
                { text: 'Sequence', dataIndex: 'Sequence', editable: true, editor: this.getAntigenFieldEditor('Sequence'), menuDisabled: true, renderer: 'htmlEncode' }
            ],
            dockedItems: [{
                xtype: 'toolbar',
                dock: 'top',
                border: false,
                items: [
                    {
                        text : 'Insert New',
                        scope : this,
                        handler : function() {
                            rowEditing.cancelEdit();
                            store.add({RowId: null, ProductId: record.get("RowId")});
                            rowEditing.startEdit(store.getCount() - 1, 0);
                        }
                    },
                    {
                        text: 'Delete',
                        itemId: 'deleteAntigenBtn',
                        disabled: true,
                        scope: this,
                        handler: function() {
                            rowEditing.cancelEdit();
                            store.remove(grid.getSelectionModel().getLastSelected());
                        }
                    }]
            }],
            listeners:
            {
                scope: this,
                selectionchange : function(view, records) {
                    win.down('#deleteAntigenBtn').setDisabled(!records.length);
                }
            }
        });

        var win = Ext4.create('Ext.window.Window', {
            title: 'Edit HIV Antigens for ' + record.get("Label"),
            cls: 'data-window',
            modal: true,
            width: 550,
            items: [
                grid,
                {
                    xtype: 'label',
                    html: '<span style="font-style: italic; font-size: smaller;">* Double click to edit an existing row</span>'
                }
            ],
            buttons: [{
                text: 'Submit',
                scope: this,
                handler: function() {
                    // get new, updated, and removed records from the store and make saveRows commands accordingly
                    var commands = [];
                    var rows = [];
                    if (store.getRemovedRecords().length > 0)
                    {
                        rows = [];
                        Ext4.each(store.getRemovedRecords(), function(record) {
                            rows.push(record.data);
                        });
                        commands.push({schemaName: 'study', queryName: 'ProductAntigen', command: 'delete', rows: rows});
                    }
                    if (store.getUpdatedRecords().length > 0)
                    {
                        rows = [];
                        Ext4.each(store.getUpdatedRecords(), function(record) {
                            rows.push(record.data);
                        });
                        commands.push({schemaName: 'study', queryName: 'ProductAntigen', command: 'update', rows: rows});
                    }
                    if (store.getNewRecords().length > 0)
                    {
                        rows = [];
                        Ext4.each(store.getNewRecords(), function(record) {
                            rows.push(record.data);
                        });
                        commands.push({schemaName: 'study', queryName: 'ProductAntigen', command: 'insert', rows: rows});
                    }

                    if (commands.length > 0)
                    {
                        LABKEY.Query.saveRows({
                            commands: commands,
                            success: function(data) {
                                win.close();

                                // reload the Immunogens grid store
                                this.grid.getStore().reload();
                            },
                            scope: this
                        });
                    }
                    else
                        win.close();
                }
            },{
                text: 'Cancel',
                handler: function() { win.close(); }
            }]
        });
        win.show();
    },

    getAntigenFieldEditor : function(name, queryName) {
        if (queryName)
        {
            return {
                xtype : 'labkey-combo',
                hideFieldLabel: true,
                name: name,
                forceSelection : false, // allow usage of inactive types
                editable : false,
                queryMode : 'local',
                displayField : 'Label',
                valueField : 'Name',
                store : Ext4.create('LABKEY.ext4.Store', {
                    schemaName: 'study',
                    queryName: queryName,
                    columns: 'Name,Label',
                    filterArray: [LABKEY.Filter.create('Inactive', false)],
                    containerFilter: LABKEY.container.type == 'project' ? 'Current' : 'CurrentPlusProject',
                    sort: 'Label',
                    autoLoad: true,
                    listeners: {
                        load: function(store) {
                            store.insert(0, {Name: null});
                        }
                    }
                })
            };
        }
        else
        {
            return {
                xtype: 'textfield',
                name: name
            }
        }
    }
});

// helper function to be used by vaccine design webpart and manage study product page
Ext4.define('LABKEY.ext4.ImmunogensGridHelper', {

    getHIVAntigenDisplay : function(antigenArr) {
        var html = "";

        if (antigenArr && antigenArr.length > 0)
        {
            function getValue(antigen, name) {
                return Ext4.String.htmlEncode(antigen[name]) || "&nbsp;";
            }

            html += "<table class='labkey-data-region labkey-show-borders study-vaccine-design' style='width: 100%;border: solid #ddd 1px;'><tr>"
                    + "<td class='labkey-col-header'>Gene</td>"
                    + "<td class='labkey-col-header'>Subtype</td>"
                    + "<td class='labkey-col-header'>Sequence</td></tr>";

            Ext4.each(antigenArr, function(antigen){
                html += "<tr><td class='assay-row-padded-view'>" + getValue(antigen, 'Gene') + "</td>"
                        + "<td class='assay-row-padded-view'>" + getValue(antigen, 'SubType') + "</td>"
                        + "<td class='assay-row-padded-view'>"
                        + (antigen['GenBankId'] ? getValue(antigen, 'GenBankId') + ": " : "")
                        + getValue(antigen, 'Sequence') + "</td></tr>";
            });

            html += "</table>";
        }

        return html;
    }
});

Ext4.define('LABKEY.ext4.AdjuvantsGrid', {

    extend : 'LABKEY.ext4.BaseVaccineDesignGrid',

    constructor : function(config) {
        Ext4.applyIf(config, {
            title : 'Adjuvants',
            filterRole : 'Adjuvant',
            hiddenColumns : ["RowId", "Role", "Type", "Antigens"],
            width : 400
        });

        this.callParent([config]);
    }
});