/*
 * Copyright (c) 2013-2014 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.define('File.panel.FileProperties', {

    extend : 'Ext.Panel',

    title: 'File Properties',

    fileConfig: 'useDefault',

    initComponent : function() {

        this.items = [
            this.getHeader(),
            this.getPropertyChoice(),
            this.getPropertyEditButton(),
            this.getCustomPropertiesGrid()
        ];

        this.callParent();
    },

    getCustomPropertiesStore : function() {
        if (!Ext4.ModelManager.isRegistered('File.panel.CustomFilePropertiesStore')) {
            Ext4.define('File.panel.CustomFilePropertiesStore', {
                extend: 'Ext.data.Model',
                fields: [
                    {name: 'name', type: 'string'},
                    {name: 'label', type: 'string'},
                    {name: 'rangeURI', type: 'string'}
                ]
            });
        }

        return Ext4.create('Ext.data.Store', {
            model: 'File.panel.CustomFilePropertiesStore',
            proxy: {
                type: 'ajax',
                extraParams: {fileConfig: this.fileConfig},
                url: LABKEY.ActionURL.buildURL('pipeline', 'getPipelineFileProperties', this.containerPath),
                reader: {type: 'json', root: 'fileProperties'}
            },
            autoLoad: true
        });
    },

    getCustomPropertiesGrid : function() {
        return {
            xtype: 'grid',
            width: '100%',
            height: 300,
            margin: '20 0 0 0',
            store: this.getCustomPropertiesStore(),
            border: 1,
            columns: [
                {text: 'Name', dataIndex: 'name', flex: 1},
                {text: 'Label', dataIndex: 'label', flex: 1},
                {text: 'Type', dataIndex: 'rangeURI', flex: 1, renderer: function(val) {
                    return val.replace("http://www.w3.org/2001/XMLSchema#", "");
                }}
            ]
        }
    },

    getHeader : function() {
        return {
            border: false,
            html: '<span class="labkey-strong">Configure File Properties</span><p>Define additional properties to be collected with each file:</p>'
        };
    },

    getPropertyChoice : function() {
        return {
            xtype: 'radiogroup',
            itemId: 'fileConfigRadioGroup',
            columns: 1,
            margin: '0 0 5 25',
            vertical: true,
            listeners: {
                change: function(radio, newValue) {
                    this.fileConfig = newValue.fileConfig;
                    var btn = this.getComponent('editPropertiesBtn');
                    if (btn) { btn.setDisabled(!(this.fileConfig == 'useCustom')); }
                },
                scope: this
            },
            items: [{
                boxLabel: 'Use Default (none)',
                name: 'fileConfig',
                inputValue: 'useDefault',
                width: 250,
                checked: (this.fileConfig == 'useDefault' || this.fileConfig == null)
            },{
                boxLabel: 'Use Same Settings as Parent',
                name: 'fileConfig',
                inputValue: 'useParent',
                width: 250,
                checked: this.fileConfig == 'useParent'
            },{
                boxLabel: 'Use Custom File Properties',
                name: 'fileConfig',
                inputValue: 'useCustom',
                width: 250,
                checked: this.fileConfig == 'useCustom'
            }]
        };
    },

    getPropertyEditButton : function() {
        return {
            xtype: 'button',
            itemId: 'editPropertiesBtn',
            text: 'edit properties',
            disabled: this.fileConfig != 'useCustom',
            handler: function() { this.fireEvent('editfileproperties'); },
            scope: this
        }
    },

    //
    // Can return 'undefined'
    //
    getFileConfig : function() {
        var propertyChoice = this.getComponent('fileConfigRadioGroup');
        var fileConfig;
        if (propertyChoice) {
            fileConfig = propertyChoice.getValue().fileConfig;
        }
        return fileConfig;
    }
});
