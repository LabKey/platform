/*
 * Copyright (c) 2016-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.define('LABKEY.VaccineDesign.VisitWindow', {
    extend: 'Ext.window.Window',

    modal: true,

    visitStore: null,

    visitNoun: 'Visit',

    constructor: function(config)
    {
        this.callParent([config]);
        this.addEvents('closewindow', 'selectexistingvisit', 'newvisitcreated');
    },

    initComponent: function()
    {
        this.isTimepoint = this.visitNoun.toLowerCase() == 'timepoint';
        this.items = [this.getFormPanel()];
        this.callParent();
    },

    getFormPanel : function()
    {
        if (!this.formPanel)
        {
            this.formPanel = Ext4.create('Ext.form.Panel',{
                border: false,
                padding: 10,
                items: [
                    this.getExistingVisitRadio(),
                    this.getExistingVisitCombo(),
                    this.getNewVisitRadio(),
                    this.getNewVisitLabelField(),
                    this.getNewVisitMinMaxContainer()
                ],
                buttons: [
                    this.getSelectBtn(),
                    this.getCancelBtn()
                ]
            });
        }

        return this.formPanel;
    },

    getExistingVisitRadio : function()
    {
        if (!this.existingVisitRadio)
        {
            this.existingVisitRadio = Ext4.create('Ext.form.field.Radio', {
                name: 'visitType',
                disabled: this.getFilteredVisitStore().getCount() == 0,
                inputValue: 'existing',
                boxLabel: 'Select an existing study ' + this.visitNoun.toLowerCase() + ':',
                checked: this.getFilteredVisitStore().getCount() > 0,
                hideFieldLabel: true,
                width: 300
            });
        }

        return this.existingVisitRadio;
    },

    getNewVisitRadio : function()
    {
        if (!this.newVisitRadio)
        {
            this.newVisitRadio = Ext4.create('Ext.form.field.Radio', {
                name: 'visitType',
                inputValue: 'new',
                boxLabel: 'Create a new study ' + this.visitNoun.toLowerCase() + ':',
                checked: this.getFilteredVisitStore().getCount() == 0,
                hideFieldLabel: true,
                width: 300
            });

            this.newVisitRadio.on('change', function(radio, newValue){
                this.getExistingVisitCombo().setDisabled(newValue);
                this.getNewVisitLabelField().setDisabled(!newValue);
                this.getNewVisitMinMaxContainer().setDisabled(!newValue);
                this.getSelectBtn().setText(newValue ? 'Submit' : 'Select');
                this.updateSelectBtnState();

                if (newValue)
                    this.getNewVisitLabelField().focus();
                else
                    this.getExistingVisitCombo().focus();
            }, this);
        }

        return this.newVisitRadio;
    },

    getExistingVisitCombo : function()
    {
        if (!this.existingVisitCombo)
        {
            this.existingVisitCombo = Ext4.create('Ext.form.field.ComboBox', {
                name: 'existingVisit',
                disabled: this.getFilteredVisitStore().getCount() == 0,
                hideFieldLabel: true,
                style: 'margin-left: 15px;',
                width: 300,
                store: this.getFilteredVisitStore(),
                editable: false,
                queryMode: 'local',
                displayField: 'Label',
                valueField: 'RowId'
            });

            this.existingVisitCombo.on('change', this.updateSelectBtnState, this);
        }

        return this.existingVisitCombo;
    },

    getFilteredVisitStore : function()
    {
        if (!this.filteredVisitStore)
        {
            var data = [];
            if (this.visitStore != null)
            {
                Ext4.each(this.visitStore.query('Included', false).items, function(record)
                {
                    var recData = Ext4.clone(record.data);
                    recData['Label'] = recData['Label'] || recData['SequenceNumMin'];
                    data.push(recData);
                }, this);
            }

            // add an option to select all existing visits for display
            if (data.length > 1)
                data.push({Label: '[Show All]', RowId: -1, DisplayOrder: -999999});

            this.filteredVisitStore = Ext4.create('Ext.data.Store', {
                model : 'LABKEY.VaccineDesign.Visit',
                data : data,
                sorters : [{property: 'DisplayOrder', direction: 'ASC'},{property: 'SequenceNumMin', direction: 'ASC'}]
            });
        }

        return this.filteredVisitStore;
    },

    getNewVisitLabelField : function()
    {
        if (!this.newVisitLabelField)
        {
            this.newVisitLabelField = Ext4.create('Ext.form.field.Text', {
                name: 'newVisitLabel',
                disabled: this.getFilteredVisitStore().getCount() > 0,
                fieldLabel: 'Label',
                labelWidth: 50,
                width: 300,
                style: 'margin-left: 15px;'
            });

            this.newVisitLabelField.on('change', this.updateSelectBtnState, this);
        }

        return this.newVisitLabelField;
    },

    getNewVisitMinField : function()
    {
        if (!this.newVisitMinField)
        {
            this.newVisitMinField = Ext4.create('Ext.form.field.Number', {
                name: 'newVisitRangeMin',
                hideLabel: true,
                width: this.isTimepoint ? 100 : 80,
                emptyText: 'min',
                hideTrigger: true,
                decimalPrecision: 4
            });

            this.newVisitMinField.on('change', this.updateSelectBtnState, this);
        }

        return this.newVisitMinField;
    },

    getNewVisitMaxField : function()
    {
        if (!this.newVisitMaxField)
        {
            this.newVisitMaxField = Ext4.create('Ext.form.field.Number', {
                name: 'newVisitRangeMax',
                hideLabel: true,
                width: this.isTimepoint ? 100 : 80,
                emptyText: 'max',
                hideTrigger: true,
                decimalPrecision: 4
            });

            this.newVisitMinField.on('change', this.updateSelectBtnState, this);
        }

        return this.newVisitMaxField;
    },

    getNewVisitMinMaxContainer : function()
    {
        if (!this.newVisitMinMaxContainer)
        {
            this.newVisitMinMaxContainer = Ext4.create('Ext.form.FieldContainer', {
                layout: 'hbox',
                style: 'margin-left: 15px; margin-bottom: 15px;',
                fieldLabel: (this.isTimepoint ? 'Day' : 'Sequence') + ' Range',
                labelWidth: this.isTimepoint ? 85 : 125,
                disabled: this.getFilteredVisitStore().getCount() > 0,
                items: [
                    this.getNewVisitMinField(),
                    {xtype: 'label', width: 10}, // spacer
                    this.getNewVisitMaxField()
                ]
            });
        }

        return this.newVisitMinMaxContainer;
    },

    getSelectBtn : function()
    {
        if (!this.selectBtn)
        {
            this.selectBtn = Ext4.create('Ext.button.Button', {
                text: this.getFilteredVisitStore().getCount() == 0 ? 'Submit' : 'Select',
                disabled: true,
                scope: this,
                handler: function() {
                    var values = this.getFormPanel().getValues();

                    if (values['visitType'] == 'existing')
                        this.fireEvent('selectexistingvisit', this, values['existingVisit'] == -1 ? 'ALL' : values['existingVisit']);
                    else
                        this.createNewVisit();
                }
            });
        }

        return this.selectBtn;
    },

    updateSelectBtnState : function()
    {
        var values = this.getFormPanel().getValues();

        if (values['visitType'] == 'existing')
            this.getSelectBtn().setDisabled(values['existingVisit'] == '');
        else
            this.getSelectBtn().setDisabled(values['newVisitLabel'] == '' || values['newVisitRangeMin'] == '');
    },

    getCancelBtn : function()
    {
        if (!this.cancelBtn)
        {
            this.cancelBtn = Ext4.create('Ext.button.Button', {
                text: 'Cancel',
                scope: this,
                handler: function() {
                    this.fireEvent('closewindow', this);
                }
            });
        }

        return this.cancelBtn;
    },

    createNewVisit : function()
    {
        this.getEl().mask('Creating new visit...');
        var values = this.getFormPanel().getValues();

        LABKEY.Ajax.request({
            url     : LABKEY.ActionURL.buildURL('study-design', 'createVisit.api'),
            method  : 'POST',
            jsonData: {
                label: values['newVisitLabel'],
                sequenceNumMin: values['newVisitRangeMin'],
                sequenceNumMax: values['newVisitRangeMax'],
                showByDefault: true
            },
            success: function(response) {
                var resp = Ext4.decode(response.responseText);
                if (resp.success)
                    this.fireEvent('newvisitcreated', this, resp);
                else
                    this.onFailure();
            },
            failure: function(response) {
                var resp = Ext4.decode(response.responseText);
                this.onFailure(resp.exception);
            },
            scope   : this
        });
    },

    onFailure : function(text)
    {
        Ext4.Msg.show({
            title: 'Error',
            msg: text || 'Unknown error occurred.',
            icon: Ext4.Msg.ERROR,
            buttons: Ext4.Msg.OK
        });

        this.getEl().unmask();
    }
});