/*
 * Copyright (c) 2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.define('LABKEY.ext4.BaseVaccineDesignGrid', {

    extend : 'Ext.panel.Panel',

    store : null,
    grid : null,
    title : null,
    emptyText : 'No records defined',
    filterRole : null,
    hiddenColumns : ["RowId"],
    visitNoun : 'Visit',
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

        Ext4.define('StudyDesign.Treatment', {
            extend : 'Ext.data.Model',
            fields : [
                {name : 'RowId', type : 'int'},
                {name : 'Label'},
                {name : 'Description'},
                {name : 'Products'}
            ]
        });

        Ext4.define('StudyDesign.Visit', {
            extend : 'Ext.data.Model',
            fields : [
                {name : 'RowId', type : 'int'},
                {name : 'Label'},
                {name : 'SortOrder', type : 'int'},
                {name : 'Included', type : 'boolean'}
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

        if (formItems.length == 0)
            return;

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
    },

    onFailure : function(text) {
        Ext4.Msg.show({
            cls: 'data-window',
            title: 'Error',
            msg: text || 'Unknown error occurred.',
            icon: Ext4.Msg.ERROR,
            buttons: Ext4.Msg.OK
        });
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
        Ext4.Msg.show({
            cls: 'data-window',
            title: "Confirm Deletion",
            msg: "Are you sure you want to delete the selected study product? Note: if this study product is being used "
                + "by any treatment definitions, those associations will also be deleted.",
            icon: Ext4.Msg.QUESTION,
            buttons: Ext4.Msg.YESNO,
            scope: this,
            fn: function(button){
                if (button === 'yes') {
                    this.deleteStudyProductRecord(grid);
                }
            }
        });
    },

    deleteStudyProductRecord : function(grid) {
        var record = grid.getSelectionModel().getLastSelected();
        if (record != null && record.get("RowId"))
        {
            // delete the study.Product record and any associated study.ProductAntigen records
            Ext4.Ajax.request({
                url     : LABKEY.ActionURL.buildURL('study', 'deleteStudyProduct.api'),
                method  : 'POST',
                jsonData: { id : record.get("RowId") },
                success : function(resp){
                    grid.getStore().reload();
                }
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
                            xtype: 'checkbox',
                            boxLabel: Ext4.String.htmlEncode(productRecord.get('Label')),
                            name: 'ProductCheckbox',
                            width: 175,
                            height: 22 * (Math.ceil(productRecord.get('Label').length / 22)), // hack at setting height based on label length
                            inputValue: productRecord.get('RowId'),
                            checked: selected,
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
                        var rowId = data.result[0].rows[0].rowid || data.result[0].rows[0].rowId;
                        var newRecord = store.findRecord('RowId', rowId);
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
        Ext4.Msg.show({
            cls: 'data-window',
            title: "Confirm Deletion",
            msg: "Are you sure you want to delete the selected treatment? Note: this will also delete any usages of this treatment record in the Immunization Schedule grid below.",
            icon: Ext4.Msg.QUESTION,
            buttons: Ext4.Msg.YESNO,
            scope: this,
            fn: function(button){
                if (button === 'yes') {
                    this.deleteTreatmentRecord(grid);
                }
            }
        });
    },

    deleteTreatmentRecord : function(grid) {
        var record = grid.getSelectionModel().getLastSelected();
        if (record != null && record.get("RowId"))
        {
            // delete the study.Treatment record and any associated study.TreatmentProductMap records
            Ext4.Ajax.request({
                url     : LABKEY.ActionURL.buildURL('study', 'deleteTreatment.api'),
                method  : 'POST',
                jsonData: { id : record.get("RowId") },
                success : function(resp){
                    // reload the page so that the treatments grid and the immunizations schedule grid are updated
                    window.location.reload();
                }
            });
        }
    }
});

// Ext4 widget that looks like the labkey textLink and allows for a handler function
Ext4.define('LinkButton', {
    extend: 'Ext.Component',
    alias: 'widget.linkbutton',

    autoEl: {
        tag: 'a',
        href: 'javascript:void(0)'
    },
    renderTpl: '{text}',
    baseCls: 'labkey-text-link',

    initComponent: function() {
        this.renderData = {
            text: this.text
        };

        this.callParent(arguments);
    },

    afterRender: function() {
        this.mon(this.getEl(), 'click', this.handler, this.scope || this);
    },

    handler: Ext4.emptyFn
});

Ext4.define('LABKEY.ext4.ImmunizationScheduleGrid', {

    extend : 'LABKEY.ext4.BaseVaccineDesignGrid',

    title : 'Immunization Schedule',
    filterRole : 'Cohort',
    hiddenColumns : ["RowId"],
    width : 300, // initial grid width for cohort label and count columns
    emptyText : 'No groups / cohorts defined',
    mappingData : null,
    treatmentStore : null,
    visitStore : null,

    constructor : function(config) {
        this.callParent([config]);
    },

    initComponent : function() {
        this.callParent();

        this.getImmunizationScheduleData(true);
    },

    getImmunizationScheduleData : function(init) {
        // query for the immunization schedule data (including all cohorts, all treatments, and mapping info for each cohort/visit/treatment)
        Ext4.Ajax.request({
            url : LABKEY.ActionURL.buildURL("study", "getStudyImmunizationSchedule"),
            method : 'GET',
            success : function(resp){
                var o = Ext4.decode(resp.responseText);
                if (o.success)
                {
                    // hold on to the mapping data
                    this.mappingData = o;

                    // load the treatment info into a store for easy access
                    this.mappingData.treatments.splice(0, 0, {Label: '[none]'});
                    this.treatmentStore = Ext4.create('Ext.data.Store', {
                        model : 'StudyDesign.Treatment',
                        data : this.mappingData.treatments,
                        sorters: [{ property: 'RowId', direction: 'ASC' }]
                    });

                    // load the visit info into a store for easy access and reuse
                    this.visitStore = Ext4.create('Ext.data.Store', {
                        model : 'StudyDesign.Visit',
                        data : this.mappingData.visits
                    });

                    if (init)
                    {
                        this.add(this.configureGrid());
                    }
                    else
                    {
                        Ext4.each(this.grid.columns, function(col){
                            var visitRec = this.visitStore.findRecord('RowId', col.dataIndex.replace('Visit',''));
                            if (visitRec)
                            {
                                if (col.hidden && visitRec.get('Included'))
                                {
                                    this.setWidth(this.width + 150);
                                    col.show();
                                }
                                else if (!col.hidden && !visitRec.get('Included'))
                                {
                                    this.setWidth(this.width - 150);
                                    col.hide();
                                }
                            }
                        }, this);
                    }

                    this.store.loadData(this.mappingData.mapping);
                }
            },
            failure : function(response, options){
                this.onFailure(response.exception);
            },
            scope : this
        });
    },

    configureGrid : function() {
        // don't do anything until we have the mapping data
        return this.mappingData ? this.callParent() : null;
    },

    configureStore : function() {
        // don't do anything until we have the mapping data
        if (this.mappingData)
        {
            var fields = [
                {name : 'RowId', type : 'int'},
                {name : 'Label'},
                {name : 'SubjectCount'}
            ];

            Ext4.each(this.visitStore.getRange(), function(visit){
                fields.push({name : 'Visit'+visit.get('RowId') });
            });

            Ext4.define('StudyDesign.Mapping', {
                extend : 'Ext.data.Model',
                fields : fields
            });

            this.store = Ext4.create('Ext.data.Store', {
                model : 'StudyDesign.Mapping',
                sorters: [{ property: 'CohortId', direction: 'ASC' }]
            });
        }

        return this.store;
    },

    getColumnConfig : function(ignoreVisitCols) {
        var columns = [
            { header: 'Row ID', dataIndex: 'RowId', editable: false, menuDisabled: true, width: 100 },
            { header: 'Group / Cohort', dataIndex: 'Label', editable: true, menuDisabled: true, width: 225 },
            { header: 'Count', dataIndex: 'SubjectCount', editable: true, menuDisabled: true, width: 75 }
        ];

        if (!ignoreVisitCols && this.visitStore)
        {
            Ext4.each(this.visitStore.getRange(), function(visit){
                columns.push({
                    header: visit.get('Label'),
                    dataIndex: 'Visit'+visit.get('RowId'),
                    editable: false,
                    menuDisabled: true,
                    width: 150,
                    hidden: !visit.get('Included'),
                    renderer: this.renderTreatment,
                    scope: this
                });

                if (visit.get('Included'))
                    this.width += 150;
            }, this);
        }

        // set hidden columns and add editors where necessary
        Ext4.each(columns, function(col){
            if (col.dataIndex == 'Label')
                col.editor = this.getStudyDesignFieldEditor(col.dataIndex, null, false, 'Label', false);
            else if (col.dataIndex == 'SubjectCount')
                col.editor = { xtype: 'numberfield', fieldLabel: col.header, name: col.dataIndex, minValue: 0, allowDecimals: false };

            if (this.hiddenColumns.indexOf(col.dataIndex) > -1)
            {
                col.hidden = true;
                col.editable = false;
                col.editor = null;
            }
        }, this);

        return columns;
    },

    renderTreatment : function(value, metaData, record) {
        var treatmentRecord = null;
        if (value != null && value != '')
            treatmentRecord = this.treatmentStore.findRecord('RowId', value);

        return treatmentRecord ? treatmentRecord.get('Label') : value;
    },

    showInsertUpdate : function(g, td, cellIndex, record)
    {
        var formItems = [];
        Ext4.each(this.getColumnConfig(true), function(column){
            if (column.editable && column.editor)
            {
                var formItem = column.editor;
                formItem.value = record ? record.get(column.dataIndex) : null;
                formItems.push(formItem);
            }
        });

        if (record)
        {
            formItems.push({ xtype: 'displayfield', value: ' ' }); // spacer
            formItems.push({ xtype: 'label', html: '<span style="font-weight: bold;">Treatment / ' + this.visitNoun + ' Mapping</span><br/>' });
            Ext4.each(this.visitStore.getRange(), function(visit){
                formItems.push(this.createTreatmentVisitCombo(record, visit));
            }, this);

            if (this.visitStore.getCount() > 0 && this.treatmentStore.getCount() > 1)
            {
                formItems.push({
                    xtype: 'linkbutton',
                    text: 'Add ' + this.visitNoun,
                    scope: this,
                    handler: function() {
                        this.showAddVisitWindow(record);
                    }
                });
            }
            else
            {
                formItems.push({
                    xtype: 'fieldcontainer', hideFieldLabel: true,
                    items: [{ xtype: 'label', text: 'No ' + (this.visitStore.getCount() == 0 ? this.visitNoun.toLowerCase() : 'treatment') + 's defined for this study.' }]
                });
            }
        }

        if (formItems.length == 0)
            return;

        var win = Ext4.create('Ext.window.Window', {
            title: (record ? 'Edit ' : 'Insert ') + this.filterRole,
            cls: 'data-window',
            modal: true,
            items: [{
                xtype: 'form',
                border: false,
                bodyStyle: 'padding: 5px;',
                defaults: {labelWidth: 120, width: 345},
                items: formItems,
                buttonAlign: 'center',
                buttons: [{
                    text: record ? 'Submit' : 'Next',
                    tooltip: record ? '' : 'Submit the new cohort record in order to define the treatment / ' + this.visitNoun.toLowerCase() + ' mapping.',
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

    createTreatmentVisitCombo : function(record, visit) {
        return {
            xtype: 'fieldcontainer',
            layout: 'hbox',
            fieldLabel: visit.get('Label'),
            visitRowId: visit.get('RowId'),
            hidden: !visit.get('Included'),
            items: [{
                xtype: 'combo',
                store: this.treatmentStore,
                editable: false,
                queryMode: 'local',
                displayField: 'Label',
                valueField: 'RowId',
                comboType: 'TreatmentVisitCombo', // for component query
                visitRowId: visit.get('RowId'),
                value: record.get("Visit"+visit.get('RowId'))
            },{
                xtype: 'linkbutton',
                text: 'Remove',
                visitRowId: visit.get('RowId'),
                style: 'padding-left: 10px;',
                handler: function() {
                    var cmp = Ext4.ComponentQuery.query('.fieldcontainer[visitRowId=' + this.visitRowId + ']');
                    if (cmp && cmp.length > 0)
                    {
                        cmp[0].down('.combo').setValue(null);
                        cmp[0].hide();
                    }
                }
            }]
        };
    },

    showAddVisitWindow : function(record) {
        var win = Ext4.create('Ext.window.Window', {
            itemId: 'MappingAddVisitWindow', // for component query
            title: 'Add ' + this.visitNoun,
            cls: 'data-window',
            modal: true,
            items: [{
                xtype: 'form',
                border: false,
                bodyStyle: 'padding: 5px;',
                items: [{
                    xtype: 'combo',
                    fieldLabel: 'Label',
                    labelWidth: 50,
                    width: 300,
                    store: this.visitStore,
                    editable: false,
                    queryMode: 'local',
                    displayField: 'Label',
                    valueField: 'RowId'
                }],
                buttonAlign: 'center',
                buttons: [{
                    text: 'Select',
                    formBind: true,
                    scope: this,
                    handler: function() {
                        // show the fieldcontainer visit/treatment combo for the select visit
                        var value = win.down('.form').down('.combo').getValue();
                        var cmp = Ext4.ComponentQuery.query('.fieldcontainer[visitRowId=' + value + ']');
                        if (cmp && cmp.length > 0)
                            cmp[0].show();

                        win.close();
                    }
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

        // either update the given cohort record or add a new one
        var cohortValues = {label: values.Label};
        if (values.SubjectCount)
            cohortValues.subjectCount = parseInt(values.SubjectCount);
        if (record)
            cohortValues.rowId = record.get('RowId');

        // pass an array of the form treatment/visit maps and it is up to the server to make insert and delete calls accordingly
        var treatmentVisitMapping = [];
        var treatmentVisitCombos = Ext4.ComponentQuery.query('combo[comboType=TreatmentVisitCombo]');
        Ext4.each(treatmentVisitCombos, function(combo){
            if (combo.value)
                treatmentVisitMapping.push({visitId: combo.visitRowId, treatmentId: combo.value});
        });

        Ext4.Ajax.request({
            url     : LABKEY.ActionURL.buildURL('study', 'updateStudyImmunizationSchedule.api'),
            method  : 'POST',
            jsonData: {
                cohort: cohortValues,
                treatmentVisitMapping: treatmentVisitMapping
            },
            success: function(response) {
                win.close();

                // for newly inserted cohort record, reshow the window for treatment / visit mapping definition
                var data = Ext4.decode(response.responseText);
                if (!record && data.cohortId)
                {
                    this.grid.getStore().on('datachanged', function(store){
                        var newRecord = store.findRecord('RowId', data.cohortId);
                        if (newRecord)
                            this.showInsertUpdate(this.grid, null, 0, newRecord);
                    }, this, {single: true});
                }

                // reload the grid store data
                this.getImmunizationScheduleData(false);
            },
            failure: function(response) {
                var resp = Ext4.decode(response.responseText);
                this.onFailure(resp.exception);
            },
            scope   : this
        });
    },

    removeSelectedGridRecord : function(grid) {
        Ext4.Msg.show({
            cls: 'data-window',
            title: "Confirm Deletion",
            msg: "Are you sure you want to delete the selected group / cohort and its associated treatment / " + this.visitNoun.toLowerCase() + " mapping records?",
            icon: Ext4.Msg.QUESTION,
            buttons: Ext4.Msg.YESNO,
            scope: this,
            fn: function(button){
                if (button === 'yes') {
                    this.deleteCohortRecord(grid);
                }
            }
        });
    },

    deleteCohortRecord : function(grid) {
        var record = grid.getSelectionModel().getLastSelected();
        if (record != null && record.get("RowId"))
        {
            // delete the study.Cohort record and any associated study.TreatmentProductMap records
            Ext4.Ajax.request({
                url     : LABKEY.ActionURL.buildURL('study', 'deleteStudyImmunizationSchedule.api'),
                method  : 'POST',
                jsonData: { id : record.get("RowId") },
                scope: this,
                success : function(resp){
                    // reload the grid store data
                    this.getImmunizationScheduleData(false);
                },
                failure : function(response) {
                    var resp = Ext4.decode(response.responseText);
                    this.onFailure(resp.exception);
                }
            });
        }
    }
});