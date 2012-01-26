/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
function renderStudySchedule(id){
    Ext4.QuickTips.init();

    this.id = id;

    this.getData = function(id){
        Ext4.Ajax.request({
            url: LABKEY.ActionURL.buildURL('study', 'browseStudySchedule.api'),
            success: function(response){
                var study = Ext4.decode(response.responseText);
                this.renderGrid(study.schedule, id);
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
                width: 275,
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
            var newCol = {
                text: schedule.timepoints[i].label != null ? '<div data-qtip="' + schedule.timepoints[i].label + '">' + schedule.timepoints[i].label +'</div>' : schedule.timepoints[i].sequenceMin,
                dataIndex: schedule.timepoints[i].name,
                tdCls: 'type-column',
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
            columns: columns,
            enableColumnMove: false,
            selType: 'rowmodelfixed'
        });

        function urlRenderer(val){
            return '<a href="' + LABKEY.ActionURL.buildURL('study', 'dataset.view', null, {datasetId: val}) + '"><img height="16px" width="16px" src="' + LABKEY.ActionURL.getContextPath() + '/reports/grid.gif" alt="dataset"></a>'
        }

        function visitRenderer(val){
            if (val.required != null){
                return '<div data-qtip="required" class="checked"></div>';
            } else {
                return '<div data-qtip="not required" class="unchecked"></div>';
            }
        }

        function datasetRenderer(val){
            return '<div data-qtip="' + val + '">' + val + '</div>';
        }

    };

    this.getData(this.id);
}