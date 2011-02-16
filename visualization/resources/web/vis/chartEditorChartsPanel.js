/*
 * Copyright (c) 2011 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext.namespace("LABKEY.vis");

Ext.QuickTips.init();

LABKEY.vis.ChartEditorChartsPanel = Ext.extend(Ext.FormPanel, {
    constructor : function(config){
        Ext.apply(config, {
            title: 'Chart(s)',
            autoHeight: true,
            autoWidth: true,
            bodyStyle: 'padding: 5px',
            border: false,
            labelAlign: 'top',
            items: []
        });

        this.addEvents('chartDefinitionChanged');

        LABKEY.vis.ChartEditorChartsPanel.superclass.constructor.call(this, config);
    },

    initComponent : function() {
        // the x-axis editor panel will be laid out with 2 columns
        var columnOneItems = [];
        var columnTwoItems = [];

        columnOneItems.push({
            xtype: 'radiogroup',
            id: 'chart-layout-radiogroup',
            fieldLabel: 'Layout',
            columns: 1,
            items: [
                {
                    name: 'chart_layout',
                    id: 'single',
                    boxLabel: 'One Chart',
                    inputValue: 'single',
                    checked: true,
                    listeners: {
                        scope: this,
                        'check': function(field, checked) {
                            if(checked) {
                                this.chartLayout = 'single';
                                this.fireEvent('chartDefinitionChanged', false);
                            }
                        }
                    }
                },
                {
                    name: 'chart_layout',
                    id: 'per_subject',
                    boxLabel: 'One Chart for Each ' + this.subjectNounSingular,
                    inputValue: 'per_subject',
                    listeners: {
                        scope: this,
                        'check': function(field, checked) {
                            if(checked) {
                                this.chartLayout = 'per_subject';
                                this.fireEvent('chartDefinitionChanged', false);
                            }
                        }
                    }
                },
                {
                    name: 'chart_layout',
                    id: 'per_dimension',
                    boxLabel: 'One Chart for Each [Dimension]',
                    inputValue: 'per_dimension',
                    //hidden: true,
                    listeners: {
                        scope: this,
                        'check': function(field, checked) {
                            if(checked) {
                                this.chartLayout = 'per_dimension';
                                this.fireEvent('chartDefinitionChanged', false);
                            }
                        }
                    }
                }
            ]
        });

        columnTwoItems.push({
            xtype: 'textfield',
            fieldLabel: 'Chart Title',
            value: this.mainTitle,
            width: 300,
            listeners: {
                scope: this,
                'change': function(cmp, newVal, oldVal) {
                    this.mainTitle = newVal;
                    this.fireEvent('chartDefinitionChanged', false);
                }
            }
        });

        this.items = [{
            layout: 'column',
            items: [{
                columnWidth: .5,
                layout: 'form',
                border: false,
                bodyStyle: 'padding: 5px',
                items: columnOneItems
            },{
                columnWidth: .5,
                layout: 'form',
                border: false,
                bodyStyle: 'padding: 5px',
                items: columnTwoItems
            }]
        }];

        this.on('activate', function(){
            // if this is rendering with a saved chart, set the layout option
            Ext.getCmp(this.chartLayout).suspendEvents(false);
            Ext.getCmp('chart-layout-radiogroup').setValue(this.chartLayout, true);
            Ext.getCmp(this.chartLayout).resumeEvents();
        }, this);

        LABKEY.vis.ChartEditorChartsPanel.superclass.initComponent.call(this);
    },

    getMainTitle: function(){
        return this.mainTitle;
    },

    getChartLayout: function(){
        return this.chartLayout;
    }
});