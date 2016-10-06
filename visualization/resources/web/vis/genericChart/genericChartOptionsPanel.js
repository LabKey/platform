/*
 * Copyright (c) 2012-2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('LABKEY.vis.GenericChartOptionsPanel', {

    extend : 'LABKEY.vis.GenericOptionsPanel',

    layout: 'column',

    pointType: 'outliers',
    defaultOpacity: null,
    defaultChartLabel: null,
    defaultColorPaletteScale: 'ColorDiscrete',
    userEditedSubtitle: false,
    userEditedFooter: false,

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

        this.subtitleBox = Ext4.create('Ext.form.field.Text', {
            name: 'subtitle',
            getInputValue: this.getSubtitle,
            fieldLabel: 'Subtitle',
            labelWidth: labelWidth,
            width: 275,
            padding: '0 0 10px 0',
            enableKeyEvents: true,
            layoutOptions: 'pie'
        });
        this.subtitleBox.addListener('keyup', function(){
            this.userEditedSubtitle = this.subtitleBox.getValue() != '';
        }, this, {buffer: 500});

        this.footerBox = Ext4.create('Ext.form.field.Text', {
            name: 'footer',
            getInputValue: this.getFooter,
            fieldLabel: 'Footer',
            labelWidth: labelWidth,
            width: 275,
            padding: '0 0 10px 0',
            enableKeyEvents: true,
            layoutOptions: 'pie'
        });
        this.footerBox.addListener('keyup', function(){
            this.userEditedFooter = this.footerBox.getValue() != '';
        }, this, {buffer: 500});

        this.widthBox = Ext4.create('Ext.form.field.Number', {
            name: 'width',
            getInputValue: this.getWidth,
            fieldLabel: 'Width',
            labelWidth: labelWidth,
            width: 275,
            padding: '0 0 10px 0',
            allowDecimals: false,
            minValue: 250,
            step: 50
        });

        this.heightBox = Ext4.create('Ext.form.field.Number', {
            name: 'height',
            getInputValue: this.getHeight,
            fieldLabel: 'Height',
            labelWidth: labelWidth,
            width: 275,
            padding: '0 0 10px 0',
            allowDecimals: false,
            minValue: 250,
            step: 50
        });

        this.showPiePercentagesCheckBox = Ext4.create('Ext.form.field.Checkbox', {
            name: 'showPiePercentages',
            getInputValue: this.getShowPiePercentages,
            fieldLabel: 'Show Percentages',
            labelWidth: 155,
            padding: '0 0 10px 0',
            layoutOptions: 'pie',
            checked: true
        });

        this.piePercentageHideNumber = Ext4.create('Ext.form.field.Number', {
            name: 'pieHideWhenLessThanPercentage',
            getInputValue: this.getHidePiePercentageNumber,
            labelWidth: 155,
            fieldLabel: 'Hide % when less than',
            width: 210,
            padding: '0 0 10px 0',
            layoutOptions: 'pie',
            allowDecimals: false,
            value: 5,
            minValue: 0,
            maxValue: 100
        });

        this.piePercentagesColorLabel = Ext4.create('Ext.form.Label', {
            width: labelWidth,
            cls: 'option-label',
            style: 'position: absolute;',
            text: 'Percentages Color:',
            layoutOptions: 'pie'
        });

        this.piePercentagesColorPicker = Ext4.create('Ext.picker.Color', {
            name: 'piePercentagesColor',
            getInputValue: this.getPiePercentagesColor,
            value: '333333',  // initial selected color
            width: 280,
            padding: '0 0 0 100px',
            layoutOptions: 'pie'
        });

        this.gradientSlider = Ext4.create('Ext.slider.Single', {
            name: 'gradientPercentage',
            getInputValue: this.getGradientPercentage,
            labelWidth: labelWidth,
            fieldLabel: 'Gradient %',
            width: 270,
            padding: '0 0 10px 0',
            layoutOptions: 'pie',
            value: 95,
            increment: 5,
            minValue: 0,
            maxValue: 100
        });

        this.gradientColorLabel = Ext4.create('Ext.form.Label', {
            width: labelWidth,
            cls: 'option-label',
            style: 'position: absolute;',
            text: 'Gradient Color:',
            layoutOptions: 'pie'
        });

        this.gradientColorPicker = Ext4.create('Ext.picker.Color', {
            name: 'gradientColor',
            getInputValue: this.getGradientColor,
            value: 'FFFFFF',  // initial selected color
            width: 280,
            padding: '0 0 0 100px',
            layoutOptions: 'pie'
        });

        this.colorPaletteFieldContainer = Ext4.create('Ext.Component',  {
            layoutOptions: 'pie',
            padding: '0 0 0 100px',
            html: '<div id="colorPalette" style="width: 175px; overflow-x: hidden;"></div>',
            listeners: {
                scope: this,
                render: this.renderColorPaletteDisplay
            }
        });

        this.colorPaletteComboBox = Ext4.create('Ext.form.ComboBox', {
            name: 'colorPaletteScale',
            fieldLabel: 'Color palette',
            getInputValue: this.getColorPalette,
            labelWidth: labelWidth,
            width: 275,
            layoutOptions: 'pie',
            editable: false,
            value: this.defaultColorPaletteScale,
            store: [
                ['ColorDiscrete', 'Light (default)'],
                ['DarkColorDiscrete', 'Dark'],
                ['DataspaceColor', 'Alternate']
            ],
            listeners: {
                scope: this,
                change: function(combo, val) {
                    this.defaultColorPaletteScale = val;
                    this.renderColorPaletteDisplay();
                }
            }
        });

        this.pieInnerRadiusSlider = Ext4.create('Ext.slider.Single', {
            name: 'pieInnerRadius',
            getInputValue: this.getPieInnerRadiusPercent,
            labelWidth: 105,
            fieldLabel: 'Inner Radius %',
            width: 270,
            padding: '0 0 10px 0',
            layoutOptions: 'pie',
            value: 0,
            increment: 5,
            minValue: 0,
            maxValue: 100
        });

        this.pieOuterRadiusSlider = Ext4.create('Ext.slider.Single', {
            name: 'pieOuterRadius',
            getInputValue: this.getPieOuterRadiusPercent,
            labelWidth: 105,
            fieldLabel: 'Outer Radius %',
            width: 270,
            padding: '0 0 10px 0',
            layoutOptions: 'pie',
            value: 80,
            increment: 5,
            minValue: 0,
            maxValue: 100
        });

        this.jitterCheckbox = Ext4.create('Ext.form.field.Checkbox', {
            name: 'position',
            getInputValue: this.getPosition,
            fieldLabel: 'Jitter Points',
            labelWidth: labelWidth,
            padding: '0 0 10px 0',
            layoutOptions: 'point',
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
            value: this.defaultOpacity || 50,
            increment: 5,
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
            cls: 'option-label',
            style: 'position: absolute;',
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
            cls: 'option-label',
            style: 'position: absolute;',
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
            cls: 'option-label',
            style: 'position: absolute;',
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

    colorToStrConverter: function(colorSet)
    {
        var colorStrArr = [];

        Ext4.each(colorSet, function(color){
            colorStrArr.push(color.indexOf('#') == 0 ? color.substring(1) : color);
        });

        return colorStrArr;
    },

    renderColorPaletteDisplay: function()
    {
        var colorPaletteEl = Ext4.get('colorPalette');
        if (colorPaletteEl)
        {
            colorPaletteEl.update('');

            if (Ext4.isString(this.defaultColorPaletteScale))
            {
                Ext4.create('Ext.picker.Color', {
                    renderTo: 'colorPalette',
                    disabled: true,
                    width: 385,
                    height: 20,
                    colors: this.colorToStrConverter(LABKEY.vis.Scale[this.defaultColorPaletteScale]())
                });
            }
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
            this.pointTypeCombo,
            this.jitterCheckbox,
            this.opacitySlider,
            this.pointSizeSlider,
            this.colorLabel, this.pointColorPicker,
            this.colorPaletteComboBox,
            this.colorPaletteFieldContainer,
            this.pieInnerRadiusSlider,
            this.pieOuterRadiusSlider
        ];
    },

    getColumnTwoFields: function()
    {
        return [
            this.lineWidthSlider,
            this.lineColorLabel, this.lineColorPicker,
            this.fillColorLabel, this.fillColorPicker,
            this.showPiePercentagesCheckBox,
            this.piePercentageHideNumber,
            this.piePercentagesColorLabel, this.piePercentagesColorPicker,
            this.gradientSlider,
            this.gradientColorLabel,
            this.gradientColorPicker
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

    onMeasureChange : function(measures, renderType)
    {
        if (!this.userEditedSubtitle)
        {
            if (renderType == 'pie_chart' && Ext4.isDefined(measures.x) && measures.x.hasOwnProperty('label'))
                this.setSubtitle(LABKEY.vis.GenericChartHelper.getDefaultLabel(renderType, 'x', measures.x));
            else
                this.setSubtitle('');
        }

        if (!this.userEditedFooter)
        {
            if (renderType == 'pie_chart' && Ext4.isDefined(measures.y) && measures.y.hasOwnProperty('label'))
                this.setFooter(LABKEY.vis.GenericChartHelper.getDefaultLabel(renderType, 'y', measures.y));
            else
                this.setFooter('');
        }
    },

    validateChanges : function()
    {
        return (this.widthBox.isDisabled() || this.getWidth() == null || this.getWidth() > 0)
                && (this.heightBox.isDisabled() || this.getHeight() == null || this.getHeight() > 0);
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

        if (Ext4.isDefined(chartConfig.gradientPercentage))
            this.setGradientPercentage(chartConfig.gradientPercentage);

        if (Ext4.isDefined(chartConfig.gradientColor))
            this.setGradientColor(chartConfig.gradientColor);

        if (Ext4.isDefined(chartConfig.pieInnerRadius))
            this.setPieInnerRadiusPercent(chartConfig.pieInnerRadius);

        if (Ext4.isDefined(chartConfig.pieOuterRadius))
            this.setPieOuterRadiusPercent(chartConfig.pieOuterRadius);

        if (Ext4.isDefined(chartConfig.showPiePercentages))
            this.setShowPiePercentages(chartConfig.showPiePercentages);

        if (Ext4.isDefined(chartConfig.pieHideWhenLessThanPercentage))
            this.setHidePiePercentageNumber(chartConfig.pieHideWhenLessThanPercentage);

        if (Ext4.isDefined(chartConfig.piePercentagesColor))
            this.setPiePercentagesColor(chartConfig.piePercentagesColor);

        if (Ext4.isDefined(chartConfig.colorPaletteScale))
            this.setColorPalette(chartConfig.colorPaletteScale);

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
            if (chartConfig.geomOptions.gradientPercentage)
                this.setGradientPercentage(chartConfig.geomOptions.gradientPercentage);

            if (chartConfig.geomOptions.gradientColor)
                this.setGradientColor(chartConfig.geomOptions.gradientColor);

            if (chartConfig.geomOptions.pieInnerRadius)
                this.setPieInnerRadiusPercent(chartConfig.geomOptions.pieInnerRadius);

            if (chartConfig.geomOptions.pieOuterRadius)
                this.setPieOuterRadiusPercent(chartConfig.geomOptions.pieOuterRadius);

            if (chartConfig.geomOptions.showPiePercentages)
                this.setShowPiePercentages(chartConfig.geomOptions.showPiePercentages);

            if (chartConfig.geomOptions.pieHideWhenLessThanPercentage)
                this.setHidePiePercentageNumber(chartConfig.geomOptions.pieHideWhenLessThanPercentage);

            if (chartConfig.geomOptions.piePercentagesColor)
                this.setPiePercentagesColor(chartConfig.geomOptions.piePercentagesColor);

            if (chartConfig.geomOptions.colorPaletteScale)
                this.setColorPalette(chartConfig.geomOptions.colorPaletteScale);

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

    getGradientPercentage: function() {
        return this.gradientSlider.getValue();
    },

    setGradientPercentage: function(value) {
        this.gradientSlider.setValue(value);
    },

    getGradientColor: function(){
        return this.gradientColorPicker.getValue();
    },

    setGradientColor: function(value){
        if (value != null && value != 'none')
            this.gradientColorPicker.select(value);
    },

    getPieInnerRadiusPercent: function() {
        return this.pieInnerRadiusSlider.getValue();
    },

    setPieInnerRadiusPercent: function(value) {
        this.pieInnerRadiusSlider.setValue(value);
    },

    getPieOuterRadiusPercent: function() {
        return this.pieOuterRadiusSlider.getValue();
    },

    setPieOuterRadiusPercent: function(value) {
        this.pieOuterRadiusSlider.setValue(value);
    },

    getShowPiePercentages: function() {
        return this.showPiePercentagesCheckBox.getValue();
    },

    setShowPiePercentages: function(value) {
        this.showPiePercentagesCheckBox.setValue(value);
    },

    getPiePercentagesColor: function(){
        return this.piePercentagesColorPicker.getValue();
    },

    setPiePercentagesColor: function(value){
        if (value != null && value != 'none')
            this.piePercentagesColorPicker.select(value);
    },

    getHidePiePercentageNumber: function() {
        return this.piePercentageHideNumber.getValue();
    },

    setHidePiePercentageNumber: function(value) {
        this.piePercentageHideNumber.setValue(value);
    },

    getColorPalette: function() {
        return this.colorPaletteComboBox.getValue();
    },

    setColorPalette: function(value) {
        this.colorPaletteComboBox.setValue(value);
    }
});
