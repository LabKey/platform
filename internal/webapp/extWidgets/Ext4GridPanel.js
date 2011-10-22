/*
 * Copyright (c) 2010-2011 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

LABKEY.requiresExt4ClientAPI();

Ext4.namespace('LABKEY.ext4');


Ext4.define('LABKEY.ext4.GridPanel', {
    extend: 'Ext.grid.Panel',
    alias: 'widget.labkey-gridpanel',
    config: {
        defaultFieldWidth: 200
    },
    initComponent: function(){

        this.store = this.store || this.createStore();

        Ext4.applyIf(this, {
            columns: [],
            pageSize: 200,
            autoSave: false,
            editable: true
        });

        this.configurePlugins();

        this.callParent();

        if(this.store.hasLoaded())
            this.setupColumnModel();
        else
            this.store.on('load', this.setupColumnModel, this, {single: true});

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
            this.plugins.push(Ext4.create('Ext.grid.plugin.CellEditing', {clicksToEdit: 2}));
    }

    ,setupColumnModel : function() {
        var columns = this.getColumnsConfig();

        //if a sel model has been set, and if it needs to be added as a column,
        //add it to the front of the list.
        //CheckBoxSelectionModel needs to be added to the column model for
        //the check boxes to show up.
        //(not sure why its constructor doesn't do this automatically).
//        if(this.getSelectionModel() && this.getSelectionModel().renderer)
//            columns = [this.getSelectionModel()].concat(columns);

        //register for the rowdeselect event if the selmodel supports events and if autoSave is on
        if(this.getSelectionModel().on && this.autoSave)
            this.getSelectionModel().on("rowselect", this.onRowSelect, this);

        //fire the "columnmodelcustomize" event to allow clients
        //to modify our default configuration of the column model
        this.fireEvent("columnmodelcustomize", this, columns);

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

        Ext4.each(columns, function(col, idx){
            var meta = this.store.findFieldMetadata(col.dataIndex);

            //remember the first editable column (used during add record)
            if(!this.firstEditableColumn && col.editable)
                this.firstEditableColumn = idx;

            if(meta.isAutoExpandColumn && !col.hidden){
                this.autoExpandColumn = idx;
            }
        }, this);

        return columns;
    }
    ,getColumnById: function(colName){
        return this.getColumnModel().getColumnById(colName);
    }

    ,onRowSelect: function(){
        //TODO.  autoSave on rows
    }

});