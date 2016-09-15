/*
 * Copyright (c) 2012-2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('LABKEY.vis.GenericChartOptionsPanel', {

    extend : 'LABKEY.vis.GenericOptionsPanel',

    layout: 'column',
    pointType: 'outliers',
    defaultColors: 'ColorDiscrete',
    defaultChartLabel: null,
    defaultChartSubTitle: null,
    defaultChartFooter: null,


    initComponent : function() {

        var labelWidth = 95;
        var defaultColorsArray = this.colorStrConverter(LABKEY.vis.Scale[this.defaultColors]());

        this.labelBox = Ext4.create('Ext.form.field.Text', {
            name: 'label',
            getInputValue: this.getLabel,
            fieldLabel: 'Title',
            labelWidth: labelWidth,
            width: 275,
            padding: '0 0 10px 0',
            value: this.defaultChartLabel
        });

        this.subtitleBox = Ext4.create('Ext.form.field.Text', {
            name: 'subtitle',
            getInputValue: this.getSubtitle,
            fieldLabel: 'Subtitle',
            labelWidth: labelWidth,
            width: 275,
            padding: '0 0 10px 0',
            layoutOptions: 'pie',
            value: this.defaultChartSubTitle
        });

        this.footerBox = Ext4.create('Ext.form.field.Text', {
            name: 'footer',
            getInputValue: this.getFooter,
            fieldLabel: 'Footer',
            labelWidth: labelWidth,
            width: 275,
            padding: '0 0 10px 0',
            layoutOptions: 'pie',
            value: this.defaultChartFooter
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

        this.percentagesCheckBox = Ext4.create('Ext.form.field.Checkbox', {
            name: 'showPercentages',
            getInputValue: this.getShowPercentages,
            fieldLabel: 'Show Percentages',
            labelWidth: labelWidth,
            padding: '0 0 10px 0',
            layoutOptions: 'pie',
            checked: true
        });

        this.percentageHideSlider = Ext4.create('Ext.slider.Single', {
            name: 'hideWhenLessThanPercentage',
            getInputValue: this.getHidePercentages,
            labelWidth: labelWidth,
            fieldLabel: 'Hide % when less than',
            width: 270,
            padding: '0 0 10px 0',
            layoutOptions: 'pie',
            value: 5,
            increment: 1,
            minValue: 0,
            maxValue: 100
        });

        this.gradientSlider = Ext4.create('Ext.slider.Single', {
            name: 'gradient',
            getInputValue: this.getGradient,
            labelWidth: labelWidth,
            fieldLabel: 'Gradient %',
            width: 270,
            padding: '0 0 10px 0',
            layoutOptions: 'pie',
            value: 100,
            increment: 5,
            minValue: 5,
            maxValue: 100
        });

        this.gradientColorLabel = Ext4.create('Ext.form.Label', {
            width: labelWidth,
            text: 'Gradient Color:',
            layoutOptions: 'pie'
        });

        this.gradientColorPicker = Ext4.create('Ext.picker.Color', {
            name: 'gradientColor',
            getInputValue: this.getGradientColor,
            labelWidth: labelWidth,
            value: 'FFFFFF',  // initial selected color
            width: 280,
            padding: '0 0 0 100px',
            layoutOptions: 'pie'
        });

        this.colorPaletteFieldContainer = Ext4.create('Ext.Component',  {
            layoutOptions: 'pie',
           html:  "<div id=\"colorPalette\"></div>",
            listeners: {
                scope: this,
                render: function() {
                    this.renderColorPaletteDisplay(defaultColorsArray);
                }
            }
        });

        this.colorPaletteComboBox = Ext4.create('Ext.form.ComboBox', {
            name: 'colorPaletteCombo',
            fieldLabel: 'Color palette',
            getInputValue: this.getColorPalette,
            layoutOptions: 'pie',
            editable: false,
            value: this.defaultColors,
            store: [
                ['ColorDiscrete', 'Light (default)'],
                ['DarkColorDiscrete', 'Dark'],
                ['DataspaceColor', 'Alternate']
            ],
            listeners: {
                scope: this,
                change: function(combo, val) {
                    var colors = this.colorStrConverter(LABKEY.vis.Scale[val]());
                    this.renderColorPaletteDisplay(colors);
                }
            }
        });

        this.innerRadiusSlider = Ext4.create('Ext.slider.Single', {
            name: 'innerRadius',
            getInputValue: this.getInnerRadiusPercent,
            labelWidth: labelWidth,
            fieldLabel: 'Inner Radius %',
            width: 270,
            padding: '0 0 10px 0',
            layoutOptions: 'pie',
            value: 0,
            increment: 10,
            minValue: 0,
            maxValue: 100
        });

        this.outerRadiusSlider = Ext4.create('Ext.slider.Single', {
            name: 'outerRadius',
            getInputValue: this.getOuterRadiusPercent,
            labelWidth: labelWidth,
            fieldLabel: 'Outer Radius %',
            width: 270,
            padding: '0 0 10px 0',
            layoutOptions: 'pie',
            value: 80,
            increment: 10,
            minValue: 0,
            maxValue: 100
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
            fieldLabel: 'Opacity',
            width: 270,
            padding: '0 0 10px 0',
            layoutOptions: 'opacity',
            value: 80,
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
            columnWidth: 0.45,
            border: false,
            padding: '0 20px 0 0',
            items: this.getColumnOneFields()
        },{
            columnWidth: 0.45, //0.5 + 0.5 + padding causes weird bug where columns are too wide to fit side-by-side
            border: false,
            items: this.getColumnTwoFields()
        }];

        this.callParent();
    },

    colorStrConverter: function(colorSet) {
        for(var i=0;i<colorSet.length;i++)
            colorSet[i] = colorSet[i].substring(1);
        return colorSet;
    },

    renderColorPaletteDisplay: function(colors) {
        console.log('re-render color palette');
        Ext4.get('colorPalette').update('');
        if (colors)
        {
            Ext4.create('Ext.picker.Color', {
                renderTo: 'colorPalette',
                disabled: true,
                width: 385,
                height: 20,
                colors: colors
            });
        }
    },

    getColumnOneFields: function()
    {
        return [
            this.labelBox,
            this.subtitleBox,
            this.footerBox,
            this.widthBox,
            this.heightBox,
            this.percentagesCheckBox,
            this.percentageHideSlider,
            this.innerRadiusSlider,
            this.outerRadiusSlider,
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
            this.gradientSlider,
            this.gradientColorLabel,
            this.gradientColorPicker,
            this.colorPaletteComboBox,
            this.colorPaletteFieldContainer,
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

    onMeasureChange : function(xMeasure, yMeasure)
    {
        if (xMeasure && xMeasure.hasOwnProperty('label')) {
            this.subtitleBox.setValue(xMeasure.label);
        }
        if (yMeasure && yMeasure.hasOwnProperty('label')) {
            this.footerBox.setValue(yMeasure.label);
        }
    },

    setPanelOptionValues: function(chartConfig)
    {
        if (Ext4.isDefined(chartConfig.label))
            this.setLabel(chartConfig.label);

        if (Ext4.isDefined(chartConfig.subtitle))
            this.setSubtitle(chartConfig.subtitle);

        if (Ext4.isDefined(chartConfig.footer))
            this.setFooter(chartConfig.footer);

        if (Ext4.isDefined(chartConfig.width))
            this.setWidth(chartConfig.width);

        if (Ext4.isDefined(chartConfig.height))
            this.setHeight(chartConfig.height);

        if (Ext4.isDefined(chartConfig.gradient))
            this.setGradient(chartConfig.gradient);

        if (Ext4.isDefined(chartConfig.gradientColor))
            this.setGradientColor(chartConfig.gradientColor);

        if (Ext4.isDefined(chartConfig.innerRadiusPercent))
            this.setInnerRadiusPercent(chartConfig.innerRadiusPercent);

        if (Ext4.isDefined(chartConfig.outerRadiusPercent))
            this.setOuterRadiusPercent(chartconfig.outerRadiusPercent);

        if (Ext4.isDefined(chartConfig.showPercentages))
            this.setShowPercentages(chartConfig.showPercentages);

        if (Ext4.isDefined(chartConfig.hideWhenLessThanPercentage))
            this.setHidePercentages(chartConfig.hideWhenLessThanPercentage);

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

        if (Ext4.isDefined(chartConfig.colorPaletteCombo))
            this.setColorPalette(chartConfig.colorPaletteCombo);

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
    },

    getSubtitle: function() {
        return this.subtitleBox.getValue();
    },

    setSubtitle: function(value) {
       this.subtitleBox.setValue(value);
    },

    getFooter: function () {
        return this.footerBox.getValue();
    },

    setFooter: function(value) {
        this.footerBox.setValue(value);
    },

    getGradient: function() {
        return this.gradientSlider.getValue();
    },

    setGradient: function(value) {
        this.gradientSlider.setValue(value);
    },

    getGradientColor: function(){
        return this.gradientColorPicker.getValue();
    },

    setGradientColor: function(value){
        if (value != null && value != 'none')
            this.gradientColorPicker.select(value);
    },

    getInnerRadiusPercent: function() {
        return this.innerRadiusSlider.getValue();
    },

    setInnerRadiusPercent: function(value) {
        this.innerRadiusSlider.setValue(value);
    },

    getOuterRadiusPercent: function() {
        return this.outerRadiusSlider.getValue();
    },

    setOuterRadiusPercent: function(value) {
        this.outerRadiusSlider.setValue(value);
    },

    getShowPercentages: function() {
        return this.percentagesCheckBox.getValue();
    },

    setShowPercentages: function(value) {
        this.percentagesCheckBox.setValue(value);
    },

    getHidePercentages: function() {
        return this.percentageHideSlider.getValue();
    },

    setHidePercentages: function(value) {
        this.percentageHideSlider.setValue(value);
    },

    getColorPalette: function() {
        return this.colorPaletteComboBox.getValue();
    },

    setColorPalette: function(value) {
        this.colorPaletteComboBox.setValue(value);
    }
});
