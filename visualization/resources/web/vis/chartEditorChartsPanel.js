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

        this.addEvents(
            'chartLayoutChanged',
            'chartTitleChanged'
        );

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
                    boxLabel: 'One Chart',
                    inputValue: 'single',
                    checked: true,
                    listeners: {
                        scope: this,
                        'check': function(field, checked) {
                            if(checked) {
                                this.fireEvent('chartLayoutChanged', 'single');
                            }
                        }
                    }
                },
                {
                    name: 'chart_layout',
                    boxLabel: 'One Chart for Each ' + this.viewInfo.subjectNounSingular,
                    inputValue: 'per_subject',
                    listeners: {
                        scope: this,
                        'check': function(field, checked) {
                            if(checked) {
                                this.fireEvent('chartLayoutChanged', 'per_subject');
                            }
                        }
                    }
                },
                {
                    name: 'chart_layout',
                    id: 'chart-layout-per-dimension',
                    boxLabel: 'One Chart for Each [Dimension]',
                    inputValue: 'per_dimension',
                    //hidden: true,
                    listeners: {
                        scope: this,
                        'check': function(field, checked) {
                            if(checked) {
                                this.fireEvent('chartLayoutChanged', 'per_dimension');
                            }
                        },
//                        'show': function(cmp) {
//                            console.log(cmp.isVisible());
//                            cmp.getContainer().doLayout();
//                        },
//                        'hide': function(cmp) {
//                            console.log(cmp.isVisible());
//                            cmp.getContainer().doLayout();
//                        }
                    }
                }
            ]
        });

        columnTwoItems.push({
            xtype: 'textfield',
            fieldLabel: 'Chart Title',
            width: 300,
            listeners: {
                scope: this,
                'change': function(cmp, newVal, oldVal) {
                    this.fireEvent('chartTitleChanged', newVal);
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

        LABKEY.vis.ChartEditorChartsPanel.superclass.initComponent.call(this);
    }
});