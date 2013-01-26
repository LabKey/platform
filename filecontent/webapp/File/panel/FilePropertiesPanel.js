/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.define('File.panel.FileProperties', {

    extend : 'Ext.panel.Panel',

    constructor : function(config) {

        Ext4.apply(config, {
            defaults: {
                xtype: 'panel',
                border: false,
                margin: '5 0 0 0'
            }
        });

        Ext4.applyIf(config, {
            fileConfig: 'useDefault'
        });

        this.callParent([config]);

    },

    initComponent : function() {
        this.title = 'File Properties';
        this.items = this.getItems();
        this.callParent();
    },

    getItems: function(){
        var items = [];
        items.push({html: '<p>Define additional properties to be collected with each file:</p>'});
        this.fileConfigRadioGroup = Ext4.create('Ext.form.RadioGroup',{
            columns: 1,
            margin: '0 0 0 25',
            vertical: true,
            listeners: {
                scope: this,
                change: function(radio, newValue){
                    this.fileConfig = newValue.fileConfig;
                    if(this.fileConfig == 'custom'){
                        this.editPropertiesButton.setDisabled(false);
                    } else {
                        this.editPropertiesButton.setDisabled(true);
                    }
                }
            },
            items: [
                {
                    boxLabel: 'Use Default (none)',
                    name: 'fileConfig',
                    inputValue: 'useDefault',
                    width: 250,
                    checked: (this.fileConfig == 'useDefault' || this.fileConfig == null)
                }, {
                    boxLabel: 'Use Same Settings as Parent',
                    name: 'fileConfig',
                    inputValue: 'useParent',
                    width: 250,
                    checked: this.fileConfig == 'useParent'
                }, {
                    boxLabel: 'Use Custom File Properties',
                    name: 'fileConfig',
                    inputValue: 'useCustom',
                    width: 250,
                    checked: this.fileConfig == 'useCustom'
                }
            ]
        });
        items.push(this.fileConfigRadioGroup);

        this.editPropertiesButton = Ext4.create('Ext.button.Button', {
            text: 'edit properties',
            disabled: this.fileConfig != 'useCustom',
            handler: function(){
                this.fireEvent('editfileproperties');
            },
            scope: this
        });
        items.push(this.editPropertiesButton);

        items.push(this.getCustomPropertiesGrid());

        return items;
    },

    getCustomPropertiesStore: function(){
        Ext4.define('File.panel.CustomFilePropertiesStore', {
            extend: 'Ext.data.Model',
            fields: [
                {name: 'name', type: 'string'},
                {name: 'label', type: 'string'},
                {name: 'rangeURI', type: 'string'}
            ]
        });

         return Ext4.create('Ext.data.Store', {
             model: 'File.panel.CustomFilePropertiesStore',
             proxy: {
                 type: 'ajax',
                 extraParams: {fileConfig: this.fileConfig},
                 url: LABKEY.ActionURL.buildURL("pipeline", "getPipelineFileProperties", this.containerPath),
                 reader: {type: 'json', root: 'fileProperties'}
             },
             autoLoad: true
        });
    },

    getCustomPropertiesGrid: function(){
        return {
            xtype: 'grid',
            width: '100%',
            margin: '25 0 0 0',
            store: this.getCustomPropertiesStore(),
            border: 1,
            columns: [
                {text: 'Name', dataIndex: 'name', flex: 1},
                {text: 'Label', dataIndex: 'label', flex: 1},
                {text: 'Type', dataIndex: 'rangeURI', flex: 1}
            ]
        }
    },

    getFileConfig: function(){
        return this.fileConfigRadioGroup.getValue().fileConfig;
    }

});