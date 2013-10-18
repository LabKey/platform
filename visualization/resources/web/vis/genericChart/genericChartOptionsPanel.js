/*
 * Copyright (c) 2012-2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('LABKEY.vis.GenericChartOptionsPanel', {

    extend : 'LABKEY.vis.GenericOptionsPanel',

    constructor : function(config){
        if (!config.renderType || config.renderType == 'null') {
            config.renderType = 'box_plot';
        }

        if (config.pointType == null) {
           config.pointType = 'outliers';
        }

        Ext4.applyIf(config, {
            renderType: 'box_plot',
            width: 310
        });

        this.callParent([config]);

        this.addEvents(
                'chartDefinitionChanged'
        );
    },

    initComponent : function() {

        var labelSeparator = '';
        var labelWidth = 85;

        this.jitterCheckbox = Ext4.create('Ext.form.field.Checkbox', {
            name: 'jitter',
            fieldLabel: "Jitter Points?",
            labelSeparator: labelSeparator,
            labelWidth: labelWidth,
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
            this.getRenderTypeCombo(labelWidth, labelSeparator),
            this.getPointCombo(labelWidth, labelSeparator),
            this.jitterCheckbox,
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

    getRenderTypeCombo: function(labelWidth, labelSeparator){
        if (!this.renderTypeCombo) {
            var renderTypes = [
                {renderType: 'scatter_plot', label: 'Scatter Plot'},
                {renderType: 'box_plot', label: 'Box Plot'},
                {renderType: 'auto_plot', label: 'Auto Plot'}
            ];

            if(this.customRenderTypes){
                for(var renderType in this.customRenderTypes){
                    renderTypes.push({
                        renderType: renderType,
                        label: this.customRenderTypes[renderType].label
                    });
                }
            }

            this.renderTypeStore = Ext4.create('Ext.data.Store', {
                fields: ['renderType', 'label'],
                data: renderTypes
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
        }

        return this.renderTypeCombo;
    },

    getPointCombo: function(labelWidth, labelSeparator){
        if (!this.pointCombo) {
            var pointTypes = [
                {pointType: 'outliers', label: 'Outliers Only'},
                {pointType: 'all', label: 'All'},
                {pointType: 'none', label: 'None'}
            ];

            this.pointTypeStore = Ext4.create('Ext.data.Store', {
                fields: ['pointType', 'label'],
                data: pointTypes
            });

            this.pointTypeCombo = Ext4.create('Ext.form.ComboBox', {
                fieldLabel: 'Show Points',
                store: this.pointTypeStore,
                labelSeparator: labelSeparator,
                labelWidth: labelWidth,
                width: 293, // It'd be great if this didn't have to be hard-coded
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
        }

        return this.pointTypeCombo;
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
            pointType: this.getPointType(),
            position: this.getPosition(),
            opacity: this.getOpacity(),
            pointSize: this.getPointSize(),
            pointFillColor: this.getPointColor(),
            lineWidth: this.getLineWidth(),
            lineColor: this.getLineColor(),
            boxFillColor: this.getFillColor(),
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

        if(chartConfig.renderType){
            this.setRenderType(chartConfig.renderType);
        }

        if(chartConfig.pointType) {
            this.setPointType(chartConfig.pointType);
        }

        if(chartConfig.geomOptions.position) {
            this.setPosition(chartConfig.geomOptions.position);
        }

        if(chartConfig.geomOptions.opacity){
            this.setOpacity(chartConfig.geomOptions.opacity);
        }

        if(chartConfig.geomOptions.pointSize){
            this.setPointSize(chartConfig.geomOptions.pointSize);
        }

        if(chartConfig.geomOptions.pointFillColor){
            this.setPointColor(chartConfig.geomOptions.pointFillColor);
        }

        if(chartConfig.geomOptions.lineWidth){
            this.setLineWidth(chartConfig.geomOptions.lineWidth);
        }

        if(chartConfig.geomOptions.lineColor){
            this.setLineColor(chartConfig.geomOptions.lineColor);
        }

        if(chartConfig.geomOptions.boxFillColor){
            this.setFillColor(chartConfig.geomOptions.boxFillColor);
        }

        if(chartConfig.width){
            this.setWidth(chartConfig.width);
        }

        if(chartConfig.height){
            this.setHeight(chartConfig.height);
        }

        this.suppressEvents = false;
    },

    getRenderType: function() {
        return this.renderTypeCombo.getValue();
    },

    setRenderType: function(value){
        this.renderTypeCombo.setValue(value);
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
        if (this.getPointType() == 'all') {
            return 'none';
        }

        return this.fillColorPicker.getValue();
    },

    setFillColor: function(value){
        if(value != 'none') {
            this.fillColorPicker.select(value);
        }
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
