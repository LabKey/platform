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

        window.onbeforeunload = LABKEY.beforeunload(this.beforeUnload, this);

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
            tbar   : [
                this.cohortsCombo,
                this.enablePagingCheckbox,
                this.prevButton,
                this.nextButton,
                this.pageDisplay
            ],
            bbar   : [{
                xtype: 'button',
                text: 'Save Changes',
                handler: this.saveChanges,
                scope: this
            },{
                xtype: 'button',
                text: 'Add Dataset',
                handler: this.addDatasetDialog,
                scope: this
            }]
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
            enableColumnMove: false,
            listeners: {
                scope: this,
                itemclick: function(view, record, html, idx){
                    if(view.getSelectionModel().getCurrentPosition().column < 2){
                        if(view.getSelectionModel().getCurrentPosition().column == 1){
                            this.expectationDatasetId = this.scheduleStore.getAt(view.getSelectionModel().getCurrentPosition().row).get('id').id;
                            this.expectationDatasetLabel = this.scheduleStore.getAt(view.getSelectionModel().getCurrentPosition().row).get('id').label;
                            var datasetType = this.scheduleStore.getAt(view.getSelectionModel().getCurrentPosition().row).get('id').type;
                            if(datasetType == 'Placeholder'){
                                this.linkDatasetDialog(this.expectationDatasetId, this.expectationDatasetLabel);
                            }
                        }
                    } else {
                        var columns;
                        if(this.enablePagingCheckbox.getValue()){
                            columns = this.pagedColumns;
                        } else if(this.filterColumns){
                            columns = this.filteredColumns;
                        } else {
                            columns = this.schedule.timepoints;
                        }

                        var timepointName = columns[view.getSelectionModel().getCurrentPosition().column -2].name;
                        var timepoint = columns[view.getSelectionModel().getCurrentPosition().column -2];

                        // Make a copy of the object so when we set the value of required the dirty bit gets set.
                        var timepointValue = Ext4.apply({}, record.data[timepointName]);
                        if(record.data[timepointName] == undefined || record.data[timepointName] == ""){
                            timepointValue = Ext4.apply({}, columns[view.getSelectionModel().getCurrentPosition().column -2]);
                        }

                        if(!timepointValue.required){
                            timepointValue.required = true;
                        } else {
                            delete timepointValue.required;
                        }
                        record.set(timepointName, timepointValue);
                    }
                }
            }
        });

        this.centerPanel.removeAll();
        this.centerPanel.add(this.gridPanel);

        // This is not approved -- just points out how horrendous layout is
        // 275 (datasets) + 50 (data) + (100 x # of timepoints)
        var calcWidth = 325 + ((columnItems.length - 2) * 100);
        if(calcWidth < 530){
            calcWidth = 565;
        }
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
            mapping : 'dataset'
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
            if(val.type == "Standard"){
                return '<a href="' + LABKEY.ActionURL.buildURL('study', 'dataset.view', null, {datasetId: val.id}) + '">' +
                        '<img height="16px" width="16px" src="' + LABKEY.ActionURL.getContextPath() + '/reports/grid.gif" alt="dataset">' +
                        '</a>';
            } else {
                return '<img height="16px" style="cursor: pointer" src="' +  LABKEY.ActionURL.getContextPath() + '/reports/link_data.png" alt="link data">';
            }
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
            text      : 'Datasets',
            dataIndex : 'dataset',
            width     : 275,
            tpl       : '<div data-qtip="{dataset}">{dataset}</div>'
        },{
            text      : 'Data',
//            locked    : true,
            dataIndex : 'id',
            width     : 50,
            tdCls     : 'type-column',
            style     : 'text-align: center',
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
            autoLoad : false,
            proxy: {
                api: {
                    update  : LABKEY.ActionURL.buildURL('study', 'updateStudySchedule.api')
                },
                type   : 'ajax',

                reader: {
                    type: 'json',
                    root: 'data'
                },
                writer: {
                    type : 'json',
                    root : 'schedule',
                    allowSingle : false
                }

            }
        };

        this.scheduleStore = Ext4.create('Ext.data.Store', config);
        this.scheduleStore.loadRawData(schedule);
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
    
    saveChanges : function(){
        this.gridPanel.getStore().sync();
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
    },

    beforeUnload : function(){
        if(this.scheduleStore.getUpdatedRecords().length > 0 || this.scheduleStore.getNewRecords().length > 0 || this.scheduleStore.getRemovedRecords.length > 0){
            return "Please save your changes."
        }
    },

    initializeCategoriesStore : function(){
        Ext4.define('Dataset.Browser.Category', {
            extend : 'Ext.data.Model',
            fields : [
                {name : 'created',      type : 'date'},
                {name : 'createdBy'                  },
                {name : 'displayOrder', type : 'int' },
                {name : 'label'                      },
                {name : 'modfied',      type : 'date'},
                {name : 'modifiedBy'                 },
                {name : 'rowid',        type : 'int' }
            ]
        });

        var config = {
            pageSize: 100,
            model   : 'Dataset.Browser.Category',
            autoLoad: true,
            autoSync: false,
            proxy   : {
                type   : 'ajax',
                api    : {
                    create  : LABKEY.ActionURL.buildURL('study', 'saveCategories.api'),
                    read    : LABKEY.ActionURL.buildURL('study', 'getCategories.api')
                },
                extraParams : {
                    // These parameters are required for specific webpart filtering
                    pageId : this.pageId,
                    index  : this.index
                },
                reader : {
                    type : 'json',
                    root : 'categories'
                },
                writer: {
                    type : 'json',
                    root : 'categories',
                    allowSingle : false
                }
            },
            listeners : {
                load : function(s, recs, success, operation, ops) {
                    s.sort('displayOrder', 'ASC');
                }
            }
        };

        return Ext4.create('Ext.data.Store', config);
    },

    addDatasetDialog : function(){
        
        var datasetPanelItems = [];

        this.categoriesCombo = Ext4.create('Ext.form.field.ComboBox', {
            fieldLabel  : 'Category',
            name        : 'category',
            store       : this.initializeCategoriesStore(),
            typeAhead   : true,
            hideTrigger : true,
            readOnly    : false,
            typeAheadDelay : 75,
            minChars       : 0,
            autoSelect     : false,
            queryMode      : 'remote',
            displayField   : 'label',
            valueField     : 'label',
            emptyText      : 'Uncategorized',
            listeners      : {
                focus      : function(combo) {
                    combo.expand();
                }
            },
            width: 340,
            labelWidth: 65
        });

        this.datasetNameField = Ext4.create('Ext.form.field.Text', {
            xtype: 'textfield',
            fieldLabel: 'Name',
            labelWidth: 65,
            width: 340
        });

        datasetPanelItems.push(this.datasetNameField, this.categoriesCombo);

        datasetPanelItems.push({
            xtype: 'displayfield',
            value: 'How would you like to define the dataset?'
        });

        var importRadio = {
            boxLabel: 'Import data from file',
            name: 'dataset',
            inputValue: 'importFromFile',
            checked: 'true'
        };
        var manualRadio = {
            boxLabel: 'Define dataset manually',
            name:'dataset',
            inputValue:'defineManually'
        };
        var placeHolderRadio = {
            boxLabel: "I don't know / I'll do this later",
            name: 'dataset',
            inputValue:'placeHolder'
        };

        this.addDatasetRadioGroup = Ext4.create('Ext.form.RadioGroup', {
            xtype: 'radiogroup',
            columns: 1,
            vertical: true,
            margin: '0 0 0 95',
            items: [importRadio, manualRadio, placeHolderRadio],
            listeners: {
                scope: this,
                change: function(radio, newVal){
                    if(radio.getValue().dataset == 'placeHolder'){
                        this.addDatasetButton.setText("Done");
                    } else {
                        this.addDatasetButton.setText("Next");
                    }
                }
            }
        });

        datasetPanelItems.push(this.addDatasetRadioGroup);
        
        this.datasetPanel = Ext4.create('Ext.form.Panel', {
            border: false,
                title: '',
                defaults: {
                    margin: '10 0 0 25'
                },
                items: [ datasetPanelItems ]
        });

        var bbarItems = [{xtype: 'tbfill'}];

        this.addDatasetButton = Ext4.create('Ext.button.Button', {
            text: 'Next',
            scope: this,
            handler: this.addDatasetHandler
        });

        this.cancelDatasetButton = Ext4.create('Ext.button.Button', {
            xtype: 'button',
                text: 'Cancel',
                scope: this,
                handler: function(btn){
                    this.addDatasetWindow.close();
                }
        });

        bbarItems.push(this.cancelDatasetButton, this.addDatasetButton);

        this.addDatasetWindow = Ext4.create('Ext.window.Window', {
            title: 'New Dataset',
            height: 225,
            width: 400,
            layout: 'fit',
            bbar: bbarItems,
            items: [this.datasetPanel]
        });
        
        this.addDatasetWindow.show();
    },

    linkDatasetDialog : function(datasetId, datasetLabel){

        var datasets = [];

        for(var i = 0; i < this.schedule.data.length; i++){
            var record = {};
            record.label = this.schedule.data[i].dataset.label;
            record.id = this.schedule.data[i].dataset.id
            datasets.push(record);
        }

        var datasetStore = Ext4.create('Ext.data.Store', {
            fields: ['label', 'id'],
            data: datasets
        });

        this.datasetCombo = Ext4.create('Ext.form.field.ComboBox', {
            hidden: true,
            fieldLabel: 'Expectation',
            width: 320,
            allowBlank: false,
            editable: false,
            forceSelection: true,
            value: 'asdf',
            store: datasetStore,
            queryMode: 'local',
            displayField: 'label',
            valueField: 'id',
            margin: '10 0 0 45',
            listeners      : {
                render     : function(combo) {
                    var store = combo.getStore();
                    combo.setValue(store.getAt(0));
                }
            }
        });

        var importRadio = {
            boxLabel: 'Import data from file',
            name: 'deftype',
            inputValue: 'linkImport',
            checked: 'true'
        };
        var manualRadio = {
            boxLabel: 'Define dataset manually',
            name:'deftype',
            inputValue:'linkManually'
        };

        this.addDatasetGroup = Ext4.create('Ext.form.RadioGroup', {
            xtype: 'radiogroup',
            columns: 1,
            vertical: true,
            margin: '10 0 0 45',
            items: [importRadio, manualRadio]
        });

        var existingRadio = {
            boxLabel: 'Link to existing dataset',
            name:'link',
            inputValue:'existing'
        };

        var newRadio = {
            boxLabel: 'Define new dataset',
            name:'link',
            inputValue:'new',
            checked: 'true'
        };

        this.linkDatasetGroup = Ext4.create('Ext.form.RadioGroup', {
            xtype: 'radiogroup',
            margin: '10 0 0 25',
            items: [newRadio, existingRadio],
            listeners: {
                change: function(group, newVal){
                    if(newVal.link == 'existing'){
                        this.datasetCombo.setVisible(true);
                        this.addDatasetGroup.setVisible(false);
                    } else {
                        this.datasetCombo.setVisible(false);
                        this.addDatasetGroup.setVisible(true);
                    }
                },
                scope: this
            }
        });

        this.linkDatasetWindow = Ext4.create('Ext.window.Window', {
            title: 'Link Dataset',
            height: 200,
            width: 400,
            layout: 'fit',
            scope: this,
            bbar: [{
                xtype: 'tbfill'
            },{
                xtype: 'button',
                align: 'right',
                text: 'Cancel',
                handler: function(){
                    this.linkDatasetWindow.close();
                },
                scope: this
            }, {
                xtype: 'button',
                text: 'Done',
                handler: this.linkDatasetHandler,
                scope: this
            }],
            items: [{
                xtype: 'form',
                border: false,
                title: '',
                defaults: {
                    margin: '10 0 0 25'
                },
                items: [{
                    xtype: 'displayfield',
                    fieldLabel: 'Dataset Name',
                    value: datasetLabel,
                    width: 340
                },this.linkDatasetGroup, this.addDatasetGroup, this.datasetCombo]
            }]
        });

        this.linkDatasetWindow.show()
    },

    addDatasetHandler : function(btn){
        var json = {
            name: this.datasetNameField.getValue(),
            type: this.addDatasetRadioGroup.getValue().dataset
        };
        if(this.categoriesCombo.getValue() != '' && this.categoriesCombo.getValue() != null){
             json.category = {label: this.categoriesCombo.getValue()};
        }

        this.addOrLinkDataset(json);
    },

    linkDatasetHandler : function(){
        var json = {};
        if(this.linkDatasetGroup.getValue().link == "new"){
            //new dataset.
            json.type = this.addDatasetGroup.getValue().deftype;
            json.expectationDataset = this.expectationDatasetId;
        } else {
            //existing dataset.
            json.type = 'linkToTarget';
            json.targetDataset = this.datasetCombo.getValue();
            json.expectationDataset = this.expectationDatasetId;
        }

        this.addOrLinkDataset(json);
    },

    addOrLinkDataset : function(json){
        
        Ext4.Ajax.request({
            url     : LABKEY.ActionURL.buildURL('study', 'defineDataset.view'),
            method  : 'POST',
            jsonData : json,
            success : function(response){
                var resp = Ext4.decode(response.responseText);
                if(json.type == 'placeHolder' || json.type == 'linkToTarget'){
                    // If placeHolder or linkToTarget, stay on page, reload grid.
                    if(this.addDatasetWindow){
                        this.addDatasetWindow.close();
                    }
                    if(this.linkDatasetWindow){
                       this.linkDatasetWindow.close();
                    }
                    this.configureGrid();
                } else {
                    // If manual/import navigate to manual/import page.
                    window.location = resp.redirectUrl;
                }
            },
            failure : function(response){
                var resp = Ext4.decode(response.responseText);
                if(resp && resp.exception){
                    Ext4.Msg.alert('Failure', resp.exception);
                } else {
                    Ext4.Msg.alert('Failure', 'An unknown failure has ocurred');
                }
            },
            scope   : this
        });
    }
});