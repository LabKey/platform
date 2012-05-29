/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.namespace("LABKEY.vis");

Ext4.tip.QuickTipManager.init();
$h = Ext4.util.Format.htmlEncode;

Ext4.define('LABKEY.vis.SaveOptionsPanel', {

    extend : 'LABKEY.vis.GenericOptionsPanel',

    constructor : function(config){
        Ext4.applyIf(config, {
            monitorValid: true
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
                itemId: 'reportSaveButton',
                text: "Save",
                hidden: !this.canSaveChanges(),
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

        // set the report name and description fields basd on the save type
        if (this.isSaveAs)
        {
            this.down('#reportName').show();
            this.down('#reportName').setValue("");
            this.down('#reportNameDisplay').hide();

            this.down('#reportDescription').show();
            this.down('#reportDescription').setValue("");
            this.down('#reportDescriptionDisplay').hide();

            if (!this.canSaveSharedCharts())
                this.down('#onlyMe').setValue(true);
            this.down('#reportSaveThumbnail').setValue(true);

            this.down('#reportSaveButton').show();
        }
        else
        {
            this.down('#reportName').setVisible(!this.isSavedReport());
            this.down('#reportName').setValue(this.isSavedReport() ? this.reportInfo.name : null);
            this.down('#reportNameDisplay').setVisible(this.isSavedReport());
            this.down('#reportNameDisplay').setValue($h(this.isSavedReport() ? this.reportInfo.name : null));

            this.down('#reportDescription').setVisible(this.canSaveChanges());
            this.down('#reportDescription').setValue(this.isSavedReport() ? this.reportInfo.description : null);
            this.down('#reportDescriptionDisplay').setVisible(!this.canSaveChanges());
            this.down('#reportDescriptionDisplay').setValue($h(this.isSavedReport() ? this.reportInfo.description : null));

            if (this.currentlyShared)
                this.down('#allReaders').setValue(true);
            this.down('#reportSaveThumbnail').setValue(this.saveThumbnail);

            this.down('#reportSaveButton').setVisible(this.canEdit);
        }
    },

    getSaveThumbnail: function() {
        return this.down('#reportSaveThumbnail').checked;
    },

    getPanelOptionValues: function() {
        return {
            saveThumbnail: this.getSaveThumbnail()
        };
    }
});
