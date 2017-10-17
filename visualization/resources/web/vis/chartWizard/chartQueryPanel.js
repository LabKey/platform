/*
 * Copyright (c) 2016-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.define('LABKEY.vis.ChartQueryPanel', {
    extend: 'LABKEY.vis.ChartWizardPanel',

    cls: 'chart-wizard-panel chart-query-panel',
    mainTitle: 'Select a query',
    height: 220,
    width: 440,

    schemaName: null,

    initComponent: function ()
    {
        this.bottomButtons = [
            '->',
            this.getCancelButton(),
            this.getOkButton()
        ];

        this.items = [
            this.getTitlePanel(),
            this.getCenterPanel(),
            this.getButtonBar()
        ];

        this.callParent();

        this.addEvents('cancel', 'ok');
    },

    getCenterPanel : function()
    {
        if (!this.centerPanel)
        {
            this.centerPanel = Ext4.create('Ext.form.Panel', {
                region: 'center',
                cls: 'region-panel',
                items : [this.getSchemaCombo(), this.getQueryCombo()]
            });
        }

        return this.centerPanel;
    },

    getSchemaCombo : function()
    {
        if (!this.schemaCombo)
        {
            // hard-coded for now, we can change this to query for the schema listing later
            var store = Ext4.create('Ext.data.Store', {
                fields: ['name'],
                data: [
                    {name : 'assay'},
                    {name : 'lists'},
                    {name : 'study'}
                ]
            });

            this.schemaCombo = Ext4.create('Ext.form.field.ComboBox', {
                fieldLabel: 'Schema',
                name: 'schema',
                store: store,
                editable: false,
                value: this.schemaName,
                queryMode: 'local',
                displayField: 'name',
                valueField: 'name',
                emptyText: 'Select a schema',
                padding: '10px 10px 0 10px',
                labelWidth: 75,
                width: 375,
                listeners   : {
                    scope : this,
                    change : function(cmp, newValue)
                    {
                        this.getOkButton().disable();
                        this.getQueryCombo().disable();
                        this.getQueryCombo().clearValue();
                        this.schemaName = newValue;

                        var proxy = this.getQueryCombo().getStore().getProxy();
                        if (proxy)
                        {
                            proxy.extraParams = {schemaName : newValue};
                            this.getQueryCombo().getStore().load();
                        }

                        this.getQueryCombo().enable();
                    }
                }
            });
        }

        return this.schemaCombo;
    },

    getQueryCombo : function()
    {
        if (!this.queryCombo)
        {
            var store = Ext4.create('Ext.data.Store', {
                model   : 'LABKEY.vis.ChartQueryModel',
                autoLoad: false,
                pageSize: 10000,
                proxy   : {
                    type   : 'ajax',
                    url : LABKEY.ActionURL.buildURL('query', 'getQueries'),
                    extraParams : {
                        schemaName  : 'study'
                    },
                    reader : {
                        type : 'json',
                        root : 'queries'
                    }
                },
                sorters : [{property: 'name', direction: 'ASC'}]
            });

            this.queryCombo = Ext4.create('Ext.form.field.ComboBox', {
                disabled: this.schemaName == null,
                fieldLabel: 'Query',
                name: 'query',
                store: store,
                editable: false,
                allowBlank: false,
                triggerAction: 'all',
                typeAhead: true,
                displayField: 'queryLabel',
                valueField: 'name',
                emptyText: 'Select a query',
                padding: 10,
                labelWidth: 75,
                width: 375,
                listeners   : {
                    scope : this,
                    change : function(cmp, newValue)
                    {
                        var selected = cmp.getStore().getAt(cmp.getStore().find('name', newValue));
                        this.queryLabel = selected ? selected.data.title : null;
                        this.queryName = selected ? selected.data.name : null;
                        if (newValue)
                            this.getOkButton().enable();
                    }
                }
            });

            this.queryCombo.getStore().addListener('beforeload', function(){
                this.getOkButton().disable();
            }, this);
        }

        return this.queryCombo;
    },

    getCancelButton : function()
    {
        if (!this.cancelButton)
        {
            this.cancelButton = Ext4.create('Ext.button.Button', {
                text : 'Cancel',
                handler : this.cancelHandler
            });
        }

        return this.cancelButton;
    },

    cancelHandler : function()
    {
        window.history.back();
    },

    getOkButton : function()
    {
        if (!this.okButton)
        {
            this.okButton = Ext4.create('Ext.button.Button', {
                text : 'OK',
                disabled: true,
                scope   : this,
                handler : this.okHandler
            });
        }

        return this.okButton;
    },

    okHandler : function()
    {
        if (this.getCenterPanel().getForm().isValid())
            this.fireEvent('ok', this, this.schemaName, this.queryName, this.queryLabel);
    }
});

Ext4.define('LABKEY.vis.ChartQueryModel', {
    extend : 'Ext.data.Model',
    fields : [
        {name : 'name'},
        {name : 'title'},
        {name : 'queryLabel', convert: function(value, record){
            return record.data.name != record.data.title ? record.data.name + ' (' + record.data.title + ')' : record.data.title;
        }},
        {name : 'description'},
        {name : 'isUserDefined', type : 'boolean'}
    ]
});