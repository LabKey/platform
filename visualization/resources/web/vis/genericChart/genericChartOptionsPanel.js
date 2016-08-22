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

    initComponent : function() {

        var labelWidth = 95;

        this.labelBox = Ext4.create('Ext.form.field.Text', {
            name: 'label',
            getInputValue: this.getLabel,
            fieldLabel: 'Title',
            labelWidth: labelWidth,
            width: 275,
            padding: '0 0 10px 0',
            value: this.defaultChartLabel
        });

        this.widthBox = Ext4.create('Ext.form.field.Number', {
            name: 'width',
            getInputValue: this.getWidth,
            fieldLabel: 'Width',
            labelWidth: labelWidth,
            width: 275,
            padding: '0 0 10px 0',
            allowDecimals: false,
            hideTrigger: true
        });

        this.heightBox = Ext4.create('Ext.form.field.Number', {
            name: 'height',
            getInputValue: this.getHeight,
            fieldLabel: 'Height',
            labelWidth: labelWidth,
            width: 275,
            padding: '0 0 10px 0',
            allowDecimals: false,
            hideTrigger: true
        });

        this.jitterCheckbox = Ext4.create('Ext.form.field.Checkbox', {
            name: 'position',
            getInputValue: this.getPosition,
            fieldLabel: 'Jitter Points',
            labelWidth: labelWidth,
            padding: '0 0 10px 0',
            layoutOptions: 'box',
            value: this.position == 'jitter'
        });

        this.opacitySlider = Ext4.create('Ext.slider.Single', {
            name: 'opacity',
            getInputValue: this.getOpacity,
            labelWidth: labelWidth,
            fieldLabel: 'Point Opacity',
            width: 270,
            padding: '0 0 10px 0',
            layoutOptions: 'point',
            value: 50,
            increment: 10,
            minValue: 10,
            maxValue: 100
        });

        this.pointSizeSlider = Ext4.create('Ext.slider.Single', {
            name: 'pointSize',
            getInputValue: this.getPointSize,
            labelWidth: labelWidth,
            fieldLabel: 'Point Size',
            width: 270,
            padding: '0 0 10px 0',
            layoutOptions: 'point',
            value: 5,
            increment: 1,
            minValue: 1,
            maxValue: 10
        });

        this.colorLabel = Ext4.create('Ext.form.Label', {
            width: labelWidth,
            text: 'Point Color:',
            layoutOptions: 'point'
        });

        this.pointColorPicker = Ext4.create('Ext.picker.Color', {
            name: 'pointFillColor',
            getInputValue: this.getPointColor,
            value: '3366FF',  // initial selected color
            width: 280,
            padding: '0 0 0 100px',
            layoutOptions: 'point'
        });

        this.lineColorLabel = Ext4.create('Ext.form.Label', {
            width: labelWidth,
            text: 'Line Color:',
            layoutOptions: 'line'
        });

        this.lineColorPicker = Ext4.create('Ext.picker.Color', {
            name: 'lineColor',
            getInputValue: this.getLineColor,
            value: '000000',  // initial selected color
            width: 280,
            padding: '0 0 0 100px',
            layoutOptions: 'line'
        });

        this.fillColorLabel = Ext4.create('Ext.form.Label', {
            width: labelWidth,
            text: 'Fill Color:',
            layoutOptions: 'line'
        });

        this.fillColorPicker = Ext4.create('Ext.picker.Color', {
            name: 'boxFillColor',
            getInputValue: this.getFillColor,
            value: '3366FF',  // initial selected color
            width: 280,
            padding: '0 0 0 100px',
            layoutOptions: 'line'
        });

        this.lineWidthSlider = Ext4.create('Ext.slider.Single', {
            name: 'lineWidth',
            getInputValue: this.getLineWidth,
            labelWidth: labelWidth,
            fieldLabel: 'Line Width',
            width: 270,
            padding: '0 0 10px 0',
            layoutOptions: 'line',
            value: 1,
            increment: 1,
            minValue: 1,
            maxValue: 10
        });

        this.pointTypeCombo = Ext4.create('Ext.form.ComboBox', {
            name: 'pointType',
            getInputValue: this.getPointType,
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
            layoutOptions: 'box',
            queryMode: 'local',
            editable: false,
            forceSelection: true,
            displayField: 'label',
            valueField: 'pointType',
            value: this.pointType
        });

        this.items = [{
            columnWidth: 0.5,
            border: false,
            padding: '0 20px 0 0',
            items: this.getColumnOneFields()
        },{
            columnWidth: 0.5,
            border: false,
            items: this.getColumnTwoFields()
        }];

        this.callParent();
    },

    getColumnOneFields: function()
    {
        return [
            this.labelBox,
            this.widthBox,
            this.heightBox,
            this.opacitySlider,
            this.pointSizeSlider,
            this.colorLabel, this.pointColorPicker
        ];
    },

    getColumnTwoFields: function()
    {
        return [
            this.pointTypeCombo,
            this.jitterCheckbox,
            this.lineWidthSlider,
            this.lineColorLabel, this.lineColorPicker,
            this.fillColorLabel, this.fillColorPicker
        ];
    },

    getInputFields: function()
    {
        return this.getColumnOneFields().concat(this.getColumnTwoFields());
    },

    getPanelOptionValues: function() {
        var values = {};

        Ext4.each(this.getInputFields(), function(field)
        {
            if (field.name && field.getInputValue && !field.isDisabled())
                values[field.name] = field.getInputValue.call(this);
        }, this);

        return values;
    },

    setPanelOptionValues: function(chartConfig)
    {
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
        if (!this.pointTypeCombo.isDisabled() && this.getPointType() == 'all') {
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
