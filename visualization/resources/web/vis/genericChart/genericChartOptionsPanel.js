/*
 * Copyright (c) 2012-2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('LABKEY.vis.GenericChartOptionsPanel', {

    extend : 'LABKEY.vis.GenericOptionsPanel',

    layout: 'column',
    pointType: 'outliers',
    defaultChartLabel: null,

    constructor : function(config)
    {
        this.callParent([config]);
        this.addEvents('chartDefinitionChanged');
    },

    initComponent : function() {

        var labelWidth = 95;

        this.jitterCheckbox = Ext4.create('Ext.form.field.Checkbox', {
            name: 'jitter',
            fieldLabel: 'Jitter Points',
            labelWidth: labelWidth,
            padding: '0 0 10px 0',
            value: this.position == 'jitter',
            listeners: {
                change: function(cb, newVal, oldVal){
                    if(!this.suppressEvents){
                        this.hasChanges = true;
                    }
                },
                scope: this
            }
        });

        this.opacitySlider = Ext4.create('Ext.slider.Single', {
            labelWidth: labelWidth,
            fieldLabel: 'Point Opacity',
            width: 270,
            padding: '0 0 10px 0',
            value: 50,
            increment: 10,
            minValue: 10,
            maxValue: 100,
            listeners: {
                change: function(combo){
                    if(!this.suppressEvents){
                        this.hasChanges = true;
                    }
                },
                scope: this
            }
        });

        this.pointSizeSlider = Ext4.create('Ext.slider.Single', {
            labelWidth: labelWidth,
            fieldLabel: 'Point Size',
            width: 270,
            padding: '0 0 10px 0',
            value: 5,
            increment: 1,
            minValue: 1,
            maxValue: 10,
            listeners: {
                change: function(combo){
                    if(!this.suppressEvents){
                        this.hasChanges = true;
                    }
                },
                scope: this
            }
        });

        this.colorLabel = Ext4.create('Ext.form.Label', {
            width: labelWidth,
            text: 'Point Color:'
        });

        this.pointColorPicker = Ext4.create('Ext.picker.Color', {
            value: '3366FF',  // initial selected color
            width: 280,
            padding: '0 0 0 100px',
            listeners: {
                select: function(picker, selColor) {
                    if(!this.suppressEvents){
                        this.hasChanges = true;
                    }
                },
                scope: this
            }
        });

        this.lineColorLabel = Ext4.create('Ext.form.Label', {
            width: labelWidth,
            text: 'Line Color:'
        });

        this.lineColorPicker = Ext4.create('Ext.picker.Color', {
            value: '000000',  // initial selected color
            width: 280,
            padding: '0 0 0 100px',
            listeners: {
                select: function(picker, selColor) {
                    if(!this.suppressEvents){
                        this.hasChanges = true;
                    }
                },
                scope: this
            }
        });

        this.fillColorLabel = Ext4.create('Ext.form.Label', {
            width: labelWidth,
            text: 'Fill Color:'
        });

        this.fillColorPicker = Ext4.create('Ext.picker.Color', {
            value: '3366FF',  // initial selected color
            width: 280,
            padding: '0 0 0 100px',
            listeners: {
                select: function(picker, selColor) {
                    if(!this.suppressEvents){
                        this.hasChanges = true;
                    }
                },
                scope: this
            }
        });

        this.lineWidthSlider = Ext4.create('Ext.slider.Single', {
            labelWidth: labelWidth,
            fieldLabel: 'Line Width',
            width: 270,
            padding: '0 0 10px 0',
            value: 1,
            increment: 1,
            minValue: 1,
            maxValue: 10,
            listeners: {
                change: function(combo){
                    if(!this.suppressEvents){
                        this.hasChanges = true;
                    }
                },
                scope: this
            }
        });

        this.widthBox = Ext4.create('Ext.form.field.Number', {
            fieldLabel: 'Width',
            labelWidth: labelWidth,
            width: 275,
            padding: '0 0 10px 0',
            allowDecimals: false,
            hideTrigger: true,
            listeners: {
                scope: this,
                change: function(){
                    if(!this.suppressEvents){
                        this.hasChanges = true;
                    }
                }
            }
        });

        this.heightBox = Ext4.create('Ext.form.field.Number', {
            fieldLabel: 'Height',
            labelWidth: labelWidth,
            width: 275,
            padding: '0 0 10px 0',
            allowDecimals: false,
            hideTrigger: true,
            listeners: {
                scope: this,
                change: function(){
                    if(!this.suppressEvents){
                        this.hasChanges = true;
                    }
                }
            }
        });

        this.labelBox = Ext4.create('Ext.form.field.Text', {
            fieldLabel: 'Title',
            labelWidth: labelWidth,
            width: 275,
            padding: '0 0 10px 0',
            value: this.defaultChartLabel
        });

        this.pointTypeCombo = Ext4.create('Ext.form.ComboBox', {
            fieldLabel: 'Show Points',
            store: Ext4.create('Ext.data.Store', {
                fields: ['pointType', 'label'],
                data: [
                    {pointType: 'outliers', label: 'Outliers Only'},
                    {pointType: 'all', label: 'All'},
                    {pointType: 'none', label: 'None'}
                ]
            }),
            labelWidth: labelWidth,
            width: 275,
            padding: '0 0 10px 0',
            queryMode: 'local',
            editable: false,
            forceSelection: true,
            displayField: 'label',
            valueField: 'pointType',
            value: this.pointType,
            listeners: {
                change: function(combo){
                    if(!this.suppressEvents){
                        this.hasChanges = true;
                    }
                },
                scope: this
            }
        });

        this.items = [{
            columnWidth: 0.5,
            border: false,
            padding: '0 20px 0 0',
            items: [
                this.labelBox,
                this.widthBox,
                this.heightBox,
                this.opacitySlider,
                this.pointSizeSlider,
                this.colorLabel, this.pointColorPicker
            ]
        },{
            columnWidth: 0.5,
            border: false,
            items: [
                this.pointTypeCombo,
                this.jitterCheckbox,
                this.lineWidthSlider,
                this.lineColorLabel, this.lineColorPicker,
                this.fillColorLabel, this.fillColorPicker
            ]
        }];

        this.callParent();
    },

    applyChangesButtonClicked: function() {
        this.fireEvent('closeOptionsWindow', false);
        this.checkForChangesAndFireEvents();
    },

    cancelChangesButtonClicked: function(){
        this.fireEvent('closeOptionsWindow', true);
    },

    checkForChangesAndFireEvents: function(){
        if(this.hasChanges){
            this.fireEvent('chartDefinitionChanged')
        }
        this.hasChanges = false;
    },

    getPanelOptionValues: function() {
        return {
            pointType: this.getPointType(),
            position: this.getPosition(),
            opacity: this.getOpacity(),
            pointSize: this.getPointSize(),
            pointFillColor: this.getPointColor(),
            lineWidth: this.getLineWidth(),
            lineColor: this.getLineColor(),
            boxFillColor: this.getFillColor(),
            width: this.getWidth(),
            height: this.getHeight(),
            label: this.getLabel()
        };
    },

    restoreValues: function(initValues) {
        if (initValues.hasOwnProperty("lineWidth"))
            this.setLineWidth(initValues.lineWidth);
        if (initValues.hasOwnProperty("opacity"))
            this.setOpacity(initValues.opacity);
        if (initValues.hasOwnProperty("pointSize"))
            this.setPointSize(initValues.pointSize);
        if (initValues.hasOwnProperty("pointFillColor"))
            this.setPointColor(initValues.pointFillColor);
        if (initValues.hasOwnProperty("lineColor"))
            this.setLineColor(initValues.lineColor);
        if (initValues.hasOwnProperty("boxFillColor"))
            this.setFillColor(initValues.boxFillColor);
        if (initValues.hasOwnProperty("width"))
            this.setWidth(initValues.width);
        if (initValues.hasOwnProperty("height"))
            this.setHeight(initValues.height);
        if (initValues.hasOwnProperty("position"))
            this.setPosition(initValues.position);
        if (initValues.hasOwnProperty("pointType"))
            this.setPointType(initValues.pointType);

        this.hasChanges = false;
    },

    setPanelOptionValues: function(chartConfig){
        this.suppressEvents = true;

        if (Ext4.isDefined(chartConfig.label))
            this.setLabel(chartConfig.label);

        if (Ext4.isDefined(chartConfig.width))
            this.setWidth(chartConfig.width);

        if (Ext4.isDefined(chartConfig.height))
            this.setHeight(chartConfig.height);

        if (Ext4.isDefined(chartConfig.pointType))
            this.setPointType(chartConfig.pointType);

        if (Ext4.isDefined(chartConfig.position))
            this.setPosition(chartConfig.position);

        if (Ext4.isDefined(chartConfig.opacity))
            this.setOpacity(chartConfig.opacity);

        if (Ext4.isDefined(chartConfig.pointSize))
            this.setPointSize(chartConfig.pointSize);

        if (Ext4.isDefined(chartConfig.pointFillColor))
            this.setPointColor(chartConfig.pointFillColor);

        if (Ext4.isDefined(chartConfig.lineWidth))
            this.setLineWidth(chartConfig.lineWidth);

        if (Ext4.isDefined(chartConfig.lineColor))
            this.setLineColor(chartConfig.lineColor);

        if (Ext4.isDefined(chartConfig.boxFillColor))
            this.setFillColor(chartConfig.boxFillColor);

        if (Ext4.isDefined(chartConfig.geomOptions))
        {
            if (chartConfig.geomOptions.position)
                this.setPosition(chartConfig.geomOptions.position);

            if (chartConfig.geomOptions.opacity)
                this.setOpacity(chartConfig.geomOptions.opacity);

            if (chartConfig.geomOptions.pointSize)
                this.setPointSize(chartConfig.geomOptions.pointSize);

            if (chartConfig.geomOptions.pointFillColor)
                this.setPointColor(chartConfig.geomOptions.pointFillColor);

            if (chartConfig.geomOptions.lineWidth)
                this.setLineWidth(chartConfig.geomOptions.lineWidth);

            if (chartConfig.geomOptions.lineColor)
                this.setLineColor(chartConfig.geomOptions.lineColor);

            if (chartConfig.geomOptions.boxFillColor)
                this.setFillColor(chartConfig.geomOptions.boxFillColor);
        }

        this.suppressEvents = false;
    },

    getPointType: function(){
        return this.pointTypeCombo.getValue();
    },

    setPointType: function(value){
        this.pointTypeCombo.setValue(value);
    },

    getPosition: function() {
        return this.jitterCheckbox.getValue() ? 'jitter' : null;
    },

    setPosition: function(value) {
        this.jitterCheckbox.setValue(value == 'jitter');
    },

    getOpacity: function(){
        return this.opacitySlider.getValue() / 100;
    },

    setOpacity: function(value){
        this.opacitySlider.setValue(value * 100);
    },

    getPointSize: function(){
        return this.pointSizeSlider.getValue()
    },

    setPointSize: function(value){
        this.pointSizeSlider.setValue(value);
    },

    getPointColor: function(){
        return this.pointColorPicker.getValue();
    },

    setPointColor: function(value){
        if (value != null && value != 'none')
            this.pointColorPicker.select(value);
    },

    getLineWidth: function(){
        return this.lineWidthSlider.getValue();
    },

    setLineWidth: function(value){
        this.lineWidthSlider.setValue(value);
    },

    getLineColor: function(){
        return this.lineColorPicker.getValue();
    },

    setLineColor: function(value){
        if (value != null && value != 'none')
            this.lineColorPicker.select(value);
    },

    getFillColor: function(){
        if (this.getPointType() == 'all') {
            return 'none';
        }

        return this.fillColorPicker.getValue();
    },

    setFillColor: function(value){
        if (value != null && value != 'none')
            this.fillColorPicker.select(value);
    },

    getWidth: function(){
        return this.widthBox.getValue();
    },

    setWidth: function(value){
        this.widthBox.setValue(value);
    },

    getHeight: function(){
        return this.heightBox.getValue();
    },

    setHeight: function(value){
        this.heightBox.setValue(value);
    },

    getLabel: function(){
        return this.labelBox.getValue();
    },

    setLabel: function(value){
        this.labelBox.setValue(value);
    }
});
