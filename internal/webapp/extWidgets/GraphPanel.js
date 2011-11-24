/*
 * Copyright (c) 2010-2011 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
LABKEY.requiresExt4ClientAPI();
Ext4.namespace('LABKEY.ext');

/*
Experimental

config:
    @param (object) [store] Required. An Ext4 store containing the data to be graphed.
    @param (mixed) [xField] Required. Either string with fieldname or array or fieldnames.
    @param  (mixed) [yField] Required. Either string with fieldname or array or fieldnames.

    @param (object) [axisConfig] Optional. An object with Ext config to be applied to the axes.  Should contain properties corresponding to the position of the axis to change, ie. (left: {...}, bottom: {...}}
    @param (object) [axisConfig.bottom] Optional. An object with Ext config to be applied to the bottom axis.
    @param (object) [axisConfig.left] Optional. An object with Ext config to be applied to the left axis.
    @param (object) [axisConfig.right] Optional. An object with Ext config to be applied to the right axis.
    @param (mixed) [groupBy] Optional. An array or  comma separated list of the fields to use in grouping.
    @param (boolean) [combineGroups] Optional. If true, each group will be displayed as a series in this chart.  If false, each group will receive a separate chart.  Defaults to true.
    @event axescustomize
    @event seriescustomize

example:
    Ext4.create('LABKEY.ext.GraphPanel', {
        store: Ext4.create('LABKEY.ext4.Store', {
            schemaName: 'assay',
            queryName: 'TruCount Data',
            sort: 'subjectid,population,sampledate',
            maxRows: 1,
            autoLoad: true,
            filterArray: [LABKEY.Filter.create('SampleDate', null, LABKEY.Filter.Types.NONBLANK)]
        }),
        title: 'TruCount Data',
        xField: 'sampleDate',
        yField: 'cellCount',
        groupBy: ['subjectId', 'population'],
        renderTo: 'testDiv'
    });
 */

Ext4.define('LABKEY.ext.GraphPanel', {
    extend: 'Ext.panel.Panel',
    alias: 'widget.labkey-graphpanel',
    initComponent: function(){
        Ext4.apply(this, {
            combineGroups: true,
            tbar: [{
//                xtype: 'button',
//                text: 'Reset',
//                handler: function(btn){
//                    var panel = btn.up('panel');
//                    var chart = panel.down('chart');
//                    if(chart){
//                        chart.render();
//                        chart.redraw();
//                    }
//                }
//            },{
                xtype: 'button',
                text: 'Zoom',
                handler: this.showZoomWindowHandler,
                scope: this
            },{
                xtype: 'button',
                text: 'Group Data',
                scope: this,
                handler: this.showGroupDataHandler
            }]
        });

        this.callParent(arguments);
        this.addEvents('axescustomize', 'seriescustomize');

        if(this.store && this.store.model && this.store.model.prototype.fields.length){
            this.addChart();
        }
        else {
            this.store.on('load', this.addChart, this);
        }
    },

    addChart: function(){
        if(this.groupBy)
            this.setupGroupBy();

        this.inferFields();
        var axes = this.generateAxes();
        var series = this.generateSeries(axes);
        this.removeAll();
        this.add({
            xtype: 'chart',
            legend: {
                position: 'right'
            },
            animate: true,
//            autoSize: true,
            height: 500,
            width: 900,
            store: this.store,
            axes: axes,
            series: series,
            mask: true
//            listeners: {
//                select: {
//                    scope: this,
//                    fn: function(me, selection) {
//                        this.setZoom(selection);
//                        me.mask.hide();
//                    }
//                }
//            }
        });

        if(this.rendered)
            this.doLayout();
    },

    setupGroupBy: function(){
        var groupBy = this.groupBy;
        if(groupBy){
            if(!Ext4.isArray(groupBy))
                groupBy = groupBy.split(',');

            for(var i=0;i<groupBy.length;i++){
                this.store.groupers.add({
                    property: groupBy[i]
                });
            }

            this.store.getGroupString = function(instance) {
                var parts = [];
                for(var i=0;i<groupBy.length;i++){
                    parts.push(instance.get(groupBy[i]) || '');
                }
                return parts.join('/');
            }
            this.store.group();
        }
    },

    inferFields: function(){
        this.xField = this.inferField(this.xField);
        this.yField = this.inferField(this.yField);
    },

    inferField: function(fieldName){
        if(typeof fieldName == 'string')
            fieldName = [fieldName];

        return fieldName;
    },

    generateAxes: function(){
        var x = this.generateAxis(this.xField, 'bottom');
        var y = this.generateAxis(this.yField, 'left');
        var axes = [x, y];

        this.fireEvent('axescustomize', axes);
        return axes;
    },

    generateAxis: function(fields, position){
        var meta = this.store.model.prototype.fields;
        var label = meta.get(fields[0]).caption;

        var axis = {
            fields: fields,
            title: label,
            type: this.getTypeFromMeta(meta.get(fields[0])),
            position: position,
            constrain: true,
            adjustMaximumByMajorUnit: true,
            adjustMinimumByMajorUnit: true
        };

        if(axis.position == 'bottom'){
            Ext4.Object.merge(axis, {
                label: {
                    rotate: {
                        degrees: 315
                    }
                }
            });
        }
        if(axis.type == 'Time'){
            Ext4.apply(axis, {
                dateFormat: 'Y M d'
                //step: [Ext4.Date.YEAR, 1]
            });
        }

        if(this.axisConfig && this.axisConfig[axis.position]){
            Ext4.Object.merge(axis, this.axisConfig[axis.position])
        }
        return axis;
    },

    getTypeFromMeta: function(meta){
        var type = (meta.jsonType || meta.type).toLowerCase();
        switch (type){
            case 'date':
                type = 'Time';
                break;
            case 'int':
            case 'integer':
            case 'float':
                type = 'Numeric';
                break;
            case 'string':
                type = 'Category';
                break;
            default:
                type = 'Numeric';
        }

        return type;
    },

    generateSeries: function(axes){
        var series = [];

        var groups = this.getGroups();

        Ext4.each(groups, function(group){
            Ext4.each(this.yField, function(field){
                series.push(this.generateSingleSeries(axes, field, group));
            }, this);
        }, this);

        this.fireEvent('seriescustomize', series);

        return series;
    },

    generateSingleSeries: function(axes, field, group){
        var title = group ? group : '';
        var s = {
            type: 'line',
            axis: [],
            highlight: true,
            xField: this.xField[0],
            yField: field,
            title: title,
            tips: {
                trackMouse: true,
                renderer: this.generateSeriesRenderer(title),
                constrainPosition: true
            }
        };

        var seriesAxes = [];
        if(axes){
            Ext4.Array.each(axes, function(axis){
                if(axis.fields.indexOf(s.xField) != -1 || axis.fields.indexOf(s.yField) != -1){
                    s.axis.push(axis.position);
                    seriesAxes.push(axis)
                }
            }, this);
        }

        if(group){
            function eachRecord(fn, scope) {
                var store = this.chart.store;
                store.each(function(rec, idx){
                    if(group && store.getGroupString(rec) != group){
                        return;
                    }

                    fn.call(scope, rec, idx);
                }, this);
            }

            function getRecordCount() {
                var chart = this.chart,
                    store = chart.store,
                    group = store.getGroups(group)
                    ;
                return group ? group.children.length : 0;
            }

            Ext4.apply(s, {
                eachRecord: eachRecord,
                getRecordCount: getRecordCount
            });
        }

        return s;
    },

    getGroups: function(name){
        return Ext4.Array.map(this.store.getGroups(name), function(item){
            return item.name;
        });
    },

    generateSeriesRenderer: function(title){
        return function(storeItem, item) {
            this.removeAll();
            var val;
            var toAdd = [];
            if(title)
                toAdd.push({
                    frame : false,
                    border: false,
                    bodyStyle: 'background: transparent;',
                    html: '<b>' + title + '</b><hr>'
                });
            storeItem.fields.each(function(field){
                if(!field.hidden && field.shownInDetailsView!==false){
                    val = storeItem.get(field.name);
                    if(val && field.jsonType == 'date'){
                        val = val.format(field.format || 'Y-m-d')
                    }

                    toAdd.push({
                        frame : false,
                        border: false,
                        bodyStyle: 'background: transparent;',
                        html: field.fieldLabel + ': ' + (!Ext4.isEmpty(val) ? val : '')
                    });
                }
            }, this);
            this.add(toAdd);
        }
    },

    showZoomWindowHandler: function(btn){
        var chart = btn.up('panel').down('chart');
        if(!chart)
            return;

        var items = [];
        chart.axes.each(function(axis, idx){
            var xtype;
            switch (axis.type){
                case 'Time':
                    xtype = 'datefield';
                    break;
                case 'Numeric':
                    xtype = 'numberfield';
                    break;
                case 'Category':
                    xtype = 'combo';
                    break;
            }

            items.push({
                xtype: 'form',
                bodyStyle: 'padding: 5px;',
                itemId: 'axis' + idx,
                axisIdx: idx,
                items: [{
                    xtype: 'displayfield',
                    value: 'Position: ' + axis.position
                },{
                    fieldLabel: 'Minimum',
                    itemId: 'min',
                    xtype: xtype,
                    value: (xtype=='datefield' ? new Date(axis.from) : axis.from)
                },{
                    fieldLabel: 'Maximum',
                    xtype: xtype,
                    itemId: 'max',
                    value: (xtype=='datefield' ? new Date(axis.to) : axis.to)
                }]
            })
        }, this);

        Ext4.create('Ext.window.Window', {
            title: 'Resize Axes',
            defaults: {
                border: false
            },
            items: items,
            buttons: [{
                text: 'Submit',
                scope: this,
                handler: function(btn){
                    var chart = this.down('chart');
                    var filters = [];

                    btn.up('window').items.each(function(item){
                        var axis = chart.axes.getAt(item.axisIdx);
                        var fields = axis.fields;
                        if(!Ext4.isArray(fields))
                            fields = fields.split(',');

                        var min = Number(item.down('#min').getValue());
                        var max = Number(item.down('#max').getValue());

                        if(min != axis.from){
                            axis.from = min;
                            axis.minimum = min;
                        }
                        if(max != axis.to){
                            axis.to = max;
                            axis.maximum = max;
                        }
                    }, this);

                    btn.up('window').hide();
                }
            },{
                text: 'Cancel',
                handler: function(btn){
                    btn.up('window').hide();
                }
            }]
        }).show();
    },

    showGroupDataHandler: function(btn){
        var chart = btn.up('panel').down('chart');
        if(!chart)
            return;

        var items = [];
        var fields = chart.store.model.prototype.fields;
        var axisFields = [];
        chart.axes.each(function(axis){
            axisFields = axisFields.concat(axis.fields);
        }, this);

        fields.each(function(field){
            if(axisFields.indexOf(field.name) == -1){
                items.push({
                    fieldLabel: field.caption,
                    xtype: 'checkbox',
                    name: field.name,
                    checked: (this.groupBy && this.groupBy.indexOf(field.name) != -1)
                });
            }
        }, this);

        Ext4.create('Ext.window.Window', {
            title: 'Group Data',
            width: 300,
            defaults: {
                border: false
            },
            items: [{
                xtype: 'form',
                bodyStyle: 'padding: 5px;',
                items: items
            }],
            buttons: [{
                text: 'Submit',
                scope: this,
                handler: function(btn){
                    var chart = this.down('chart');
                    var filters = [];

                    var values = btn.up('window').down('form').getForm().getFieldValues();
                    this.groupBy = [];
                    for(var i in values){
                        if(values[i])
                            this.groupBy.push(i);
                    }

                    this.addChart();
                    btn.up('window').hide();
                }
            },{
                text: 'Cancel',
                handler: function(btn){
                    btn.up('window').hide();
                }
            }]
        }).show();
    }
});