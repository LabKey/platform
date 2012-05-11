/*
 * Copyright (c) 2011-2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext.namespace("LABKEY.vis");

Ext.QuickTips.init();
$h = Ext.util.Format.htmlEncode;

LABKEY.vis.ChartEditorSavePanel = Ext.extend(Ext.FormPanel, {
    constructor : function(config){
        Ext.applyIf(config, {
            header: false,
            border: false,
            autoHeight: true,
            autoWidth: true,
            padding: 5,
            labelWidth: 125,
            monitorValid: true,
            items: [],
            buttonAlign: 'right',
            buttons: []
        });

        this.addEvents(
            'saveChart',
            'closeOptionsWindow'
        );

        LABKEY.vis.ChartEditorSavePanel.superclass.constructor.call(this, config);
    },

    initComponent : function() {
        this.isSaveAs = false;

        // Note that Readers are allowed to save new charts (readers own new charts they're creating)- this is by design.
        this.currentlyShared = (this.isSavedReport() && this.reportInfo.shared) || (!this.isSavedReport() && this.canSaveSharedCharts());
        this.createdBy = this.isSavedReport() ? this.reportInfo.createdBy : LABKEY.Security.currentUser.id;

        this.items = [
            new Ext.form.TextField({
                itemId: 'reportName',
                name: 'reportName',
                fieldLabel: 'Report Name',
                hidden: this.isSavedReport() || !this.canSaveChanges(),
                value: (this.isSavedReport() ? this.reportInfo.name : null),
                allowBlank: true,
                anchor: '100%',
                maxLength: 200
            }),
            new Ext.form.DisplayField({
                itemId: 'reportNameDisplay',
                name: 'reportNameDisplay',
                fieldLabel: 'Report Name',
                hidden: !this.isSavedReport() && this.canSaveChanges(),
                value: $h(this.isSavedReport() ? this.reportInfo.name : null),
                anchor: '100%'
            }),
            new Ext.form.TextArea({
                itemId: 'reportDescription',
                name: 'reportDescription',
                fieldLabel: 'Report Description',
                hidden: !this.canSaveChanges(),
                value: (this.isSavedReport() ? this.reportInfo.description : null),
                allowBlank: true,
                anchor: '100%',
                height: 35
            }),
            new Ext.form.DisplayField({
                itemId: 'reportDescriptionDisplay',
                name: 'reportDescriptionDisplay',
                fieldLabel: 'Report Description',
                hidden: this.canSaveChanges(),
                value: $h(this.isSavedReport() ? this.reportInfo.description : null),
                anchor: '100%'
            }),
            new Ext.form.RadioGroup({
                itemId: 'reportShared',
                name: 'reportShared',
                fieldLabel: 'Viewable By',
                width: 250,
                items : [
                        { name: 'reportShared', boxLabel: 'All readers', inputValue: 'true', disabled: !this.canSaveSharedCharts(), checked: this.currentlyShared },
                        { name: 'reportShared', boxLabel: 'Only me', inputValue: 'false', disabled: !this.canSaveSharedCharts(), checked: !this.currentlyShared }
                    ]
            }),
            new Ext.form.Checkbox({
                itemId: 'reportSaveThumbnail',
                name: 'reportSaveThumbnail',
                fieldLabel: 'Save Thumbnail',
                anchor: '100%',
                checked: this.saveThumbnail,
                value: this.saveThumbnail,
                hidden: (Ext.isIE6 || Ext.isIE7 || Ext.isIE8)
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
                       Ext.Msg.show({
                            title: "Error",
                            msg: "Report name must be specified when saving a chart.",
                            buttons: Ext.MessageBox.OK,
                            icon: Ext.MessageBox.ERROR
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

        LABKEY.vis.ChartEditorSavePanel.superclass.initComponent.call(this);
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
            this.find('itemId', 'reportName')[0].show();        // TODO: convert to this.down('#foo'); with Ext4
            this.find('itemId', 'reportName')[0].setValue("");
            this.find('itemId', 'reportNameDisplay')[0].hide();
            this.find('itemId', 'reportDescription')[0].setValue("");
            this.find('itemId', 'reportShared')[0].setValue('true');
            this.find('itemId', 'reportSaveThumbnail')[0].setValue(true);            
        }
        else
        {
            this.find('itemId', 'reportName')[0].hide();
            this.find('itemId', 'reportNameDisplay')[0].show();
            if (this.isSavedReport())
            {
                this.find('itemId', 'reportNameDisplay')[0].setValue($h(this.reportInfo.name));
                this.find('itemId', 'reportName')[0].setValue(this.reportInfo.name);
                this.find('itemId', 'reportDescription')[0].setValue(this.reportInfo.description);
                this.find('itemId', 'reportShared')[0].setValue(this.currentlyShared);
                this.find('itemId', 'reportSaveThumbnail')[0].setValue(this.saveThumbnail);

            }
        }
    },

    getSaveThumbnail: function() {
        return this.find('itemId', 'reportSaveThumbnail')[0].checked;
    }
});
