/*
 * Copyright (c) 2011-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext.namespace('LABKEY');
LABKEY.requiresCss('Experiment/QCFlagToggle.css');
Ext.QuickTips.init();
var $h = Ext.util.Format.htmlEncode;

// helper function called to open the QC Flag toggle window (i.e. to enable/disable QC flags)
// and reload the page on a successful save
function showQCFlagToggleWindow(schemaName, runId, editable)
{
    var win = new LABKEY.QCFlagToggleWindow({
        schemaName: schemaName,
        queryName: "QCFlags",
        editable : editable,
        runId: runId,
        listeners: {
            scope: this,
            'saveSuccess': function(){
                window.location.reload();
            }
        }
    });
    win.show(this);
}

/**
 * Displays a dialog window to allow a user with update permissions to enable/disbled run QC flags (and add comments)
 * @param schemaName
 * @param queryName
 * @param runId
 * @param analyte Optional, for Luminex Levey-Jennings report
 * @param controlName Optional, for Luminex Levey-Jennings report
 * @param controlType Optional, for Luminex Levey-Jennings report
 */
LABKEY.QCFlagToggleWindow = Ext.extend(Ext.Window, {
    constructor : function(config){
        // check that the config properties needed are present
        if (!config.schemaName || !config.queryName || !config.runId)
            throw "You must specify a schemaName, queryName, and runId!";

        Ext.apply(config, {
            cls: 'extContainer qcflagtoggle',
            title: 'Run QC Flags',
            layout: 'fit',
            width: 700,
            height: 300,
            padding: 15,
            modal: true,
            closeAction:'close',
            bodyStyle: 'background-color: white;',
            items: [],
            userCanUpdate: config.editable && LABKEY.user.canUpdate
        });

        this.addEvents('saveSuccess');

        LABKEY.QCFlagToggleWindow.superclass.constructor.call(this, config);
    },

    initComponent : function() {
        // setup the filter array (always filter by run, optionally filter by analyte and controlName)
        var filters = [LABKEY.Filter.create("Run/RowId", this.runId)];
        if (this.analyte && this.controlName && this.controlType)
        {
            filters.push(LABKEY.Filter.create("Analyte/Name", this.analyte));
            filters.push(LABKEY.Filter.create((this.controlType == 'Titration' ? 'Titration' : 'SinglePointControl') + "/Name", this.controlName));
        }

        // add a grid to the panel with the QC Flags for this run
        this.flagsGrid = new Ext.grid.EditorGridPanel({
            cls: 'extContainer',
            header: false,
            height: 300,
            stripeRows: true,
            columnLines: true,
            clicksToEdit: 1,
            loadMask:{msg:"Loading, please wait..."},
            store:  new LABKEY.ext.Store({
                schemaName: this.schemaName,
                queryName: this.queryName,
                containerFilter: LABKEY.Query.containerFilter.allFolders,
                filterArray: filters,
                columns: "RowId, FlagType, Description, Comment, Enabled, Run/Folder/Path",
                sort: "FlagType, RowId",
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
                    {header: 'Flag', sortable: true, dataIndex: 'FlagType', width: 80, editable: false, renderer: this.encodingRenderer},
                    {header: 'Description', sortable: true, dataIndex: 'Description', width: 320, editable: false, renderer: this.encodingRenderer},
                    {header: 'Comment', sortable: false, dataIndex: 'Comment', width: 250, editable: this.userCanUpdate,
                        renderer: this.encodingRenderer, editor: new LABKEY.ext.LongTextField({columnName: 'Comment'})}
                ]
            }),
            selModel: new Ext.grid.CheckboxSelectionModel({moveEditorOnEnter: false}),
            viewConfig: {forceFit: true}
        });

        // add save and cancel button if the user can update, or just an OK button otherwise
        this.saveButton = new Ext.Button({
            text: 'Save',
            disabled: true,
            handler: this.saveQCFlagChanges,
            scope: this
        });
        this.cancelButton = new Ext.Button({
            text: 'Cancel',
            handler: function(){this.close();},
            scope: this
        });
        this.okButton = new Ext.Button({
            text: 'OK',
            handler: function(){this.close();},
            scope: this
        });

        this.items = [
            new Ext.Panel({
                cls: 'extContainer',
                layout: 'fit',
                border: false,
                items: [this.flagsGrid],
                buttonAlign: 'right',
                buttons: this.userCanUpdate ? [this.saveButton, this.cancelButton] : [this.okButton]
            })
        ];

        LABKEY.QCFlagToggleWindow.superclass.initComponent.call(this);
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
        var containerPath = LABKEY.container.path;
        for (var i = 0; i < modifiedRecords.length; i++)
        {
            var record = modifiedRecords[i];
            updateRows.push({
                RowId: record.get("RowId"),
                Enabled: record.get("Enabled"),
                Comment: record.get("Comment")
            });
            containerPath = record.get("Run/Folder/Path");
        }

        this.getEl().mask("Saving updates...", "x-mask-loading");
        LABKEY.Query.updateRows({
            schemaName: this.schemaName,
            queryName: this.queryName,
            containerPath: containerPath,
            rows: updateRows,
            success: function(){
                this.fireEvent('saveSuccess');
                this.close();
            },
            failure: function(info, response, options){
                if (this.getEl().isMasked())
                    this.getEl().unmask();

                LABKEY.Utils.displayAjaxErrorResponse(response, options);
            },
            scope: this
        })
    },

    encodingRenderer: function(value, p, record) {
        return $h(value);
    }
});
