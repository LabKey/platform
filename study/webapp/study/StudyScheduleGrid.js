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
        this.cohortsStore = Ext4.create('Ext.data.Store', {
            fields: ['label'],
            data: [{label: 'all'}]
        });

        this.cohortsCombo = Ext4.create('Ext.form.field.ComboBox', {
            hidden: true,
            fieldLabel: 'Cohort',
            labelWidth: 50,
            width: 250,
            allowBlank: false,
            editable: false,
            forceSelection: true,
            value: 'all',
            store: this.cohortsStore,
            queryMode: 'local',
            displayField: 'label',
            valueField: 'label',
            listeners:{
                scope: this,
                change: this.filterCohort
            }
        });

        this.enablePagingCheckbox = Ext4.create('Ext.form.field.Checkbox', {
            fieldLabel: "Enable Paging",
            value: false,
            scope: this,
            handler: this.enablePaging
        });

        this.nextButton = Ext4.create('Ext.button.Button', {
            text: 'Next',
            disabled: true,
            hidden: true,
            scope: this,
            handler: this.nextPage
        });

        this.prevButton = Ext4.create('Ext.button.Button', {
            text: 'Previous',
            disabled: true,
            hidden: true,
            scope: this,
            handler: this.previousPage
        });

        this.pageDisplay = Ext4.create('Ext.form.field.Display', {
            fieldLabel: "Page",
            disabled: true,
            hidden: true,
            labelWidth: 40,
            value: '1'
        });

        this.centerPanel = Ext4.create('Ext.panel.Panel', {
            layout : 'fit',
            border : false, frame : false,
            tbar: [
                this.enablePagingCheckbox,
                this.prevButton,
                this.nextButton,
                this.pageDisplay
            ],
            bbar: [
                this.cohortsCombo
            ]
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
            this.schedule = json.schedule;
            this.centerPanel.getEl().unmask();
            this.initGrid(this.initColumns(this.schedule.timepoints), this.initScheduleStore(this.schedule, this.initFields()));
        };

        this.centerPanel.getEl().mask('Initializing...');
        this.getData(handler, this);
    },

    initGrid : function(columnItems, store) {
        var columns = {
            defaults: {
                menuDisabled: true,
                sortable: false
            },
            items: columnItems
        };

        this.gridPanel = Ext4.create('Ext.grid.Panel', {
            store       : store,
            border      : false,
            autoScroll  : true,
            columnLines : false,
            columns     : columns,
            selType     : 'cellmodel',
            enableColumnMove: false
        });

        this.centerPanel.removeAll();
        this.centerPanel.add(this.gridPanel);

        // This is not approved -- just points out how horrendous layout is
        // 275 (datasets) + 50 (data) + (100 x # of timepoints)
        var calcWidth = 325 + ((columnItems.length - 2) * 100);
        this.setWidth(calcWidth);
        this.centerPanel.setWidth(calcWidth);
        this.cohortsCombo.setVisible(true);
    },

    initFields : function(){

        var fields = [{
            name    : 'dataset',
            type    : 'string',
            mapping : 'dataset.label'
        },{
            name    : 'id',
            type    : 'string',
            mapping : 'dataset.id'
        }];

        for(var i = 0; i < this.schedule.timepoints.length; i++){
            fields.push({
                name: this.schedule.timepoints[i].name,
                mapping: this.schedule.timepoints[i].name
            });

            //Here we also update the cohortStore so we can filter by cohort.
            if(this.schedule.timepoints[i].cohort && this.cohortsStore.find('label', this.schedule.timepoints[i].cohort.label) == -1){
                this.cohortsStore.add({label: this.schedule.timepoints[i].cohort.label});
            }
        }

        return fields;
    },

    initColumns : function(visibleColumns){

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

        var header;

        for(var i = 0; i < visibleColumns.length; i++){

            header = '';
            
            if(this.timepointType == "DATE"){
                if(visibleColumns[i].label != null){

                    header = '<div data-qtip="' + visibleColumns[i].label + '<br>Start Day: ' + visibleColumns[i].sequenceMin + ' <br>End Day: ' + visibleColumns[i].sequenceMax;
                    if(visibleColumns[i].cohort){
                        header = header + '<br> Cohort: ' + visibleColumns[i].cohort.label;
                    }
                    header = header + '">' + visibleColumns[i].label +'</div>';
                } else {
                    header = visibleColumns[i].sequenceMin;
                }
            } else {
                if(visibleColumns[i].label != null){
                    if(visibleColumns[i].sequenceMin == visibleColumns[i].sequenceMax){
                        header = '<div data-qtip="' + visibleColumns[i].label + '<br>Sequence: ' + visibleColumns[i].sequenceMin;
                    } else {
                        header = '<div data-qtip="' + visibleColumns[i].label + '<br>Sequence: ' + visibleColumns[i].sequenceMin + ' - ' + visibleColumns[i].sequenceMax;
                    }
                    if(visibleColumns[i].cohort){
                        header = header + '<br> Cohort: ' + visibleColumns[i].cohort.label;
                    }
                    header = header + '">' + visibleColumns[i].sequenceMin + '<br>' + visibleColumns[i].label +'</div>';
                } else {
                    header = '<div>' + visibleColumns[i].sequenceMin;
                }
            }

            var newCol = {
                text: header,
                dataIndex: visibleColumns[i].name,
                renderer: visitRenderer
            };
            columnItems.push(newCol);
        }

        return columnItems;
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
    },

    filterCohort : function(combo, newValue){
        this.filteredColumns = [];

        if(newValue != 'all'){
            this.filterColumns = true;
            //filter timepoints, call initGrid with filtered list.
            for(var i = 0; i < this.schedule.timepoints.length; i++){
                if(this.schedule.timepoints[i].cohort && this.schedule.timepoints[i].cohort.label == newValue){
                    this.filteredColumns.push(this.schedule.timepoints[i]);
                }
            }

            if(this.enablePagingCheckbox.getValue() == true){
                //if paging then call changePage(0) and reset value of page counter
                this.pageDisplay.setValue(1);
                this.changePage(0);
            } else {
                //else initGrid with all columns
                this.initGrid(this.initColumns(this.filteredColumns), this.scheduleStore);
            }
        } else {
            this.filterColumns = false;
            if(this.enablePagingCheckbox.getValue() == true){
                //if paging then call changePage(0) and reset value of page counter
                this.pageDisplay.setValue(1);
                this.changePage(0);
            } else {
                //else initGrid with all columns
                this.initGrid(this.initColumns(this.schedule.timepoints), this.scheduleStore);
            }
        }
    },

    enablePaging : function(checkbox, checked){
        if(checked){
            this.prevButton.setVisible(true);
            this.nextButton.setVisible(true);
            this.pageDisplay.setVisible(true);
            //Change the column model to use a paged set of columns.

            this.pagedColumns = []; // Current set of columns in the 'page'.

            var value = this.pageDisplay.getValue();
            if(value > 1){
                this.prevButton.setDisabled(false);
            }
            this.nextButton.setDisabled(false);
            this.pageDisplay.setDisabled(false);
            this.changePage(0);
        } else {
            if(this.filterColumns){
                // use the filteredColumns set of columns.
                this.initGrid(this.initColumns(this.filteredColumns), this.scheduleStore);

            } else {
                // use the unfiltered set of columns.
                this.initGrid(this.initColumns(this.schedule.timepoints), this.scheduleStore);
            }
            this.prevButton.setDisabled(true);
            this.nextButton.setDisabled(true);
            this.pageDisplay.setDisabled(true);
            this.prevButton.setVisible(false);
            this.nextButton.setVisible(false);
            this.pageDisplay.setVisible(false);
            this.pageDisplay.setValue(1);
        }
    },

    nextPage : function() {
        var val = this.pageDisplay.getValue();
        val++;
        this.pageDisplay.setValue(val);
        if(val > 1 && this.prevButton.disabled){
            this.prevButton.setDisabled(false);
        }
        this.changePage(--val);
    },

    previousPage : function() {
        var val = this.pageDisplay.getValue();
        val--;
        this.pageDisplay.setValue(val);
        if(val == 1){
            this.prevButton.setDisabled(true);
        }
        this.changePage(--val);
    },

    changePage : function(page){

        var pageSize = 5; // Number of columns per page. Maybe we'll let the user change this?
        var columns;

        if(this.filterColumns){
            // use the filteredColumns set of columns.
            columns = this.filteredColumns;
        } else {
            // use the unfiltered set of columns.
            columns = this.schedule.timepoints
        }

        var firstColumn = page * pageSize;
        var lastColumn = (firstColumn + 4) < (columns.length - 1) ? firstColumn + 4 : columns.length -1;
        if(lastColumn == columns.length -1){
            this.nextButton.setDisabled(true);
        } else {
            if(this.nextButton.disabled){
                this.nextButton.setDisabled(false);
            }
        }

        this.pagedColumns = [];

        for(var i = firstColumn; i <= lastColumn; i++){
            this.pagedColumns.push(columns[i]);
        }
        this.initGrid(this.initColumns(this.pagedColumns), this.scheduleStore);
    }
});