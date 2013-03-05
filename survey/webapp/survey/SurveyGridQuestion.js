/*
 * Copyright (c) 2012-2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.define('LABKEY.ext4.SurveyGridQuestion', {
    extend: 'Ext.grid.Panel',
    alias: 'widget.surveygridquestion',
    isFormField: true,
    submitValue: true,

    constructor : function(config) {

        Ext4.applyIf(config, {
            columns: [],
            store: null,
            border: true,
            forceFit: true
        });

        this.callParent([config]);

        // add listeners to the store to set the dirty state on add, update, remove
        if (this.getStore() && !this.readOnly)
        {
            this.getStore().addListener('add', this.gridChanged, this);
            this.getStore().addListener('remove', this.gridChanged, this);
            this.getStore().addListener('update', this.gridChanged, this);
        }
    },

    initComponent : function() {
        this.originalValue = this.value;
        this.storeCount = 0;
        this.dirty = false;

        if (!this.readOnly)
        {
            this.selModel = Ext4.create('Ext.selection.RowModel', {
                allowDeselect: true,
                listeners: {
                    scope: this,
                    selectionchange: function() {
                        this.down('#edit-selected-btn').setDisabled(!this.getSelectionModel().hasSelection());
                        this.down('#delete-selected-btn').setDisabled(!this.getSelectionModel().hasSelection());
                    }
                }
            });

            this.dockedItems = [{
                xtype: 'toolbar',
                dock: 'bottom',
                ui: 'footer',
                items: [{
                    text:'Add Record',
                    handler: function(){
                        // call method without a 'record' param to add a new entry
                        this.showUpdateRecordWindow();
                    },
                    scope: this
                },{
                    itemId: 'edit-selected-btn',
                    text:'Edit Selected',
                    disabled: true,
                    handler: function() {
                        var selectedArr = this.getSelectionModel().getSelection();
                        if (selectedArr)
                        {
                            // call method with a 'record' param to edit an entry
                            this.showUpdateRecordWindow(selectedArr[0]);
                        }
                    },
                    scope: this
                },{
                    itemId: 'delete-selected-btn',
                    text:'Delete Selected',
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

    gridChanged : function(setDirty) {
        this.setDirty(setDirty);
        this.fireEvent('change', this, this.getStore().getCount(), this.storeCount);
        this.storeCount = this.getStore().getCount();
    },

    getName : function() {
        return this.name;
    },

    resetOriginalValue : function() {
        this.originalValue = this.getValue();
    },

    clearValue : function() {
        this.getStore().loadData([]);
        this.gridChanged(true);
    },

    setValue : function(data) {
        this.getStore().loadData(data ? Ext4.decode(data) : []);
        this.gridChanged(false);
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

    getSubmitValue : function() {
        return this.getValue();
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

    clearInvalid : function() {
        // not implemented since there isn't really an invalid state for this question type
    },

    setReadOnly : function() {
        // not implemented, set via the question config instead
    },

    showUpdateRecordWindow : function(record) {
        // if we have a proper columns array for this component, add a window with the form panel
        if (this.columns)
        {
            var formItems = [];
            Ext4.each(this.columns, function(column){
                var formItem;
                if (!column.editor)
                    formItem = {xtype: 'displayfield'};
                else
                    formItem = column.editor;

                formItem.fieldLabel = column.text;
                formItem.name = column.dataIndex;
                formItem.value = record ? record.get(column.dataIndex) : null;

                formItems.push(formItem);
            });

            var win = Ext4.create('Ext.window.Window', {
                border: false,
                modal: true,
                title: record ? 'Edit Record' : ' Add Record',
                minHeight: 100,
                minWidth: 200,
                items: [{
                    itemId: 'recordWindowFormPanel',
                    xtype: 'form',
                    border: false,
                    bodyStyle: 'padding: 5px;',
                    items: formItems
                }],
                buttonAlign: 'center',
                buttons: [{
                    text: 'Update',
                    handler: function() {
                        // either update the given record or add a new one
                        var values = win.down('#recordWindowFormPanel').getForm().getValues();
                        if (record)
                        {
                            Ext4.each(values, function(val) {
                                record.set(val, values[val]);
                            });
                        }
                        else
                            this.getStore().add(values);

                        win.close();
                    },
                    scope: this
                },{
                    text: 'Cancel',
                    handler: function() { win.close(); }
                }]
            });
            win.show();
        }
    }
});



