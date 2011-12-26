/*
 * Copyright (c) 2011 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext.namespace('LABKEY');
LABKEY.requiresCss('Experiment/QCFlagToggle.css');
Ext.QuickTips.init();
var $h = Ext.util.Format.htmlEncode;

// function called onclick of Run QC Flags to open the QC Flag toggle panel (i.e. to enable/disable QC flags)
function qcFlagToggleWindow(assayName, runId)
{
    var win = new Ext.Window({
        cls: 'extContainer',
        title: 'Run QC Flags',
        width: 700,
        autoHeight: true,
        padding: 15,
        modal: true,
        closeAction:'close',
        bodyStyle: 'background-color: white;',
        items: new LABKEY.QCFlagTogglePanel({
            schemaName: "assay",
            queryName: assayName + " QCFlags",
            runId: runId,
            listeners: {
                scope: this,
                'closeWindow': function(){
                    win.close();
                }
            }
        })
    });
    win.show(this);
}

LABKEY.QCFlagTogglePanel = Ext.extend(Ext.Panel, {
    constructor : function(config){
        // check that the config properties needed are present
        if (!config.schemaName || !config.queryName || !config.runId)
            throw "You must specify a schemaName, queryName, and runId!";

        Ext.apply(config, {
            cls: 'extContainer',
            autoScroll: true,
            border: false,
            items: [],
            buttonAlign: 'center',
            buttons: [],
            userCanUpdate: LABKEY.user.canUpdate
        });

        this.addEvents('closeWindow');

        LABKEY.QCFlagTogglePanel.superclass.constructor.call(this, config);
    },

    initComponent : function() {
        // add a grid to the panel with the QC Flags for this run
        this.flagsGrid = new Ext.grid.EditorGridPanel({
            cls: 'extContainer',
            header: false,
            autoHeight: true,
            stripeRows: true,
            clicksToEdit: 1,
            loadMask:{msg:"Loading, please wait..."},
            store:  new LABKEY.ext.Store({
                schemaName: this.schemaName,
                queryName: this.queryName,
                filterArray: [LABKEY.Filter.create("Run/RowId", this.runId)],
                columns: "RowId, FlagType, Description, Comment, Enabled",
                sort: "FlagType, Description",
                autoLoad: true,
                listeners: {
                    scope: this,
                    'load': function(store, records, options){
                        store.purgeListeners();
                        this.addCheckColumn();
                    }
                }
            }),
            colModel: new Ext.grid.ColumnModel({
                columns: [
                    // the check column for the enabled dataIndex will be added on store load
                    {header: 'RowId', dataIndex: 'RowId', hidden: true},
                    {header: 'Flag', sortable: true, dataIndex: 'FlagType', width: 65, editable: false},
                    {header: 'Description', sortable: true, dataIndex: 'Description', width: 320, editable: false, renderer: this.tooltipRenderer},
                    {header: 'Comment', sortable: false, dataIndex: 'Comment', width: 250, editable: this.userCanUpdate,
                        renderer: this.tooltipRenderer, editor: new LABKEY.ext.LongTextField({columnName: 'Comment'})}
                ]
            }),
            selModel: new Ext.grid.CheckboxSelectionModel({moveEditorOnEnter: false}),
            viewConfig: {forceFit: true}
        });
        this.items = [this.flagsGrid];

        // add save and cancel button if the user can update, or just an OK button otherwise
        this.saveButton = new Ext.Button({
            text: 'Save',
            disabled: true,
            handler: this.saveQCFlagChanges,
            scope: this
        });
        this.cancelButton = new Ext.Button({
            text: 'Cancel',
            handler: function(){this.fireEvent('closeWindow');},
            scope: this
        });
        this.okButton = new Ext.Button({
            text: 'OK',
            handler: function(){this.fireEvent('closeWindow');},
            scope: this
        });
        this.buttons = this.userCanUpdate ? [this.saveButton, this.cancelButton] : [this.okButton];

        LABKEY.QCFlagTogglePanel.superclass.initComponent.call(this);
    },

    addCheckColumn : function() {
        var columns = this.flagsGrid.getColumnModel().columns;
        var checkCol = new Ext.grid.CheckColumn({
            header: 'Enabled',
            dataIndex: 'Enabled',
            width: 65,
            fixed: true,
            editable: false
        });

        // if the user can update, inititialize the column to be editable
        if (this.userCanUpdate)
            checkCol.init(this.flagsGrid);
        
        // add the enabled check column as the first column
        columns.splice(0, 0, checkCol);

        //reset the column model
        this.flagsGrid.reconfigure(this.flagsGrid.getStore(), new Ext.grid.ColumnModel(columns));

        // add listner to enabled save button on record update
        this.flagsGrid.getStore().on('update', function(){this.saveButton.enable()}, this);
    },

    saveQCFlagChanges: function() {
        var modifiedRecords = this.flagsGrid.getStore().getModifiedRecords();
        var updateRows = [];
        for (var i = 0; i < modifiedRecords.length; i++)
        {
            var record = modifiedRecords[i];
            updateRows.push({
                RowId: record.get("RowId"),
                Enabled: record.get("Enabled"),
                Comment: record.get("Comment")
            });
        }

        this.findParentByType('window').getEl().mask("Saving updates...", "x-mask-loading");
        LABKEY.Query.updateRows({
            schemaName: this.schemaName,
            queryName: this.queryName,
            rows: updateRows,
            success: function(){
                this.fireEvent('closeWindow');
                window.location.reload();
            },
            failure: function(info, response, options){
                if (this.findParentByType('window').getEl().isMasked())
                    this.findParentByType('window').getEl().unmask();

                LABKEY.Utils.displayAjaxErrorResponse(response, options);
            },
            scope: this
        })
    },

    tooltipRenderer: function(value, p, record) {
        var msg = $h(value);
        p.attr = 'ext:qtip="' + (msg ? $h(msg) : "") + '"';
        return msg;
    }
});
