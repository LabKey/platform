/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('LABKEY.ext4.StudyScheduleGrid', {

    extend : 'Ext.panel.Panel',

    constructor : function(config) {

        Ext4.QuickTips.init();

        Ext4.applyIf(config, {
            layout : 'border',
            frame  : false, border : false
        });

        this.callParent([config]);
    },

    initComponent : function() {

        this.items = [];
        this.items.push(this.initCenterPanel());

        this.callParent([arguments]);
    },

    initCenterPanel : function() {

        this.centerPanel = Ext4.create('Ext.panel.Panel', {
            layout : 'fit',
            border : false, frame : false
        });

        this.centerPanel.on('render', this.configureGrid, this);

        return Ext4.create('Ext.panel.Panel', {
            border : false, frame : false,
            layout : 'fit',
            region : 'center',
            items  : [this.centerPanel]
        });
    },

    configureGrid : function() {

        var handler = function(json) {
            this.centerPanel.getEl().unmask();
            this.initGrid(json.schedule);
        };

        this.centerPanel.getEl().mask('Initializing...');
        this.getData(handler, this);
    },

    initGrid : function(schedule) {

        function urlRenderer(val){
            return '<a href="' + LABKEY.ActionURL.buildURL('study', 'dataset.view', null, {datasetId: val}) + '">' +
                        '<img height="16px" width="16px" src="' + LABKEY.ActionURL.getContextPath() + '/reports/grid.gif" alt="dataset">' +
                   '</a>';
        }

        function visitRenderer(val){
            if (val.required){
                return '<div data-qtip="required" class="checked"></div>';
            } else {
                return '<div data-qtip="not required" class="unchecked"></div>';
            }
        }

        var columnItems = [{
            xtype     : 'templatecolumn',
//            locked    : true,
            text      : "Datasets",
            dataIndex : "dataset",
            width     : 275,
            tpl       : '<div data-qtip="{dataset}">{dataset}</div>'
        },{
            text      : "Data",
//            locked    : true,
            dataIndex : 'id',
            width     : 50,
            tdCls     : 'type-column',
            style     : "text-align: center",
            renderer  : urlRenderer
        }];

        var fields = [{
            name    : 'dataset',
            type    : 'string',
            mapping : 'dataset.label'
        },{
            name    : 'id',
            type    : 'string',
            mapping : 'dataset.id'
        }];

        for(var i = 0; i < schedule.timepoints.length; i++){

            var header;
            if(this.timepointType == "DATE"){
                if(schedule.timepoints[i].label != null){

                    header = '<div data-qtip="' + schedule.timepoints[i].label + '<br>Start Day: ' + schedule.timepoints[i].sequenceMin + ' <br>End Day: ' + schedule.timepoints[i].sequenceMax;
                    if(schedule.timepoints[i].cohort){
                        header = header + '<br> Cohort: ' + schedule.timepoints[i].cohort.label;
                    }
                    header = header + '">' + schedule.timepoints[i].label +'</div>';
                } else {
                    header = schedule.timepoints[i].sequenceMin;
                }
            } else {
                if(schedule.timepoints[i].label != null){
                    if(schedule.timepoints[i].sequenceMin == schedule.timepoints[i].sequenceMax){
                        header = '<div data-qtip="' + schedule.timepoints[i].label + '<br>Sequence: ' + schedule.timepoints[i].sequenceMin;
                    } else {
                        header = '<div data-qtip="' + schedule.timepoints[i].label + '<br>Sequence: ' + schedule.timepoints[i].sequenceMin + ' - ' + schedule.timepoints[i].sequenceMax;
                    }
                    if(schedule.timepoints[i].cohort){
                        header = header + '<br> Cohort: ' + schedule.timepoints[i].cohort.label;
                    }
                    header = header + '">' + schedule.timepoints[i].sequenceMin + '<br>' + schedule.timepoints[i].label +'</div>';
                } else {
                    header = '<div>' + schedule.timepoints[i].sequenceMin;
                }
            }

            var newCol = {
                text: header,
                dataIndex: schedule.timepoints[i].name,
                renderer: visitRenderer
            };
            columnItems.push(newCol);

            fields.push({
                name: schedule.timepoints[i].name,
                mapping: schedule.timepoints[i].name
            });
        }

        var columns = {
            defaults: {
                menuDisabled: true,
                sortable: false
            },
            items: columnItems
        };

        this.gridPanel = Ext4.create('Ext.grid.Panel', {
            store       : this.initScheduleStore(schedule, fields),
            border      : false,
            autoScroll  : true,
            columnLines : false,
            columns     : columns,
            selType     : 'cellmodel',
            enableColumnMove: false
        });

        this.centerPanel.add(this.gridPanel);

        // This is not approved -- just points out how horrendous layout is
        var calcWidth = 325 + (columnItems.length * 95);
        this.setWidth(calcWidth);
        this.centerPanel.setWidth(calcWidth);
    },

    initScheduleStore : function(schedule, fields) {

        Ext4.define('Schedule.View', {
            extend : 'Ext.data.Model',
            fields : fields
        });

        var config = {
            model: 'Schedule.View',
            proxy: {
                type: 'memory',
                reader: {
                    type: 'json',
                    root: 'data'
                }
            },
            data : schedule
        };

        this.scheduleStore = Ext4.create('Ext.data.Store', config);
        return this.scheduleStore;
    },

    getData : function(handler, scope) {
        Ext4.Ajax.request({
            url     : LABKEY.ActionURL.buildURL('study', 'browseStudySchedule.api'),
            method  : 'GET',
            success : function(response){
                if (handler)
                {
                    var json = Ext4.decode(response.responseText);
                    handler.call(scope || this, json);
                }
            },
            failure : function(e){
                Ext4.Msg.alert('Failure');
            },
            scope   : this
        });
    }
});