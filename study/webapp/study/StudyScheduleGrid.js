/*
 * Copyright (c) 2011 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

LABKEY.requiresExt4Sandbox(true);

function renderStudySchedule(id){
    Ext4.QuickTips.init();

    this.id = id;

    this.getData = function(id){
        Ext.Ajax.request({
            url: LABKEY.ActionURL.buildURL('study', 'browseStudySchedule.api'),
            success: function(response){
                var response = Ext.decode(response.responseText);
                this.renderGrid(response.schedule, id);
            },
            failure: function(e){
            },
            scope: this
        });
    }

    this.renderGrid = function(schedule, id){

        var columnItems = [
            {
                text: "Dataset",
                dataIndex: "dataset",
                width: 200,
                renderer: datasetRenderer
            },
            {
                text: "Data",
                dataIndex: 'id',
                width: 50, tdCls: 'type-column',
                style: "text-align: center",
                renderer: urlRenderer
            }
        ];

        for(var i = 0; i < schedule.timepoints.length; i++){
            var newCol = {
                text: schedule.timepoints[i].label != null ? '<div data-qtip="' + schedule.timepoints[i].label + '">' + schedule.timepoints[i].label +'</div>' : '',
                dataIndex: schedule.timepoints[i].name,
                tdCls: 'type-column',
                renderer: visitRenderer
            };
            columnItems.push(newCol);
        }

        var columns = {
            defaults: {
                menuDisabled: true,
                sortable: false
            },
            items: columnItems
        };

        var fields = [
            {
                name: 'dataset',
                type: 'string',
                mapping: 'dataset.label'
            },
            {
                name: 'id',
                type: 'string',
                mapping: 'dataset.id'
            }
        ];

        for(var i = 0; i < schedule.timepoints.length; i++){
            fields.push({
                name: schedule.timepoints[i].name,
                mapping: schedule.timepoints[i].name
            });
        }

        Ext4.define('Schedule.View', {
            extend : 'Ext.data.Model',
            fields : fields
        });

        var store = Ext4.create('Ext.data.Store', {
            model: 'Schedule.View',
            proxy: {
                type: 'memory',
                reader: {
                    type: 'json',
                    root: 'data'
                }
            },
            data: schedule
        });

        var scheduleGrid = Ext4.create('Ext.grid.Panel', {
            renderTo: id,
            store: store,
            border: false,
            autoScroll: true,
            columnLines: false,
            columns: columns
        });

        function urlRenderer(val){
            return '<a href="' + LABKEY.ActionURL.buildURL('study', 'dataset.view', null, {datasetId: val}) + '"><img height="16px" width="16px" src="' + LABKEY.ActionURL.getContextPath() + '/reports/grid.gif" alt="dataset"></a>'
        }

        function visitRenderer(val){
            if (val.required != null){
                return '<input type="checkbox" checked="true" disabled="true">';
            } else {
                return '<input type="checkbox" disabled="true">';
            }
        }

        function datasetRenderer(val){
            return '<div data-qtip="' + val + '">' + val + '</div>';
        }

    };

    this.getData(this.id);
}