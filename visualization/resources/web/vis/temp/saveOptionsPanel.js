/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
LABKEY.requiresExt4ClientAPI();

Ext4.namespace("LABKEY.vis");

Ext4.QuickTips.init();
$h = Ext4.util.Format.htmlEncode;

Ext4.define('LABKEY.vis.ChartEditorSavePanel', {

    extend : 'Ext.form.Panel',

    constructor : function(config){
        Ext4.applyIf(config, {
            header: false,
            border: false,
            autoHeight: true,
            autoWidth: true,
            padding: 5,
            monitorValid: true,
            items: [],
            buttonAlign: 'right',
            buttons: []
        });

        this.callParent([config]);

        this.addEvents(
            'saveChart',
            'closeOptionsWindow'
        );
    },

    initComponent : function() {
        this.isSaveAs = false;

        // Note that Readers are allowed to save new charts (readers own new charts they're creating)- this is by design.
        this.currentlyShared = (this.isSavedReport() && this.reportInfo.shared) || (!this.isSavedReport() && this.canSaveSharedCharts());
        this.createdBy = this.isSavedReport() ? this.reportInfo.createdBy : LABKEY.Security.currentUser.id;

        this.items = [
            Ext4.create('Ext.form.field.Text', {
                itemId: 'reportName',
                name: 'reportName',
                fieldLabel: 'Report Name',
                labelWidth: 125,
                hidden: this.isSavedReport() || !this.canSaveChanges(),
                value: (this.isSavedReport() ? this.reportInfo.name : null),
                allowBlank: true,
                anchor: '100%',
                maxLength: 200
            }),
            Ext4.create('Ext.form.field.Display', {
                itemId: 'reportNameDisplay',
                name: 'reportNameDisplay',
                fieldLabel: 'Report Name',
                labelWidth: 125,
                hidden: !this.isSavedReport() && this.canSaveChanges(),
                value: $h(this.isSavedReport() ? this.reportInfo.name : null),
                anchor: '100%'
            }),
            Ext4.create('Ext.form.field.TextArea', {
                itemId: 'reportDescription',
                name: 'reportDescription',
                fieldLabel: 'Report Description',
                labelWidth: 125,
                hidden: !this.canSaveChanges(),
                value: (this.isSavedReport() ? this.reportInfo.description : null),
                allowBlank: true,
                anchor: '100%',
                height: 35
            }),
            Ext4.create('Ext.form.field.Display', {
                itemId: 'reportDescriptionDisplay',
                name: 'reportDescriptionDisplay',
                fieldLabel: 'Report Description',
                labelWidth: 125,
                hidden: this.canSaveChanges(),
                value: $h(this.isSavedReport() ? this.reportInfo.description : null),
                anchor: '100%'
            }),
            Ext4.create('Ext.form.RadioGroup', {
                itemId: 'reportShared',
                fieldLabel: 'Viewable By',
                labelWidth: 125,
                width: 350,
                items : [
                        { itemId: 'allReaders', name: 'reportShared', boxLabel: 'All readers', inputValue: 'true', disabled: !this.canSaveSharedCharts(), checked: this.currentlyShared },
                        { itemId: 'onlyMe', name: 'reportShared', boxLabel: 'Only me', inputValue: 'false', disabled: !this.canSaveSharedCharts(), checked: !this.currentlyShared }
                    ]
            }),
            Ext4.create('Ext.form.field.Checkbox', {
                itemId: 'reportSaveThumbnail',
                name: 'reportSaveThumbnail',
                fieldLabel: 'Save Thumbnail',
                labelWidth: 125,
                checked: this.saveThumbnail,
                value: this.saveThumbnail,
                hidden: (Ext4.isIE6 || Ext4.isIE7 || Ext4.isIE8)
            })
        ];

        this.buttons = [
            {
                text: "Save",
                hidden: !this.canSaveChanges(),
                disabled: !this.canSaveChanges(),
                handler: function() {
                    var formVals = this.getForm().getValues();

                    // report name is required for saving
                    if(!formVals.reportName){
                       Ext4.Msg.show({
                            title: "Error",
                            msg: "Report name must be specified when saving a chart.",
                            buttons: Ext4.MessageBox.OK,
                            icon: Ext4.MessageBox.ERROR
                       });
                       return;
                    }

                    // the save button will not allow for replace if this is a new chart,
                    // but will force replace if this is a change to a saved chart
                    var shared = typeof formVals.reportShared == "string" ? 'true' == formVals.reportShared : (new Boolean(formVals.reportShared)).valueOf();
                    this.fireEvent('saveChart', {
                        isSaveAs: this.isSaveAs,
                        replace: !this.isSaveAs ? this.isSavedReport() : false,
                        reportName: formVals.reportName,
                        reportDescription: formVals.reportDescription,
                        shared: shared,
                        saveThumbnail: this.getSaveThumbnail(),
                        canSaveSharedCharts: this.canSaveSharedCharts(),
                        createdBy: this.createdBy
                    });

                    this.fireEvent('closeOptionsWindow');
                },
                scope: this,
                formBind: true
            },
            {text: 'Cancel', handler: function(){this.fireEvent('closeOptionsWindow');}, scope: this}
        ];

        this.callParent();
    },

    isSavedReport : function()
    {
        return (typeof this.reportInfo == "object");
    },

    canSaveChanges : function()
    {
        return this.canEdit;
    },

    canSaveSharedCharts : function()
    {
        return this.canEdit && this.canShare;
    },

    setSaveAs : function(isSaveAs)
    {
        this.isSaveAs = isSaveAs;

        // reset the report name and description fields
        if (this.isSaveAs || !this.isSavedReport())
        {
            this.down('#reportName').show();      
            this.down('#reportName').setValue("");
            this.down('#reportNameDisplay').hide();
            this.down('#reportDescription').setValue("");
            this.down('#reportShared').setValue('true');
            this.down('#reportSaveThumbnail').setValue(true);
        }
        else
        {
            this.down('#reportName').hide();
            this.down('#reportNameDisplay').show();
            if (this.isSavedReport())
            {
                this.down('#reportNameDisplay').setValue($h(this.reportInfo.name));
                this.down('#reportName').setValue(this.reportInfo.name);
                this.down('#reportDescription').setValue(this.reportInfo.description);
                if (this.currentlyShared)
                    this.down('#allReaders').setValue(true);
                this.down('#reportSaveThumbnail').setValue(this.saveThumbnail);

            }
        }
    },

    getSaveThumbnail: function() {
        return this.down('#reportSaveThumbnail').checked;
    }
});
