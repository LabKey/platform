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
                {renderType: 'box_plot', label: 'Box Plot'},
                {renderType: 'auto_plot', label: 'Auto Plot'}
            ]
        });

        this.renderTypeCombo = Ext4.create('Ext.form.ComboBox', {
            fieldLabel: 'Plot Type',
            store: this.renderTypeStore,
            labelSeparator: labelSeparator,
            labelWidth: labelWidth,
            width: 293, // It'd be great if this didn't have to be hard-coded
            queryMode: 'local',
            editable: false,
            forceSelection: true,
            displayField: 'label',
            valueField: 'renderType',
            value: this.renderType,
            listeners: {
                change: function(combo){
                    if(!this.suppressEvents){
                        this.hasChanges = true;
                    }
                },
                scope: this
            }
        });

        this.opacitySlider = Ext4.create('Ext.slider.Single', {
            labelSeparator: labelSeparator,
            labelWidth: labelWidth,
            fieldLabel: 'Point Opacity',
            width: '100%',
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
            labelSeparator: labelSeparator,
            labelWidth: labelWidth,
            fieldLabel: 'Point Size',
            width: '100%',
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
            text: 'Point Color'
        });

        this.pointColorPicker = Ext4.create('Ext.picker.Color', {
            value: '3366FF',  // initial selected color
            fieldLabel: 'Point Color',
            width: 275,
            height: 60,
            listeners: {
                select: function(picker, selColor) {
                    if(!this.suppressEvents){
                        this.hasChanges = true;
                    }
                },
                scope: this
            }
        });

        this.colorFieldContainer = Ext4.create('Ext.form.FieldContainer', {
            layout: 'hbox',
            items: [this.colorLabel, this.pointColorPicker]
        });

        this.lineColorLabel = Ext4.create('Ext.form.Label', {
            width: labelWidth,
            text: 'Line Color'
        });

        this.lineColorPicker = Ext4.create('Ext.picker.Color', {
            value: '000000',  // initial selected color
            fieldLabel: 'Line Color',
            width: 275,
            height: 60,
            listeners: {
                select: function(picker, selColor) {
                    if(!this.suppressEvents){
                        this.hasChanges = true;
                    }
                },
                scope: this
            }
        });

        this.lineColorContainer = Ext4.create('Ext.form.FieldContainer', {
            layout: 'hbox',
            items: [this.lineColorLabel, this.lineColorPicker]
        });


        this.fillColorLabel = Ext4.create('Ext.form.Label', {
            width: labelWidth,
            text: 'Fill Color'
        });

        this.fillColorPicker = Ext4.create('Ext.picker.Color', {
            value: '3366FF',  // initial selected color
            fieldLabel: 'Fill Color',
            width: 275,
            height: 60,
            listeners: {
                select: function(picker, selColor) {
                    if(!this.suppressEvents){
                        this.hasChanges = true;
                    }
                },
                scope: this
            }
        });

        this.fillColorContainer = Ext4.create('Ext.form.FieldContainer', {
            layout: 'hbox',
            items: [this.fillColorLabel, this.fillColorPicker]
        });

        this.lineWidthSlider = Ext4.create('Ext.slider.Single', {
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
                        this.hasChanges = true;
                    }
                },
                scope: this
            }
        });

        this.widthBox = Ext4.create('Ext.form.field.Number', {
            fieldLabel: 'Width',
            labelSeparator: labelSeparator,
            labelWidth: labelWidth,
            width: 293,
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
            labelSeparator: labelSeparator,
            labelWidth: labelWidth,
            width: 293,
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

        this.items = [
            this.renderTypeCombo,
            this.lineWidthSlider,
            this.opacitySlider,
            this.pointSizeSlider,
            this.colorFieldContainer,
            this.lineColorContainer,
            this.fillColorContainer,
            this.widthBox,
            this.heightBox
        ];

        this.buttons = [{
            text: 'OK',
            handler: this.applyChangesButtonClicked,
            scope: this
        },{
            text: 'Cancel',
            handler: this.cancelChangesButtonClicked,
            scope: this
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
            renderType: this.getRenderType(),
            opacity: this.getOpacity(),
            pointSize: this.getPointSize(),
            pointColor: this.getPointColor(),
            lineWidth: this.getLineWidth(),
            lineColor: this.getLineColor(),
            fillColor: this.getFillColor(),
            width: this.getWidth(),
            height: this.getHeight()
        };
    },

    restoreValues: function(initValues) {
        if (initValues.hasOwnProperty("renderType"))
            this.setRenderType(initValues.renderType);
        if (initValues.hasOwnProperty("lineWidth"))
            this.setLineWidth(initValues.lineWidth);
        if (initValues.hasOwnProperty("opacity"))
            this.setOpacity(initValues.opacity);
        if (initValues.hasOwnProperty("pointSize"))
            this.setPointSize(initValues.pointSize);
        if (initValues.hasOwnProperty("pointColor"))
            this.setPointColor(initValues.pointColor);
        if (initValues.hasOwnProperty("lineColor"))
            this.setLineColor(initValues.lineColor);
        if (initValues.hasOwnProperty("fillColor"))
            this.setFillColor(initValues.fillColor);
        if (initValues.hasOwnProperty("width"))
            this.setWidth(initValues.width);
        if (initValues.hasOwnProperty("height"))
            this.setHeight(initValues.height);

        this.hasChanges = false;
    },

    setPanelOptionValues: function(config){
        this.suppressEvents = true;

        if(config.renderType){
            this.setRenderType(config.renderType);
        }

        if(config.opacity){
            this.setOpacity(config.opacity);
        }

        if(config.pointSize){
            this.setPointSize(config.pointSize);
        }

        if(config.pointColor){
            this.setPointColor(config.pointColor);
        }

        if(config.lineWidth){
            this.setLineWidth(config.lineWidth);
        }

        if(config.lineColor){
            this.setLineColor(config.lineColor);
        }

        if(config.fillColor){
            this.setFillColor(config.fillColor);
        }

        if(config.width){
            this.setWidth(config.width);
        }

        if(config.height){
            this.setHeight(config.height);
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

    getPointColor: function(){
        return this.pointColorPicker.getValue();
    },

    setPointColor: function(value){
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
        this.lineColorPicker.select(value);
    },

    getFillColor: function(){
        return this.fillColorPicker.getValue();
    },

    setFillColor: function(value){
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
    }
});
