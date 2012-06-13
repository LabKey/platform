/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.namespace("LABKEY.vis");

Ext4.tip.QuickTipManager.init();

Ext4.define('LABKEY.vis.GenericChartOptionsPanel', {

    extend : 'LABKEY.vis.GenericOptionsPanel',

    constructor : function(config){
        if(config.renderType == 'null'){
            config.renderType = 'box_plot';
        }
        Ext4.applyIf(config, {
            renderType: 'box_plot',
            width: 300
        });

        this.callParent([config]);

        this.addEvents(
                'chartDefinitionChanged'
        );
    },

    initComponent : function() {

        var labelSeparator = '';
        var labelWidth = 80;

        this.renderTypeStore = Ext4.create('Ext.data.Store', {
            fields: ['renderType', 'label'],
            data: [
                {renderType: 'scatter_plot', label: 'Scatter Plot'},
                {renderType: 'box_plot', label: 'Box Plot'}
            ]
        });

        this.renderTypeCombo = Ext4.create('Ext.form.ComboBox', {
            fieldLabel: 'Plot Type',
            store: this.renderTypeStore,
            labelSeparator: labelSeparator,
            labelWidth: labelWidth,
            width: 275, // It'd be great if this didn't have to be hard-coded
            queryMode: 'local',
            editable: false,
            forceSelection: true,
            displayField: 'label',
            valueField: 'renderType',
            value: this.renderType,
            listeners: {
                change: function(combo){
                    if(combo.getValue() == 'scatter_plot'){
                        this.enableScatterPlotOptions();
                    } else if(combo.getValue() == 'box_plot'){
                        this.enableBoxPlotOptions();
                    }
                    if(!this.suppressEvents){
                        this.fireEvent('chartDefinitionChanged', this);
                    }
                },
                scope: this
            }
        });

        this.opacitySlider = Ext4.create('Ext.slider.Single', {
            hidden: this.renderType == 'box_plot',
            labelSeparator: labelSeparator,
            labelWidth: labelWidth,
            fieldLabel: 'Opacity',
            width: '100%',
            value: 50,
            increment: 10,
            minValue: 10,
            maxValue: 100,
            listeners: {
                change: function(combo){
                    if(!this.suppressEvents){
                        this.fireEvent('chartDefinitionChanged', this);
                    }
                },
                scope: this
            }
        });

        this.pointSizeSlider = Ext4.create('Ext.slider.Single', {
            hidden: this.renderType == 'box_plot',
            labelSeparator: labelSeparator,
            labelWidth: labelWidth,
            fieldLabel: 'Size',
            width: '100%',
            value: 5,
            increment: 1,
            minValue: 1,
            maxValue: 10,
            listeners: {
                change: function(combo){
                    if(!this.suppressEvents){
                        this.fireEvent('chartDefinitionChanged', this);
                    }
                },
                scope: this
            }
        });

        this.lineWidthSlider = Ext4.create('Ext.slider.Single', {
            hidden: this.renderType == 'scatter_plot',
            labelSeparator: labelSeparator,
            labelWidth: labelWidth,
            fieldLabel: 'Line Width',
            width: '100%',
            value: 1,
            increment: 1,
            minValue: 1,
            maxValue: 10,
            listeners: {
                change: function(combo){
                    if(!this.suppressEvents){
                        this.fireEvent('chartDefinitionChanged', this);
                    }
                },
                scope: this
            }
        });

        this.items = [
            this.renderTypeCombo,
            this.opacitySlider,
            this.pointSizeSlider,
            this.lineWidthSlider
        ];

        this.callParent();
    },

    getPanelOptionValues: function() {
        return {
            renderType: this.getRenderType(),
            opacity: this.getOpacity(),
            pointSize: this.getPointSize(),
            lineWidth: this.getLineWidth()
        };
    },

    setPanelOptionValues: function(config){
        this.suppressEvents = true;

        if(config.renderType){
            this.setRenderType(config.renderType, true);
        }

        if(config.opacity){
            this.setOpacity(config.opacity, true);
        }

        if(config.pointSize){
            this.setPointSize(config.pointSize, true);
        }

        if(config.lineWidth){
            this.setLineWidth(config.lineWidth, true);
        }

        this.suppressEvents = false;
    },

    getRenderType: function() {
        return this.renderTypeCombo.getValue();
    },

    setRenderType: function(value){
        this.renderTypeCombo.setValue(value);
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

    getLineWidth: function(){
        return this.lineWidthSlider.getValue();
    },

    setLineWidth: function(value){
        this.lineWidthSlider.setValue(value);
    },

    enableScatterPlotOptions: function(){
        this.opacitySlider.show();
        this.pointSizeSlider.show();

        this.lineWidthSlider.hide();
    },

    enableBoxPlotOptions: function(){
        this.opacitySlider.hide();
        this.pointSizeSlider.hide();

        this.lineWidthSlider.show();
    }
});
