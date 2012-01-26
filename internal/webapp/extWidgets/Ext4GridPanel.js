/*
 * Copyright (c) 2011-2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

LABKEY.requiresExt4ClientAPI();

Ext4.namespace('LABKEY.ext4');

/**
 * Constructs a new LabKey GridPanel using the supplied configuration.
 *
 * @param (boolean) [config.noAlertOnError] If true, no dialog will appear on if the store fires a syncerror event
 * @param {boolean} [config.hideNonEditableColumns] If true, columns that are non-editable will be hidden
 */

Ext4.define('LABKEY.ext4.GridPanel', {
    extend: 'Ext.grid.Panel',
    alias: 'widget.labkey-gridpanel',
    config: {
        defaultFieldWidth: 200,
        editable: true,
        pageSize: 200,
        autoSave: false,
        multiSelect: true
    },
    initComponent: function(){

        this.store = this.store || this.createStore();

        Ext4.QuickTips.init({
            constrainPosition: true
        });

        Ext4.applyIf(this, {
            columns: []
        });

        this.configurePlugins();

        this.callParent();

        //if we sort/filter remotely, we risk losing changes made on the client
        this.remoteSort = !this.editable;
        this.remoteFilter = !this.editable;

        if(this.autoSave)
            this.store.autoSync = true;  //could we just obligate users to put this on the store directly?

        if(this.store.hasLoaded()){
            this.setupColumnModel.defer(10, this);
        }
        else {
            this.mon(this.store, 'load', this.setupColumnModel, this, {single: true});
            this.store.load({ params : {
                start: 0,
                limit: this.pageSize
            }});
        }

        this.mon(this.store, 'syncexception', this.onCommitException, this);
        this.addEvents('beforesubmit', 'fieldconfiguration', 'recordchange', 'fieldvaluechange');
    }

    ,createStore: function(){
        return Ext4.create('LABKEY.ext4.Store', {
            containerPath: this.containerPath,
            schemaName: this.schemaName,
            queryName: this.queryName,
            sql: this.sql,
            viewName: this.viewName,
            columns: this.columns,
            storeId: LABKEY.ext.MetaHelper.getLookupStoreId(this),
            filterArray: this.filterArray || [],
            metadata: this.metadata,
            metadataDefaults: this.metadataDefaults,
            autoLoad: true
        });
    }

    //separated to allow subclasses to override
    ,configurePlugins: function(){
        this.plugins = this.plugins || [];
        if(this.editable)
            this.plugins.push(Ext4.create('Ext.grid.plugin.CellEditing', {pluginId: 'cellediting', clicksToEdit: 2}));

        if(!this.selModel){
            //NOTE: this is overridden in order to prevent the grid from focusing to itself on mouseclick.  Ext has bugs on this and it may be fixed in versions above 4.0.7
            this.selModel = {
                xtype: 'rowmodel',
                //@Override
                onRowMouseDown: function(view, record, item, index, e) {
                    //view.el.focus();
                    if (!this.allowRightMouseSelection(e)) {
                        return;
                    }
                    this.selectWithEvent(record, e);
                }
            }
        }
    }

    ,setupColumnModel : function() {
        var columns = this.getColumnsConfig();

        //fire the "columnmodelcustomize" event to allow clients
        //to modify our default configuration of the column model
        this.fireEvent("columnmodelcustomize", this, columns);

        this.columns = columns;

        //reset the column model
        this.reconfigure(this.store, columns);

    }
    ,getColumnsConfig: function(){
        var config = {
            editable: this.editable,
            defaults: {
                sortable: false
            }
        };

        var columns = LABKEY.ext.MetaHelper.getColumnsConfig(this.store, this, config);

        var flexWidth = 0;
        var fixedWidth = 0;
        Ext4.each(columns, function(col, idx){
            var meta = this.store.findFieldMetadata(col.dataIndex);

            //remember the first editable column (used during add record)
            if(!this.firstEditableColumn && col.editable)
                this.firstEditableColumn = idx;

            if(meta.isAutoExpandColumn && !col.hidden){
                this.autoExpandColumn = idx;
            }

            if(this.hideNonEditableColumns && !col.editable)
                col.hidden = true;
            else {
                if(meta.fixedWidthColumn)
                    fixedWidth += col.width || 0;
                else
                    flexWidth += col.width || 0;
            }
        }, this);

        //the intent is to distribute width proportionally across columns using the Ext4 flex property
        Ext4.each(columns, function(col, idx){
            var meta = this.store.findFieldMetadata(col.dataIndex);

            if(!col.hidden && !meta.fixedWidthColumn){
                col.flex = (col.width / flexWidth);
                delete col.width;
            }
        }, this);
        return columns;
    }
    ,getColumnById: function(colName){
        return this.getColumnModel().getColumnById(colName);
    }

    ,onCommitException: function(response, operation){
        var msg;
        if(response.errors && response.errors.exception)
            msg = response.errors.exception;
        else
            msg = 'There was an error with the submission';

        if(!this.noAlertOnError)
            Ext4.Msg.alert('Error', msg);
    }
});

LABKEY.ext4.GRIDBUTTONS = {
    ADDRECORD: function(config){
        return Ext4.Object.merge({
            text: 'Add Record',
            tooltip: 'Click to add a row',
            handler: function(btn){
                var grid = btn.up('gridpanel');
                if(!grid.store || !grid.store.hasLoaded())
                    return;

                grid.getPlugin('cellediting').completeEdit( );
                grid.store.insert(grid.store.createModel({}), 0); //add a blank record in the first position
                grid.getPlugin('cellediting').startEditByPosition({row: 0, column: this.firstEditableColumn || 0});
            }
        }, config);
    },
    DELETERECORD: function(config){
        return Ext4.Object.merge({
            text: 'Delete Records',
            tooltip: 'Click to delete selected rows',
            handler: function(btn){
                var grid = btn.up('gridpanel');
                var selections = grid.getSelectionModel().getSelection();

                if(!grid.store || !selections || !selections.length)
                    return;

                grid.store.remove(selections);
            }
        });
    },
    SUBMIT: function(config){
        return Ext4.Object.merge({
            text: 'Submit',
            formBind: true,
            handler: function(btn, key){
                var panel = btn.up('gridpanel');
                panel.store.on('write', function(store, success){
                    Ext4.Msg.alert("Success", "Your upload was successful!", function(){
                        window.location = btn.successURL || LABKEY.ActionURL.buildURL('query', 'executeQuery', null, {schemaName: this.store.schemaName, 'query.queryName': this.store.queryName})
                    }, panel);
                }, this);
                panel.store.sync();
            }
        }, config);
    },
    CANCEL: function(config){
        return Ext4.Object.merge({
            text: 'Cancel',
            handler: function(btn, key){
                window.location = btn.returnURL || LABKEY.ActionURL.getParameter('srcURL') || LABKEY.ActionURL.buildURL('project', 'begin')
            }
        }, config)
    }
}
