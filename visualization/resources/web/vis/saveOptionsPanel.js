/*
 * Copyright (c) 2012-2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
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

        // default to auto generate thumbnail from report svg
        this.thumbnailType = this.isSavedReport() && this.reportInfo.reportProps && this.reportInfo.reportProps.thumbnailType ? this.reportInfo.reportProps.thumbnailType : 'AUTO';

        // Note that Readers are allowed to save new charts (readers own new charts they're creating)- this is by design.
        this.currentlyShared = (this.isSavedReport() && this.reportInfo.shared) || (!this.isSavedReport() && this.canSaveSharedCharts());
        this.createdBy = this.isSavedReport() ? this.reportInfo.createdBy : LABKEY.Security.currentUser.id;

        // generate unique id for the thumbnail preview div
        this.thumbnailPreviewId = Ext4.id();

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
                value: Ext4.util.Format.htmlEncode(this.isSavedReport() ? this.reportInfo.name : null),
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
                value: Ext4.util.Format.htmlEncode(this.isSavedReport() ? this.reportInfo.description : null),
                anchor: '100%'
            }),
            Ext4.create('Ext.form.RadioGroup', {
                itemId: 'reportShared',
                fieldLabel: 'Viewable By',
                labelWidth: 125,
                width: 350,
                items : [
                        { itemId: 'allReaders', name: 'reportShared', boxLabel: 'All readers', inputValue: 'true', disabled: !this.canSaveSharedCharts(), checked: this.currentlyShared, width: 140 },
                        { itemId: 'onlyMe', name: 'reportShared', boxLabel: 'Only me', inputValue: 'false', disabled: !this.canSaveSharedCharts(), checked: !this.currentlyShared, width: 140 }
                    ]
            }),
            Ext4.create('Ext.form.RadioGroup', {
                itemId: 'reportThumbnailType',
                fieldLabel: 'Thumbnail',
                labelWidth: 125,
                width: 480,
                hidden: (Ext4.isIE6 || Ext4.isIE7 || Ext4.isIE8),
                items: [
                    {
                        itemId: 'autoGenerate',
                        width: 140,
                        name: 'reportThumbnailType',
                        boxLabel: 'Auto-generate',
                        description: 'Auto-generate a new thumbnail based on the first chart in this report',
                        thumbnailPreview: null, // to be poplulated by calls to updateCurrentChartThumbnail
                        inputValue: 'AUTO',
                        checked: this.thumbnailType == 'AUTO'
                    },
                    {
                        itemId: 'none',
                        width: 75,
                        name: 'reportThumbnailType',
                        boxLabel: 'None',
                        description: 'Use the default static image for this report type',
                        thumbnailPreview: '<img src="' + LABKEY.contextPath + '/visualization/report/timechart.png"/>', 
                        inputValue: 'NONE',
                        checked: this.thumbnailType == 'NONE'
                    },
                    {
                        itemId: 'keepCustom',
                        width: 140,
                        name: 'reportThumbnailType',
                        boxLabel: 'Keep existing',
                        description: 'Keep the existing custom thumbnail that has been provided for this report',
                        thumbnailPreview: (this.isSavedReport() ? '<img src="' + this.reportInfo.thumbnailURL + '"/>'  : null),
                        inputValue: 'CUSTOM',
                        checked: this.thumbnailType == 'CUSTOM'
                    }
                ],
                listeners: {
                    afterrender: function(cmp) {
                        Ext4.each(Ext4.ComponentQuery.query('#reportThumbnailType radio'), function(item) {
                            Ext4.create('Ext.tip.ToolTip', {
                                target: item.getId(),
                                html: item.description
                            });
                        }, this);

                        this.setThumbnailPreview();
                    },
                    change: this.setThumbnailPreview,
                    scope: this
                }
            }),
            Ext4.create('Ext.form.field.Display', {
                itemId: 'thumbnailPreview',
                fieldLabel: ' ',
                labelSeparator: '',
                labelWidth: 125,
                value: '<div class="thumbnail" id="' + this.thumbnailPreviewId + '" style="border: solid #C0C0C0 1px;"></div>'
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
                        thumbnailType: formVals.reportThumbnailType,
                        canSaveSharedCharts: this.canSaveSharedCharts(),
                        createdBy: this.createdBy
                    });

                    // store the update report properties
                    if(this.reportInfo){
                        this.reportInfo.name = formVals.reportName;
                        this.reportInfo.description = formVals.reportDescription;
                        this.currentlyShared = shared;
                        this.thumbnailType = formVals.reportThumbnailType;
                    }

                    this.fireEvent('closeOptionsWindow', false);
                },
                scope: this,
                formBind: true
            },
            {
                text: 'Cancel',
                handler: this.cancelChangesButtonClicked,
                scope: this
            }
        ];

        this.callParent();
    },

    cancelChangesButtonClicked: function(){
        this.fireEvent('closeOptionsWindow', true);
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
            else
                this.down('#allReaders').setValue(true);

            this.down('#reportThumbnailType').setValue({reportThumbnailType: 'AUTO'});
            this.down('#keepCustom').hide();

            this.down('#reportSaveButton').show();
        }
        else
        {
            this.down('#reportName').setVisible(!this.isSavedReport());
            this.down('#reportName').setValue(this.isSavedReport() ? this.reportInfo.name : null);
            this.down('#reportNameDisplay').setVisible(this.isSavedReport());
            this.down('#reportNameDisplay').setValue(Ext4.util.Format.htmlEncode(this.isSavedReport() ? this.reportInfo.name : null));

            this.down('#reportDescription').setVisible(this.canSaveChanges());
            this.down('#reportDescription').setValue(this.isSavedReport() ? this.reportInfo.description : null);
            this.down('#reportDescriptionDisplay').setVisible(!this.canSaveChanges());
            this.down('#reportDescriptionDisplay').setValue(Ext4.util.Format.htmlEncode(this.isSavedReport() ? this.reportInfo.description : null));

            if (!this.currentlyShared)
                this.down('#onlyMe').setValue(true);
            else
                this.down('#allReaders').setValue(true);

            this.down('#reportThumbnailType').setValue({reportThumbnailType: this.thumbnailType});
            // hide the Keep existing option if there hasn't been a custom thumbnail saved for this report
            if (this.thumbnailType == 'CUSTOM')
                this.down('#keepCustom').show();
            else
                this.down('#keepCustom').hide();

            this.down('#reportSaveButton').setVisible(this.canEdit);
        }

        this.setThumbnailPreview();
    },

    setThumbnailPreview: function() {
        // if the thumbnail preview element hasn't rendered, return without doing anything
        if (!Ext4.getDom(this.thumbnailPreviewId))
            return;

        var checkedRadio = this.down('#reportThumbnailType').getChecked();
        if (checkedRadio.length > 0 && checkedRadio[0].thumbnailPreview)
            Ext4.getDom(this.thumbnailPreviewId).innerHTML = checkedRadio[0].thumbnailPreview;
        else
            Ext4.getDom(this.thumbnailPreviewId).innerHTML = "";

        this.doLayout();
    },

    restoreValues: function() {
        // remove the reference to the SVG so that the original image doesn't get confused
        Ext4.getDom(this.thumbnailPreviewId).innerHTML = "";
    },

    updateCurrentChartThumbnail: function(chartSVGStr, size)
    {
        // resize the svg by resetting the viewBox and width/height attributes
        chartSVGStr = chartSVGStr.replace(/width="\d+"/, 'width="100%"');
        chartSVGStr = chartSVGStr.replace(/height="\d+"/, 'height="100%"');
        chartSVGStr = chartSVGStr.replace(/<svg /, '<svg viewBox="0 0 ' + size.width + ' ' + size.height + '"');
        // remove the id reference so that updates to the original aren't confused
        chartSVGStr = chartSVGStr.replace(/id="[\w\-]+"/, '');

        // update the html contents of the thumbnail preview div
        this.down('#autoGenerate').thumbnailPreview = chartSVGStr;
        this.setThumbnailPreview();
    },

    setNoneThumbnail: function(url){
        this.down('#none').thumbnailPreview = '<img src="' + url +'" />';
    },

    setReportInfo: function(config){
        // This is really just used for GenericCharts since they don't have the reportInfo
        // available when they new up the save panel.
        this.reportInfo = Ext4.apply({}, config);

        this.down('#reportName').setValue(config.name);
        this.down('#reportNameDisplay').setValue(config.name);
        this.down('#reportDescription').setValue(config.description);
        this.down('#reportDescriptionDisplay').setValue(config.description);
        this.down('#reportShared').setValue(config.shared);

        if(config.reportProps && config.reportProps.thumbnailType){
            this.thumbnailType = config.reportProps.thumbnailType;
            if(this.thumbnailType === 'CUSTOM' && this.reportInfo.thumbnailURL){
                this.down('#keepCustom').thumbnailPreview = '<img src="' + this.reportInfo.thumbnailURL +'" />';
            }
            this.down('#none').setValue(config.reportProps.thumbnailType);
        } else {
            this.down('#autoGenerate').setValue('AUTO');
        }
    }
});
