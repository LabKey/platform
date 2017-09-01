/*
 * Copyright (c) 2012-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('LABKEY.vis.GenericChartOptionsPanel', {

    extend : 'LABKEY.vis.GenericOptionsPanel',

    layout: 'column',

    defaultLabelWidth: 95,
    pointType: 'outliers',
    defaultOpacity: null,
    defaultChartLabel: null,
    defaultColorPaletteScale: 'ColorDiscrete',
    defaultLineWidth: null,
    userEditedLabel: false,
    userEditedSubtitle: false,
    userEditedFooter: false,
    isSavedReport: false,
    renderType: null,
    initMeasures: null,

    initComponent : function()
    {
        this.userEditedLabel = this.isSavedReport;

        this.defineGeneralOptions();
        this.defineLineOptions();
        this.definePointOptions();
        this.defineBoxOptions();
        this.defineOpacityOptions();
        this.definePieOptions();
        this.defineBinningOptions();
        this.defineTimeOptions();

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

        this.addEvents('chartLayoutChange', 'requiresDataRefresh');
    },

    defineGeneralOptions : function()
    {
        this.labelBox = Ext4.create('Ext.form.field.Text', {
            name: 'label',
            getInputValue: this.getLabel,
            hideFieldLabel: true,
            width: 153,
            padding: '0 0 10px 0',
            enableKeyEvents: true,
            value: this.getDefaultChartLabel()
        });
        this.labelBox.addListener('keyup', function()
        {
            if (this.getDefaultChartLabel() != null)
            {
                this.userEditedLabel = this.labelBox.getValue() != '';
                this.labelBoxResetButton.setDisabled(!this.userEditedLabel);
            }
        }, this, {buffer: 500});

        this.labelBoxResetButton = Ext4.create('Ext.Button', {
            disabled: !this.userEditedLabel,
            cls: 'revert-label-button',
            iconCls: 'fa fa-refresh',
            tooltip: 'Reset the title to the default value based on the Chart Type dialog selections.',
            handler: function() {
                if (this.getDefaultChartLabel() != null)
                {
                    this.userEditedLabel = false;
                    this.labelBoxResetButton.setDisabled(!this.userEditedLabel);
                    this.setLabel(this.getDefaultChartLabel());
                }
            },
            scope: this
        });

        this.labelBoxFieldContainer = Ext4.create('Ext.form.FieldContainer', {
            fieldLabel: 'Title',
            labelWidth: this.defaultLabelWidth,
            width: 275,
            layout: 'hbox',
            items: [this.labelBox, this.labelBoxResetButton]
        });

        this.subtitleBox = Ext4.create('Ext.form.field.Text', {
            name: 'subtitle',
            getInputValue: this.getSubtitle,
            fieldLabel: 'Subtitle',
            labelWidth: this.defaultLabelWidth,
            width: 275,
            padding: '0 0 10px 0',
            enableKeyEvents: true,
            layoutOptions: ['line', 'point', 'box', 'pie', 'series']
        });
        this.subtitleBox.addListener('keyup', function(){
            this.userEditedSubtitle = this.subtitleBox.getValue() != '';
        }, this, {buffer: 500});

        this.footerBox = Ext4.create('Ext.form.field.Text', {
            name: 'footer',
            getInputValue: this.getFooter,
            fieldLabel: 'Footer',
            labelWidth: this.defaultLabelWidth,
            width: 275,
            padding: '0 0 10px 0',
            enableKeyEvents: true,
            layoutOptions: ['line', 'point', 'box', 'pie']
        });
        this.footerBox.addListener('keyup', function(){
            this.userEditedFooter = this.footerBox.getValue() != '';
        }, this, {buffer: 500});

        this.widthBox = Ext4.create('Ext.form.field.Number', {
            name: 'width',
            getInputValue: this.getWidth,
            fieldLabel: 'Width (px)',
            labelWidth: this.defaultLabelWidth,
            width: 275,
            padding: '0 0 10px 0',
            allowDecimals: false,
            minValue: 250,
            step: 50
        });

        this.heightBox = Ext4.create('Ext.form.field.Number', {
            name: 'height',
            getInputValue: this.getHeight,
            fieldLabel: 'Height (px)',
            labelWidth: this.defaultLabelWidth,
            width: 275,
            padding: '0 0 10px 0',
            allowDecimals: false,
            minValue: 250,
            step: 50
        });

        this.colorPaletteFieldContainer = Ext4.create('Ext.Component',  {
            padding: '0 0 0 100px',
            html: '<div id="colorPalette" style="width: 175px; overflow-x: hidden;"></div>',
            listeners: {
                scope: this,
                render: this.renderColorPaletteDisplay
            }
        });
    },

    defineLineOptions : function()
    {
        this.lineColorPicker = Ext4.create('LABKEY.vis.ColorPickerCombo', {
            fieldLabel: 'Line Color',
            name: 'lineColor',
            getInputValue: this.getLineColor,
            value: '000000',  // initial selected color
            width: 175,
            padding: '0 0 10px 0',
            layoutOptions: 'line'
        });

        this.fillColorPicker = Ext4.create('LABKEY.vis.ColorPickerCombo', {
            fieldLabel: 'Fill Color',
            name: 'boxFillColor',
            getInputValue: this.getFillColor,
            value: '3366FF',  // initial selected color
            width: 175,
            padding: '0 0 10px 0',
            layoutOptions: 'line'
        });

        this.lineWidthSlider = Ext4.create('Ext.slider.Single', {
            name: 'lineWidth',
            getInputValue: this.getLineWidth,
            labelWidth: this.defaultLabelWidth,
            fieldLabel: 'Line Width',
            width: 270,
            padding: '0 0 10px 0',
            layoutOptions: ['line', 'time', 'series'],
            value: this.defaultLineWidth || 1,
            increment: 1,
            minValue: 1,
            maxValue: 10
        });
    },

    definePointOptions : function()
    {
        this.jitterCheckbox = Ext4.create('Ext.form.field.Checkbox', {
            name: 'position',
            getInputValue: this.getPosition,
            fieldLabel: 'Jitter Points',
            labelWidth: this.defaultLabelWidth,
            padding: '0 0 10px 0',
            layoutOptions: 'point',
            value: this.position == 'jitter'
        });

        this.pointSizeSlider = Ext4.create('Ext.slider.Single', {
            name: 'pointSize',
            getInputValue: this.getPointSize,
            labelWidth: this.defaultLabelWidth,
            fieldLabel: 'Point Size',
            width: 270,
            padding: '0 0 10px 0',
            layoutOptions: ['point', 'series'],
            value: 5,
            increment: 1,
            minValue: 1,
            maxValue: 10
        });

        this.pointColorPicker = Ext4.create('LABKEY.vis.ColorPickerCombo', {
            fieldLabel: 'Point Color',
            name: 'pointFillColor',
            getInputValue: this.getPointColor,
            value: '3366FF',  // initial selected color
            width: 175,
            padding: '0 0 10px 0',
            layoutOptions: ['point','series']
        });
    },

    defineBoxOptions : function()
    {
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
            labelWidth: this.defaultLabelWidth,
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
    },

    defineOpacityOptions : function()
    {
        this.opacitySlider = Ext4.create('Ext.slider.Single', {
            name: 'opacity',
            getInputValue: this.getOpacity,
            labelWidth: this.defaultLabelWidth,
            fieldLabel: 'Opacity',
            width: 270,
            padding: '0 0 10px 0',
            layoutOptions: 'opacity',
            value: this.defaultOpacity || 50,
            increment: 5,
            minValue: 10,
            maxValue: 100
        });
    },

    definePieOptions : function()
    {
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

        this.piePercentagesColorPicker = Ext4.create('LABKEY.vis.ColorPickerCombo', {
            fieldLabel: '% Text Color',
            name: 'piePercentagesColor',
            getInputValue: this.getPiePercentagesColor,
            value: '333333',  // initial selected color
            width: 175,
            padding: '0 0 10px 0',
            layoutOptions: 'pie'
        });

        this.gradientSlider = Ext4.create('Ext.slider.Single', {
            name: 'gradientPercentage',
            getInputValue: this.getGradientPercentage,
            labelWidth: this.defaultLabelWidth,
            fieldLabel: 'Gradient %',
            width: 270,
            padding: '0 0 10px 0',
            layoutOptions: 'pie',
            value: 95,
            increment: 5,
            minValue: 0,
            maxValue: 100
        });

        this.gradientColorPicker = Ext4.create('LABKEY.vis.ColorPickerCombo', {
            fieldLabel: 'Gradient Color',
            name: 'gradientColor',
            getInputValue: this.getGradientColor,
            value: 'FFFFFF',  // initial selected color
            width: 175,
            padding: '0 0 10px 0',
            layoutOptions: 'pie'
        });

        this.colorPaletteComboBox = Ext4.create('Ext.form.ComboBox', {
            name: 'colorPaletteScale',
            fieldLabel: 'Color Palette',
            getInputValue: this.getColorPalette,
            labelWidth: this.defaultLabelWidth,
            width: 275,
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
    },

    defineBinningOptions : function()
    {
        this.binFieldRadioGroup = Ext4.create('Ext.form.RadioGroup', {
            name: 'binThreshold',
            fieldLabel: 'Group by Density',
            getInputValue: this.getBinThreshold,
            columns: 1,
            padding: '0 0 10px 0',
            layoutOptions: 'binnable',
            items: [
                {boxLabel: 'When # of data points exceeds 10,000', inputValue: 10000, name: 'binThresh', checked: true},
                {boxLabel: 'Always', inputValue: 1, name: 'binThresh'}
            ],
            listeners: {
                scope: this,
                change: function(field, newValue, oldValue) {
                    this.toggleBinningControls(newValue.binThresh == 10000);
                }
            }
        });

        this.binShapeRadioGroup = Ext4.create('Ext.form.RadioGroup', {
            name: 'binShapeGroup',
            fieldLabel: 'Grouped Data Shape',
            getInputValue: this.getBinShape,
            columns: 1,
            padding: '0 0 10px 0',
            layoutOptions: 'binnable',
            items: [
                {boxLabel: 'Hexagon', inputValue: 'hex', name: 'binShape', checked: true},
                {boxLabel: 'Square', inputValue: 'square', name: 'binShape'}
            ]
        });

        this.binColorRadioGroup = Ext4.create('Ext.form.RadioGroup', {
            name: 'binColorGroup',
            fieldLabel: 'Density Color Palette',
            getInputValue: this.getBinColor,
            columns: 1,
            layoutOptions: 'binnable',
            items: [
                {boxLabel: 'Blue & White', inputValue: 'BlueWhite', name: 'binColor', checked: true},
                {boxLabel: 'Heat', inputValue: 'Heat', name: 'binColor'},
                {boxLabel: 'Single Color:', inputValue: 'SingleColor', name: 'binColor'}
            ]
        });

        this.binSingleColorPicker = Ext4.create('LABKEY.vis.ColorPickerCombo', {
            name: 'binSingleColor',
            getInputValue: this.getBinColorPicker,
            value: '000000',  // initial selected color
            width: 70,
            hideLabel: true,
            padding: '0 0 0 125px',
            layoutOptions: 'binnable'
        });
    },

    defineTimeOptions : function()
    {
        var subjectInfo = LABKEY.vis.TimeChartHelper.getStudySubjectInfo();

        this.hideDataPointsCheckbox = Ext4.create('Ext.form.field.Checkbox', {
            name: 'hideDataPoints',
            getInputValue: this.getHideDataPoints,
            fieldLabel: 'Hide Data Points',
            labelWidth: 135,
            padding: '0 0 10px 0',
            layoutOptions: ['time', 'series'],
            value: false
        });

        this.chartLayoutRadioGroup = Ext4.create('Ext.form.RadioGroup', {
            name: 'chartLayout',
            fieldLabel: 'Number of Charts',
            labelWidth: 135,
            getInputValue: this.getChartLayout,
            columns: 1,
            padding: '0 0 10px 0',
            layoutOptions: 'time',
            items: [
                {boxLabel: 'One Chart', inputValue: 'single', name: 'chartLayoutOption', checked: true},
                {boxLabel: 'One Per Group', inputValue: 'per_group', name: 'chartLayoutOption', hidden: true},
                {boxLabel: 'One Per ' + subjectInfo.nounSingular, inputValue: 'per_subject', name: 'chartLayoutOption'},
                {boxLabel: 'One Per Measure/Dimension', inputValue: 'per_dimension', name: 'chartLayoutOption'}
            ]
        });

        // on render attach a change listener so other tabs can be notified of chart layout changes
        this.chartLayoutRadioGroup.on('render', function(rg) {
            rg.on('change', function(rg, newValue) { this.fireEvent('chartLayoutChange', newValue.chartLayoutOption);}, this);
        }, this);

        this.chartSubjectSelectionRadioGroup = Ext4.create('Ext.form.RadioGroup', {
            name: 'chartSubjectSelection',
            fieldLabel: 'Subject Selection',
            labelWidth: 135,
            getInputValue: this.getChartSubjectSelection,
            columns: 1,
            layoutOptions: 'time',
            items: [
                {boxLabel: subjectInfo.nounPlural, inputValue: 'subjects', name: 'chartSubjectSelectionOption', checked: true},
                {boxLabel: subjectInfo.nounSingular + ' Groups', inputValue: 'groups', name: 'chartSubjectSelectionOption'}
            ],
            listeners: {
                scope: this,
                change: function(rg, newValue)
                {
                    var groupsOptionSelected = newValue.chartSubjectSelectionOption == 'groups';

                    // toggle which of the per_group/per_subject radio options are visible
                    // and transfer radio button selection between them
                    var perGroupRadio = this.getChartLayoutRadioByValue('per_group'),
                        perSubjectRadio = this.getChartLayoutRadioByValue('per_subject');
                    perGroupRadio.setVisible(groupsOptionSelected);
                    perSubjectRadio.setVisible(!groupsOptionSelected);
                    if (perGroupRadio.checked && perGroupRadio.isHidden())
                        this.setChartLayout('per_subject');
                    else if (perSubjectRadio.checked && perSubjectRadio.isHidden())
                        this.setChartLayout('per_group');

                    this.displayIndividualLinesCheckbox.setDisabled(!groupsOptionSelected);
                    this.setDisplayIndividualLines(!groupsOptionSelected);

                    this.displayAggregateLinesCheckbox.setDisabled(!groupsOptionSelected);
                    this.setDisplayAggregateLines(groupsOptionSelected);
                    this.errorBarsCombobox.setDisabled(!groupsOptionSelected);
                    this.setErrorBars('None');

                    this.fireEvent('requiresDataRefresh');
                }
            }
        });

        this.displayIndividualLinesCheckbox = Ext4.create('Ext.form.field.Checkbox', {
            name: 'displayIndividual',
            getInputValue: this.getDisplayIndividualLines,
            hideFieldLabel: true,
            boxLabel: 'Show Individual Lines',
            width: 160,
            disabled: true,
            checked: true,
            listeners : {
                scope : this,
                change : function(cmp, checked){
                    this.fireEvent('requiresDataRefresh');
                }
            }
        });

        this.showIndividualLinesFieldContainer = Ext4.create('Ext.form.FieldContainer', {
            hideFieldLabel: true,
            layoutOptions: 'time',
            width: 450,
            padding: '0 0 0 160px',
            items: [this.displayIndividualLinesCheckbox]
        });

        this.displayAggregateLinesCheckbox = Ext4.create('Ext.form.field.Checkbox', {
            name: 'displayAggregate',
            getInputValue: this.getDisplayAggregateLines,
            hideFieldLabel: true,
            boxLabel: 'Show Mean',
            width: 90,
            disabled: true,
            checked: false,
            listeners : {
                scope : this,
                change : function(cmp, checked){
                    // enable/disable the error bars combo box accordingly
                    this.errorBarsCombobox.setDisabled(!checked);
                    if (!checked)
                        this.setErrorBars('None');

                    this.fireEvent('requiresDataRefresh');
                }
            }
        });

        this.errorBarsCombobox = Ext4.create('Ext.form.field.ComboBox', {
            name: 'errorBars',
            getInputValue: this.getErrorBars,
            hideFieldLabel: true,
            width: 80,
            triggerAction: 'all',
            mode: 'local',
            store: Ext4.create('Ext.data.ArrayStore', {
                fields: ['value', 'display'],
                data: [
                    ['None', 'None'],
                    ['SD', 'Std Dev'],
                    ['SEM', 'Std Err']
                ]
            }),
            disabled: true,
            forceSelection: 'true',
            editable: false,
            valueField: 'value',
            displayField: 'display',
            value: 'None'
        });

        this.showMeanPlusErrorBarFieldContainer = Ext4.create('Ext.form.FieldContainer', {
            hideFieldLabel: true,
            layoutOptions: 'time',
            layout: 'hbox',
            width: 450,
            padding: '0 0 10px 160px',
            items: [
                this.displayAggregateLinesCheckbox,
                this.errorBarsCombobox
            ]
        });
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
            this.labelBoxFieldContainer,
            this.subtitleBox,
            //this.footerBox,
            this.widthBox,
            this.heightBox,
            this.pointTypeCombo,
            this.jitterCheckbox,
            this.opacitySlider,
            this.pointSizeSlider,
            this.pointColorPicker,
            this.lineWidthSlider,
            this.lineColorPicker,
            this.fillColorPicker,
            this.hideDataPointsCheckbox,
            this.colorPaletteComboBox,
            this.colorPaletteFieldContainer
        ];
    },

    getColumnTwoFields: function()
    {
        return [
            this.chartLayoutRadioGroup,
            this.chartSubjectSelectionRadioGroup,
            this.showIndividualLinesFieldContainer,
            this.showMeanPlusErrorBarFieldContainer,
            this.pieInnerRadiusSlider,
            this.pieOuterRadiusSlider,
            this.showPiePercentagesCheckBox,
            this.piePercentageHideNumber,
            this.piePercentagesColorPicker,
            this.gradientSlider,
            this.gradientColorPicker,
            this.binFieldRadioGroup,
            this.binShapeRadioGroup,
            this.binColorRadioGroup,
            this.binSingleColorPicker
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
            if (!field.isDisabled())
            {
                if (field.name && field.getInputValue)
                {
                    values[field.name] = field.getInputValue.call(this);
                }
                else if (field.getXType() == 'fieldcontainer')
                {
                    Ext4.each(field.items.items, function(subField) {
                        if (subField.name && subField.getInputValue)
                            values[subField.name] = subField.getInputValue.call(this);
                    }, this);
                }
            }
        }, this);

        return values;
    },

    toggleBinningControls : function(binningDisabled)
    {
        this.pointColorPicker.setDisabled(!binningDisabled);
        this.pointSizeSlider.setDisabled(!binningDisabled);
        this.opacitySlider.setDisabled(!binningDisabled);
        this.jitterCheckbox.setDisabled(!binningDisabled);
        this.colorPaletteComboBox.setDisabled(!binningDisabled);
    },

    getDefaultChartLabel : function()
    {
        return Ext4.isString(this.defaultChartLabel) && this.defaultChartLabel != '' ? this.defaultChartLabel : null;
    },

    onMeasureChange : function(measures, renderType)
    {
        this.defaultChartLabel = LABKEY.vis.GenericChartHelper.getTitleFromMeasures(renderType, measures);

        if (!this.userEditedLabel)
        {
            if (renderType == 'time_chart')
                this.setLabel(this.getDefaultChartLabel());
        }

        if (!this.userEditedSubtitle)
        {
            if (renderType == 'pie_chart' && Ext4.isDefined(measures.x) && measures.x.hasOwnProperty('label'))
                this.setSubtitle(LABKEY.vis.GenericChartHelper.getSelectedMeasureLabel(renderType, 'x', measures.x));
            else
                this.setSubtitle('');
        }

        if (!this.userEditedFooter)
        {
            if (renderType == 'pie_chart' && Ext4.isDefined(measures.y) && measures.y.hasOwnProperty('label'))
                this.setFooter(LABKEY.vis.GenericChartHelper.getSelectedMeasureLabel(renderType, 'y', measures.y));
            else
                this.setFooter('');
        }

        this.adjustColorOptionVisibility(renderType, Ext4.isDefined(measures.color), Ext4.isDefined(measures.series));
    },

    adjustColorOptionVisibility : function(renderType, hasColorMeasure, hasSeriesMeasure) {
        var visibleColor = (renderType == 'box_plot' || renderType == 'scatter_plot') && !hasColorMeasure;
        var visibleSeries = renderType == 'line_plot' && !hasSeriesMeasure;
        this.setPointColorVisible(visibleColor || visibleSeries);

        this.setFillColorVisible(!(renderType == 'bar_chart' && hasColorMeasure));
        this.setColorPalletteVisible(renderType == 'pie_chart' || hasColorMeasure);
    },

    setPointColorVisible : function(visible)
    {
        this.pointColorPicker.hideForDatatype = !visible;
    },

    setFillColorVisible : function(visible)
    {
      this.fillColorPicker.hideForDatatype = !visible;
    },

    setColorPalletteVisible : function(visible)
    {
        this.colorPaletteComboBox.hideForDatatype = !visible;
        this.colorPaletteFieldContainer.hideForDatatype = !visible;
    },

    onChartSubjectSelectionChange : function(asGroups)
    {
        this.setChartSubjectSelection(asGroups ? 'groups' : 'subjects');
    },

    validateChanges : function()
    {
        var hasValidWidth = this.widthBox.isDisabled() || this.getWidth() == null || this.getWidth() > 0,
            hasValidHeight = this.heightBox.isDisabled() || this.getHeight() == null || this.getHeight() > 0,
            hasValidBinThreshold = this.getBinThreshold() <= 10000 && this.getBinThreshold() >= 1;

        return hasValidWidth && hasValidHeight && hasValidBinThreshold;

    },

    setPanelOptionValues: function(chartConfig)
    {
        if (!Ext4.isDefined(chartConfig))
            return;

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

        if (Ext4.isDefined(chartConfig.hideDataPoints))
            this.setHideDataPoints(chartConfig.hideDataPoints);

        if (Ext4.isDefined(chartConfig.chartLayout))
            this.setChartLayout(chartConfig.chartLayout);

        if (Ext4.isDefined(chartConfig.chartSubjectSelection))
            this.setChartSubjectSelection(chartConfig.chartSubjectSelection);

        if (Ext4.isDefined(chartConfig.displayIndividual))
            this.setDisplayIndividualLines(chartConfig.displayIndividual);

        if (Ext4.isDefined(chartConfig.displayAggregate))
            this.setDisplayAggregateLines(chartConfig.displayAggregate);

        if (Ext4.isDefined(chartConfig.errorBars))
            this.setErrorBars(chartConfig.errorBars);

        if (Ext4.isDefined(chartConfig.binThreshold))
            this.setBinThreshold(chartConfig.binThreshold);

        if (Ext4.isDefined(chartConfig.binShapeGroup))
            this.setBinShape(chartConfig.binShapeGroup);

        if (Ext4.isDefined(chartConfig.binColorGroup))
            this.setBinColor(chartConfig.binColorGroup);

        if (Ext4.isDefined(chartConfig.binSingleColor))
            this.setBinColorPicker(chartConfig.binSingleColor);

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

        if (this.renderType != null && Ext4.isObject(this.initMeasures)) {
            this.adjustColorOptionVisibility(this.renderType, Ext4.isDefined(this.initMeasures.color));
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
            this.pointColorPicker.setValue(value);
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
            this.lineColorPicker.setValue(value);
    },

    getFillColor: function(){
        if (!this.pointTypeCombo.isDisabled() && this.getPointType() == 'all') {
            return 'none';
        }

        return this.fillColorPicker.getValue();
    },

    setFillColor: function(value){
        if (value != null && value != 'none')
            this.fillColorPicker.setValue(value);
    },

    getHideDataPoints: function() {
        return this.hideDataPointsCheckbox.getValue();
    },

    setHideDataPoints: function(value) {
        this.hideDataPointsCheckbox.setValue(value);
    },

    getChartLayout: function() {
        return this.chartLayoutRadioGroup.getValue().chartLayoutOption;
    },

    setChartLayout: function(value) {
        this.chartLayoutRadioGroup.setValue(value);
        var radioComp = this.getChartLayoutRadioByValue(value);
        if (radioComp)
            radioComp.setValue(true);
    },

    getChartLayoutRadioByValue: function(value) {
        return this.chartLayoutRadioGroup.down('radio[inputValue="' + value + '"]');
    },

    getChartSubjectSelection: function() {
        return this.chartSubjectSelectionRadioGroup.getValue().chartSubjectSelectionOption;
    },

    setChartSubjectSelection: function(value) {
        this.chartSubjectSelectionRadioGroup.setValue(value);
        var radioComp = this.chartSubjectSelectionRadioGroup.down('radio[inputValue="' + value + '"]');
        if (radioComp)
            radioComp.setValue(true);
    },

    getDisplayIndividualLines: function() {
        return this.displayIndividualLinesCheckbox.getValue();
    },

    setDisplayIndividualLines: function(value) {
        this.displayIndividualLinesCheckbox.setValue(value);
    },

    getDisplayAggregateLines: function() {
        return this.displayAggregateLinesCheckbox.getValue();
    },

    setDisplayAggregateLines: function(value) {
        this.displayAggregateLinesCheckbox.setValue(value);
    },

    getErrorBars: function() {
        return this.errorBarsCombobox.getValue();
    },

    setErrorBars: function(value) {
        this.errorBarsCombobox.setValue(value);
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

        if (this.getDefaultChartLabel() != value)
        {
            this.userEditedLabel = true;
            this.labelBoxResetButton.setDisabled(!this.userEditedLabel);

            if (this.getDefaultChartLabel() == null)
                this.defaultChartLabel = value;
        }
    },

    getSubtitle: function() {
        return this.subtitleBox.getValue();
    },

    setSubtitle: function(value) {
       this.subtitleBox.setValue(value);
    },

    getFooter: function() {
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
            this.gradientColorPicker.setValue(value);
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
            this.piePercentagesColorPicker.setValue(value);
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
    },

    getBinThreshold: function() {
        return this.binFieldRadioGroup.getValue().binThresh;
    },

    setBinThreshold: function(value) {
        this.binFieldRadioGroup.setValue(value);
        var radioComp = this.binFieldRadioGroup.down('radio[inputValue="' + value + '"]');
        if (radioComp)
            radioComp.setValue(true);
    },

    getBinShape: function() {
        return this.binShapeRadioGroup.getValue().binShape;
    },

    setBinShape: function(value) {
        this.binShapeRadioGroup.setValue(value);
        var radioComp = this.binShapeRadioGroup.down('radio[inputValue="' + value + '"]');
        if (radioComp)
            radioComp.setValue(true);
    },

    getBinColor: function() {
        return this.binColorRadioGroup.getValue().binColor;
    },

    setBinColor: function(value) {
        this.binColorRadioGroup.setValue(value);
        var radioComp = this.binColorRadioGroup.down('radio[inputValue="' + value + '"]');
        if (radioComp)
        {
            radioComp.setValue(true);
        }
    },

    getBinColorPicker: function() {
        return this.binSingleColorPicker.getValue();
    },

    setBinColorPicker: function(value) {
        if (value != null && value != 'none')
            this.binSingleColorPicker.setValue(value);
    }
});

Ext4.define('LABKEY.vis.ColorPickerCombo', {

    extend: 'Ext.form.field.Trigger',

    editable: false,

    colorPicker: null,

    onTriggerClick: function()
    {
        if (this.colorPicker != null)
            this.colorPicker.destroy();

        this.colorPicker = Ext4.create('Ext.picker.Color', {
            cls: 'chart-option-color-picker',
            value: this.getValue(),
            floating: true,
            allowReselect: true,
            selectOnFocus: false,
            listeners: {
                scope:this,
                select: function(field, value)
                {
                    this.setValue(value);
                    this.setFieldStyle(this.getFieldStyleStr(value));
                }
            }
        });

        var y = this.inputEl.getY() + this.getHeight();
        this.colorPicker.showAt(this.inputEl.getX(), y);

        this.focus();
        this.on('blur', function(){
            this.colorPicker.destroy();
        }, this, {single: true, delay: 100});
    },

    setValue: function(value)
    {
        this.setFieldStyle(this.getFieldStyleStr(value));
        this.callParent([value]);
    },

    getFieldStyleStr: function(value)
    {
        return 'background-color: #' + value + '; color: transparent; background-image: none;';
    }
});