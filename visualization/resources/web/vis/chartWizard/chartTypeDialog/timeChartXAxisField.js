/*
 * Copyright (c) 2016-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.define('LABKEY.vis.TimeChartXAxisField', {
    extend: 'Ext.form.Panel',

    border: false,
    bodyStyle: 'background-color: transparent;',

    initData: null,
    isVisitBased: false,

    initComponent : function()
    {
        if (this.initData == null)
            this.initData = {};

        this.isVisitBased = this.initData != null && this.initData.time == 'visit';

        this.items = [
            this.getTimeTypeRadioGroup(),
            this.getIntervalCombo(),
            this.getZeroDateColCombo()
        ];

        this.callParent();
    },

    getTimeTypeRadioGroup : function()
    {
        if (!this.timeTypeRadioGroup)
        {
            this.timeTypeRadioGroup = Ext4.create('Ext.form.RadioGroup', {
                columns: 2,
                items: [
                    {
                        name: 'time',
                        inputValue: 'date',
                        boxLabel: 'Date-Based',
                        checked: !this.isVisitBased
                    },
                    {
                        name: 'time',
                        inputValue: 'visit',
                        boxLabel: 'Visit-Based',
                        checked: this.isVisitBased,
                        disabled: LABKEY.vis.TimeChartHelper.getStudyTimepointType() == 'DATE'
                    }
                ],
                listeners: {
                    scope: this,
                    change: function(rg, newValue)
                    {
                        this.isVisitBased = newValue.time == 'visit';
                        this.getIntervalCombo().setDisabled(this.isVisitBased);
                        this.getZeroDateColCombo().setDisabled(this.isVisitBased);
                    }
                }
            });
        }

        return this.timeTypeRadioGroup;
    },

    getTimeTypeRadioByValue: function(value) {
        return this.getTimeTypeRadioGroup().down('radio[inputValue="' + value + '"]');
    },

    getIntervalCombo : function()
    {
        if (!this.intervalCombo)
        {
            this.intervalCombo = Ext4.create('Ext.form.field.ComboBox', {
                name: 'interval',
                fieldLabel: 'Time Interval',
                disabled: this.isVisitBased,
                labelWidth: 110,
                width: 295,
                padding: '0 0 0 5px',
                store: Ext4.create('Ext.data.ArrayStore', {
                    fields: ['value'],
                    data: [['Days'], ['Weeks'], ['Months'], ['Years']]
                }),
                queryMode: 'local',
                editable: false,
                forceSelection: true,
                displayField: 'value',
                valueField: 'value',
                value: this.initData.interval || 'Days'
            });
        }

        return this.intervalCombo;
    },

    getZeroDateColCombo : function()
    {
        if (!this.zeroDateColCombo)
        {
            this.zeroDateColCombo = Ext4.create('Ext.form.field.ComboBox', {
                name: 'zeroDateCol',
                fieldLabel: 'Interval Start Date',
                disabled: this.isVisitBased,
                labelWidth: 110,
                width: 295,
                padding: '0 0 0 5px',
                store: this.getZeroDateStore(),
                queryMode: 'local',
                valueField: 'longlabel',
                displayField: 'longlabel',
                forceSelection: true,
                editable: false
            });
        }

        return this.zeroDateColCombo;
    },

    getZeroDateStore : function()
    {
        if (!this.zeroDateStore)
        {
            this.zeroDateStore = Ext4.create('Ext.data.Store', {
                model: 'LABKEY.vis.DimensionModel',
                proxy: {
                    type: 'ajax',
                    url: LABKEY.ActionURL.buildURL('visualization', 'getZeroDate', LABKEY.ActionURL.getContainer(), {
                        filters: [LABKEY.Query.Visualization.Filter.create({schemaName: 'study'})],
                        dateMeasures: false
                    }),
                    reader: {
                        type: 'json',
                        root: 'measures',
                        idProperty: 'id'
                    }
                },
                autoLoad: true,
                sorters: {property: 'longlabel', direction: 'ASC'},
                listeners: {
                    scope: this,
                    load: function (store)
                    {
                        if (store.getCount() == 0)
                        {
                            // disable the Date-Based option and try to select the Visit-Based option
                            this.getTimeTypeRadioByValue('date').disable();
                            var visitRadio = this.getTimeTypeRadioByValue('visit');
                            if (!visitRadio.isDisabled())
                            {
                                visitRadio.setValue(true);
                            }
                            else
                            {
                                this.getIntervalCombo().hide();
                                this.getZeroDateColCombo().hide();
                                this.update('<div class="labkey-error">There are no demographic date options available '
                                    + 'in this study. Please contact an administrator to have them configure the study '
                                    + 'to work with the Chart Wizard.</div>');
                            }
                        }
                        else if (Ext4.isObject(this.initData.zeroDateCol))
                        {
                            // if this is a saved report, we will have a zero date to select
                            this.setZeroDateCol(this.initData.zeroDateCol);
                        }
                        else
                        {
                            // otherwise try a few hard-coded options, and default to the first record
                            var index = 0;
                            if (store.find('name', 'StartDate') > -1)
                                index = store.find('name', 'StartDate');
                            else if (store.find('name', 'EnrollmentDt') > -1)
                                index = store.find('name', 'EnrollmentDt');
                            else if (store.find('name', 'Date') > -1)
                                index = store.find('name', 'Date');

                            if(store.getAt(index))
                                this.setZeroDateCol(store.getAt(index).data);
                        }
                    }
                }
            });
        }

        return this.zeroDateStore;
    },

    setZeroDateCol: function(value)
    {
        if (this.getZeroDateStore().getCount() > 0)
        {
            var index = this.getZeroDateIndexByName(value.name, value.queryName);
            if (index > -1)
                this.getZeroDateColCombo().setValue(this.getZeroDateStore().getAt(index).get('longlabel'));
        }
    },

    getZeroDateIndexByName : function(colName, queryName)
    {
        return this.getZeroDateStore().findBy(function(record, id){
            return record.get('name') == colName && record.get('queryName') == queryName;
        }, this);
    },

    getZeroDateColData: function(){
        var value = this.getZeroDateColCombo().getValue();
        if (value)
        {
            var record = this.getZeroDateColCombo().findRecordByValue(value);
            if (record)
                return Ext4.clone(record.data);
        }

        return null;
    },

    getValue : function()
    {
        var values = {time: this.getTimeTypeRadioGroup().getValue().time};
        if (!this.isVisitBased)
        {
            values.interval = this.getIntervalCombo().getValue();
            values.zeroDateCol = this.getZeroDateColData();
        }

        return values;
    }
});