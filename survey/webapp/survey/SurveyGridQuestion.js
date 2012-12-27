/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.define('LABKEY.ext4.SurveyGridQuestion', {
    extend: 'Ext.grid.Panel',
    alias: 'widget.surveygridquestion',
    isFormField: true,

    constructor : function(config) {

        Ext4.applyIf(config, {
            columns: [],
            store: null,
            border: true,
            scrollOffset: 10
        });

        this.callParent([config]);

        // add listeners to the store to set the dirty state on add, update, remove
        if (this.getStore() && !this.readOnly)
        {
            this.getStore().addListener('add', function(){ this.setDirty(true); }, this);
            this.getStore().addListener('remove', function(){ this.setDirty(true); }, this);
            this.getStore().addListener('update', function(){ this.setDirty(true); }, this);
        }
    },

    initComponent : function() {
        this.originalValue = this.value;
        this.dirty = false;

        if (!this.readOnly)
        {
            this.rowEditor = Ext4.create('Ext.grid.plugin.RowEditing', {
                clicksToEdit: 2
            });
            this.plugins = [this.rowEditor];

            this.selModel = Ext4.create('Ext.selection.RowModel', {
                allowDeselect: true,
                listeners: {
                    scope: this,
                    selectionchange: function() {
                        this.down('#remove-selected-btn').setDisabled(!this.getSelectionModel().hasSelection());
                    }
                }
            });

            this.dockedItems = [{
                xtype: 'toolbar',
                dock: 'bottom',
                ui: 'footer',
                items: [{
                    text:'Add Record',
                    handler: function() {
                        // if the grid height is too small, the row editor will be masked
                        if (this.getHeight() < 130)
                            this.minHeight = 130;

                        var record = this.getStore().add({});
                        this.rowEditor.startEdit(this.getStore().getCount() - 1, 0);
                    },
                    scope: this
                },{
                    itemId: 'remove-selected-btn',
                    text:'Remove Selected',
                    disabled: true,
                    handler: function() {
                        var selected = this.getSelectionModel().getSelection();
                        if (selected)
                            this.getStore().remove(selected);
                    },
                    scope: this
                }]
            }];
        }

        this.callParent();
    },

    getName : function() {
        return this.name;
    },

    resetOriginalValue : function() {
        this.originalValue = this.getValue();
    },

    clearValue : function() {
        this.getStore().loadData([]);
        this.setDirty(true);
    },

    setValue : function(data) {
        this.getStore().loadData(data ? Ext4.decode(data) : []);
        this.setDirty(false);
    },

    getValue : function() {
        // convert the store data into a JSON string
        var data = Ext4.pluck(this.getStore().data.items, 'data');
        if (data.length > 0)
            return Ext4.encode(data);
        else
            return null;
    },

    getSubmitData : function() {
        var data = null;
        if (this.getValue != null)
        {
            data = {};
            data[this.getName()] = this.getValue();
        }

        return data;
    },

    setDirty : function(isDirty) {
        if (this.dirty != isDirty)
            this.fireEvent('dirtychange', this, isDirty);

        this.dirty = isDirty;
    },

    isDirty : function() {
        return this.dirty;
    },

    isValid : function() {
        return true;
    },

    setReadOnly : function() {
        // not implemented, set via the question config instead
    }
});



