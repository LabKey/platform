
Ext4.define('LABKEY.ext4.BaseVaccineDesignGrid', {

    extend : 'Ext.panel.Panel',

    store : null,
    grid : null,
    title : null,
    emptyText : 'No records defined',
    filterRole : null,
    hiddenColumns : ["RowId"],
    frame  : false,

    constructor : function(config) {

        Ext4.QuickTips.init();

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

        this.items = [this.configureGrid()];

        this.callParent();
    },

    configureStore : function() {
        return this.store;
    },

    configureGrid : function() {

        this.grid = Ext4.create('LABKEY.ext4.GridPanel', {
            store: this.configureStore(),
            columns: this.getColumnConfig(),
            autoHeight: true,
            selType: 'rowmodel',
            multiSelect: false,
            forceFit: true,
            editable: true,
            emptyText: this.emptyText,
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
                        itemId: 'deleteRecordBtn',
                        disabled: true,
                        scope: this,
                        handler: function() { this.removeSelectedGridRecord(this.grid); }
                    }]
            }],
            listeners:
            {
                scope: this,
                selectionchange : function(view, records) {
                    this.grid.down('#deleteRecordBtn').setDisabled(!records.length);
                }
            }
        });

        // block the default LABKEY.ext4.GridPanel cellediting using beforeedit and add our own double click event
        this.grid.on('beforeedit', function(){ return false; });
        this.grid.on('celldblclick', this.showInsertUpdate, this);

        return this.grid;
    },

    getColumnConfig : function() {
        return [{ header: 'Row ID', dataIndex: 'RowId', editable: false, menuDisabled: true }];
    },

    showInsertUpdate : function(g, td, cellIndex, record)
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
                    scope: this,
                    handler: function() { this.insertUpdateGridRecord(win, record); }
                },{
                    text: 'Cancel',
                    handler: function() { win.close(); }
                }]
            }]
        });
        win.show();
    },

    insertUpdateGridRecord : function(win, record) {
        // No op
    },

    removeSelectedGridRecord : function(grid) {
        // No op
    },

    getStudyDesignFieldEditor : function(name, queryName, hideLabel, label, allowBlank) {
        if (queryName != null)
        {
            return {
                xtype : 'labkey-combo',
                hideFieldLabel: hideLabel,
                fieldLabel: hideLabel ? null : (label || name),
                name: name,
                allowBlank: allowBlank != undefined ? allowBlank : true,
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
                hideFieldLabel: hideLabel,
                fieldLabel: hideLabel ? null : (label || name),
                name: name,
                allowBlank: allowBlank != undefined ? allowBlank : true
            }
        }
    }
});

Ext4.define('LABKEY.ext4.StudyProductsGrid', {

    extend : 'LABKEY.ext4.BaseVaccineDesignGrid',

    title : 'Products',
    emptyText : 'No study products defined',
    hiddenColumns : ["RowId", "Role"],

    constructor : function(config) {
        this.callParent([config]);
    },

    configureStore : function() {
        this.store = Ext4.create('Ext.data.Store', {
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

        return this.store;
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
                col.editor = this.getStudyDesignFieldEditor(col.dataIndex, null, false, col.header, false);
            else if (col.dataIndex == 'Type')
                col.editor = this.getStudyDesignFieldEditor(col.dataIndex, 'StudyDesignImmunogenTypes', false, col.header, true);

            if (this.hiddenColumns.indexOf(col.dataIndex) > -1)
            {
                col.hidden = true;
                col.editable = false;
                col.editor = null;
            }
        }, this);

        return columns;
    },

    insertUpdateGridRecord : function(win, record) {
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
            success: function() {
                win.close();

                // reload the grid store
                this.grid.getStore().reload();
            },
            scope: this
        });
    },

    removeSelectedGridRecord : function(grid) {
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
    }
});

Ext4.define('LABKEY.ext4.ImmunogensGrid', {

    extend : 'LABKEY.ext4.StudyProductsGrid',

    title : 'Immunogens',
    filterRole : 'Immunogen',
    width: 1100,

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

            return new LABKEY.ext4.VaccineDesignDisplayHelper().getHIVAntigenDisplay(value);
        }, scope: this});

        return columns;
    },

    showInsertUpdate : function(g, td, cellIndex, record)
    {
        if (cellIndex == undefined || cellIndex < 2)
            this.callParent([g, td, cellIndex, record]);
        else
            this.showProductAntigenEdit(record);
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
                { text: 'Gene', dataIndex: 'Gene', editable: true, editor: this.getStudyDesignFieldEditor('Gene', 'StudyDesignGenes', true), menuDisabled: true, renderer: 'htmlEncode' },
                { text: 'SubType', dataIndex: 'SubType', editable: true, editor: this.getStudyDesignFieldEditor('SubType', 'StudyDesignSubTypes', true), menuDisabled: true, renderer: 'htmlEncode' },
                { text: 'GenBank Id', dataIndex: 'GenBankId', editable: true, editor: this.getStudyDesignFieldEditor('GenBankId', null, true), menuDisabled: true, renderer: 'htmlEncode' },
                { text: 'Sequence', dataIndex: 'Sequence', editable: true, editor: this.getStudyDesignFieldEditor('Sequence', null, true), menuDisabled: true, renderer: 'htmlEncode' }
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
    }
});

Ext4.define('LABKEY.ext4.VaccineDesignDisplayHelper', {

    // helper function to be used by vaccine design webpart and manage study product page
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
                        + (antigen['GenBankId'] ? getValue(antigen, 'GenBankId') : "")
                        + (antigen['GenBankId'] && antigen['Sequence'] ? ": " : "")
                        + getValue(antigen, 'Sequence') + "</td></tr>";
            });

            html += "</table>";
        }

        return html;
    },

    // helper function to be used by immunization schedule webpart and manage immunizations page
    getTreatmentProductDisplay : function(productArr) {
        function getValue(product, name) {
            return Ext4.String.htmlEncode(product[name]) || "&nbsp;";
        }

        var html = "";

        if (productArr && productArr.length > 0)
        {
            html += "<table class='labkey-data-region labkey-show-borders study-vaccine-design' style='width: 100%;border: solid #ddd 1px;'><tr>"
                    + "<td class='labkey-col-header'>Label</td>"
                    + "<td class='labkey-col-header'>Dose and units</td>"
                    + "<td class='labkey-col-header'>Route</td></tr>";

            Ext4.each(productArr, function(val){
console.log(val);
                html += "<tr><td class='assay-row-padded-view'>" + getValue(val, "ProductId/Label") + "</td>"
                        + "<td class='assay-row-padded-view'>" + getValue(val, "Dose") + "</td>"
                        + "<td class='assay-row-padded-view'>" + getValue(val, "Route") + "</td></tr>";
            });

            html += "</table>";
        }

        return html;
    }
});

Ext4.define('LABKEY.ext4.AdjuvantsGrid', {

    extend : 'LABKEY.ext4.StudyProductsGrid',

    title : 'Adjuvants',
    filterRole : 'Adjuvant',
    hiddenColumns : ["RowId", "Role", "Type", "Antigens"],
    width : 400
});

Ext4.define('LABKEY.ext4.TreatmentsGrid', {

    extend : 'LABKEY.ext4.BaseVaccineDesignGrid',

    title : 'Treatments',
    filterRole : 'Treatment',
    emptyText : 'No study treatments defined',
    width: 1000,
    productStore: null,

    constructor : function(config) {

        Ext4.define('StudyDesign.Treatment', {
            extend : 'Ext.data.Model',
            fields : [
                {name : 'RowId', type : 'int'},
                {name : 'Label'},
                {name : 'Description'},
                {name : 'Products'}
            ]
        });

        this.callParent([config]);
    },

    initComponent : function() {
        this.callParent();

        this.productStore = Ext4.create('Ext.data.Store', {
            model : 'StudyDesign.Product',
            proxy: {
                type: 'ajax',
                url : LABKEY.ActionURL.buildURL("study", "getStudyProducts"),
                reader: {
                    type: 'json',
                    root: 'products'
                }
            },
            sorters: [{ property: 'RowId', direction: 'ASC' }],
            autoLoad: true
        });
    },

    configureStore : function() {
        this.store = Ext4.create('Ext.data.Store', {
            model : 'StudyDesign.Treatment',
            proxy: {
                type: 'ajax',
                url : LABKEY.ActionURL.buildURL("study", "getStudyTreatments"),
                reader: {
                    type: 'json',
                    root: 'treatments'
                }
            },
            sorters: [{ property: 'RowId', direction: 'ASC' }],
            autoLoad: true
        });

        return this.store;
    },

    getColumnConfig : function() {
        var columns = [
            { header: 'Row ID', dataIndex: 'RowId', editable: false, menuDisabled: true },
            { header: 'Label', dataIndex: 'Label', editable: true, menuDisabled: true, minWidth: 150, renderer: 'htmlEncode' },
            { header: 'Description', dataIndex: 'Description', editable: true, menuDisabled: true, minWidth: 250, renderer: 'htmlEncode' },
            { header: 'Study Products', dataIndex: 'Products', editable: false, menuDisabled: true, minWidth: 500, renderer: function(value) {
                return new LABKEY.ext4.VaccineDesignDisplayHelper().getTreatmentProductDisplay(value);
            }}
        ];

        // set hidden columns and add editors where necessary
        Ext4.each(columns, function(col){
            if (col.dataIndex == 'Label')
                col.editor = this.getStudyDesignFieldEditor(col.dataIndex, null, false, col.header, false);
            else if (col.dataIndex == 'Description')
                col.editor = { xtype: 'textarea', fieldLabel: col.header, name: col.dataIndex };

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
        var recordProductIds = [];
        if (record)
        {
            Ext4.each(record.get('Products'), function(r){
                recordProductIds[r.ProductId] = r;
            });
        }

        var formItems = [];
        Ext4.each(this.getColumnConfig(), function(column){
            if (column.editable && column.editor)
            {
                var formItem = column.editor;
                formItem.value = record ? record.get(column.dataIndex) : null;
                formItem.labelWidth = 80;
                formItem.width = 485;
                formItems.push(formItem);
            }
        });

        if (record)
        {
            var productTypes = ['Immunogen', 'Adjuvant'];
            Ext4.each(productTypes, function(type){

                formItems.push({ xtype: 'label', text: type + 's', style: 'font-weight: bold;' });
                var productStoreRecords = this.productStore.query('Role', type).getRange();
                if (productStoreRecords.length > 0)
                {
                    Ext4.each(productStoreRecords, function(productRecord){

                        var selected = recordProductIds[productRecord.get('RowId')] != undefined;

                        var routeLookupField = this.getStudyDesignFieldEditor('Route', 'StudyDesignRoutes', true);
                        Ext4.apply(routeLookupField, {
                            id: Ext4.id(), emptyText: 'route', width: 150, disabled: !selected,
                            value: (selected ? recordProductIds[productRecord.get('RowId')].Route : null)
                        });

                        var doseTextField = {
                            xtype: 'textfield', id: Ext4.id(), name: 'Dose',
                            emptyText: 'dose and units', width: 150, disabled: !selected,
                            value: (selected ? recordProductIds[productRecord.get('RowId')].Dose : null)
                        };

                        var checkboxField = {
                            xtype: 'checkbox', boxLabel: productRecord.get('Label'), name: 'ProductCheckbox', width: 175,
                            inputValue: productRecord.get('RowId'), checked: selected,
                            listeners: {
                                change: function(cb, isChecked){
                                    Ext4.getCmp(doseTextField.id).setDisabled(!isChecked);
                                    Ext4.getCmp(routeLookupField.id).setDisabled(!isChecked);
                                }
                            }
                        };

                        formItems.push({
                            xtype: 'fieldcontainer',
                            hideFieldLabel: true,
                            layout: 'hbox',
                            items: [
                                checkboxField,
                                doseTextField,
                                { xtype: 'label', text: ' ', width: 10 }, // spacer
                                routeLookupField
                            ]
                        });

                    }, this);
                }
                else
                {
                    formItems.push({
                        xtype: 'fieldcontainer', hideFieldLabel: true,
                        items: [{ xtype: 'label', text: 'No ' + type.toLowerCase() + 's defined for this study.' }]
                    });
                }

            }, this);
        }

        var win = Ext4.create('Ext.window.Window', {
            title: (record ? 'Edit ' : 'Insert ') + this.filterRole,
            cls: 'data-window',
            modal: true,
            items: [{
                xtype: 'form',
                width: 515,
                maxHeight: 400,
                autoScroll: true,
                border: false,
                bodyStyle: 'padding: 5px;',
                items: formItems,
                buttonAlign: 'center',
                buttons: [{
                    text: record ? 'Submit' : 'Next',
                    tooltip: record ? '' : 'Submit the new treatment record in order to define which Immunogens and/or Adjuvants are used.',
                    formBind: true,
                    scope: this,
                    handler: function() { this.insertUpdateGridRecord(win, record); }
                },{
                    text: 'Cancel',
                    handler: function() { win.close(); }
                }]
            }]
        });
        win.show();
    },

    insertUpdateGridRecord : function(win, record) {
        var form = win.down('.form').getForm();
        var values = form.getValues();
        var commands = [];

        // either update the given record or add a new one for the study.Treatment table
        var command = 'insert';
        if (record)
        {
            Ext4.applyIf(values, record.data);
            command = 'update';

            // if only one product is selected, the values will not be arrays, so convert accordingly
            if (values['ProductCheckbox'])
            {
                if (!(values['ProductCheckbox'] instanceof Array))
                    values['ProductCheckbox'] = [values['ProductCheckbox']];
                if (!(values['Dose'] instanceof Array))
                    values['Dose'] = [values['Dose']];
                if (!(values['Route'] instanceof Array))
                    values['Route'] = [values['Route']];
            }

            // get the info for the study.TreatmentProductMap table (inserts, updates, and deletes based on form values)
            var origProducts = record.get('Products');
            var getTreatmentProductMapId = function(id) {
                for (var j = 0; j < origProducts.length; j++)
                {
                    if (origProducts[j].ProductId == id)
                    {
                        var mapId = origProducts[j].RowId;
                        origProducts.splice(j, 1);
                        return mapId;
                    }
                }
                return null;
            };

            // any products that are being inserted or updated will have their product checkbox selected
            if (values['ProductCheckbox'])
            {
                for (var i = 0; i < values['ProductCheckbox'].length; i++)
                {
                    var row = {
                        TreatmentId: record.get("RowId"),
                        ProductId: values['ProductCheckbox'][i],
                        Dose: values['Dose'][i],
                        Route: values['Route'][i]
                    };

                    var mapId = getTreatmentProductMapId(row.ProductId);
                    row.RowId = mapId;
                    commands.push({
                        schemaName: 'study', queryName: 'TreatmentProductMap',
                        command: mapId ? 'update' : 'insert', rows: [row]
                    });
                }
            }

            // any products that are being deleted will not have matched from above so will be left in the origProducts variable
            if (origProducts.length > 0)
                commands.push({ schemaName: 'study', queryName: 'TreatmentProductMap', command: 'delete', rows: origProducts });
        }

        commands.push({
            schemaName: 'study', queryName: 'Treatment',
            command: command, rows: [{RowId: values.RowId, Label: values.Label, Description: values.Description}]
        });

        LABKEY.Query.saveRows({
            commands: commands,
            success: function(data) {
                win.close();

                // for newly inserted treatment record, reshow the window for Immunogen and Adjuvant selection
                if (command == 'insert')
                {
                    this.grid.getStore().on('load', function(store, records){
                        var newRecord = store.findRecord('RowId', data.result[0].rows[0].rowid);
                        if (newRecord)
                            this.showInsertUpdate(this.grid, null, 0, newRecord);
                    }, this, {single: true});
                }

                // reload the grid store
                this.grid.getStore().reload();
            },
            scope: this
        });
    },

    removeSelectedGridRecord : function(grid) {
        var record = grid.getSelectionModel().getLastSelected();
        if (record != null && record.get("RowId"))
        {
            // delete the study.Treatment record and any associated study.TreatmentProductMap records
            var commands = [{schemaName: 'study', queryName: 'Treatment', command: 'delete', rows: [record.data]}];
            if (record.get("Products").length > 0)
            {
                var rows = [];
                Ext4.each(record.get("Products"), function(rec) {
                    rows.push(rec);
                });
                commands.push({schemaName: 'study', queryName: 'TreatmentProductMap', command: 'delete', rows: rows});
            }

            LABKEY.Query.saveRows({
                commands: commands,
                success: function() {
                    grid.getStore().reload();
                },
                scope: this
            });
        }
    }
});

Ext4.define('LABKEY.ext4.ImmunizationScheduleGrid', {

    extend : 'LABKEY.ext4.BaseVaccineDesignGrid',

    title : 'Immunization Schedule',
    disabled : true,
    width : 400,

    configureStore : function() {
        // TODO
        this.store = Ext4.define('Ext.data.ArrayStore', {});

        return this.store;
    }
});