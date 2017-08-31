/*
 * Copyright (c) 2012-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('LABKEY.ext4.StudyScheduleGrid', {

    extend : 'Ext.panel.Panel',

    constructor : function(config) {

        LABKEY.ext4.DataViewUtil.defineModels();

        Ext4.QuickTips.init();

        Ext4.applyIf(config, {
            layout : 'fit',
            frame  : false,
            border : false,
            maxHeight : 850
        });

        this.callParent([config]);
    },

    initComponent : function() {

        this.items = [this.initCenterPanel()];

        this.callParent();

        window.onbeforeunload = LABKEY.beforeunload(this.beforeUnload, this);
    },

    initCenterPanel : function() {
        this.cohortsStore = Ext4.create('Ext.data.Store', {
            fields: ['label'],
            data: [{label: 'All Cohorts'}, {label: 'Unassigned'}]
        });

        this.cohortsCombo = Ext4.create('Ext.form.field.ComboBox', {
            hidden: true,
            labelWidth: 50,
            width: 250,
            margin: '0 0 0 8',
            allowBlank: false,
            editable: false,
            forceSelection: true,
            value: 'All Cohorts',
            store: this.cohortsStore,
            queryMode: 'local',
            displayField: 'label',
            valueField: 'label',
            listeners:{
                scope: this,
                change: this.filterCohort
            }
        });

        var prevConfig = {
            disabled: true,
            width: 25,
            icon: LABKEY.ActionURL.getContextPath()+'/reports/paging_arrow_prev.gif',
            cls: 'button-prev',
            scope: this,
            handler: this.previousPage
        };

        var displayConfig = {
            width: 12,
            value: '1'
        };

         var nextConfig = {
             disabled: true,
             cls: 'button-next',
             icon: LABKEY.ActionURL.getContextPath()+'/reports/paging_arrow_next.gif',
             scope: this,
             handler: this.nextPage
         };

        var saveButtonConfig = {
            xtype: 'button',
            text: 'Save Changes',
            handler: this.saveChanges,
            scope: this
        };

        var addDatasetButtonConfig = {
            xtype: 'button',
            text: 'Add Dataset',
            handler: this.getCategories,
            scope: this
        };

        this.enablePagingCheckbox = Ext4.create('Ext.form.field.Checkbox', {
            fieldLabel: "Paging",
            checked: true,
            labelWidth: 50,
            value: false,
            scope: this,
            handler: this.enablePaging
        });

        this.topPrevButton  = Ext4.create('Ext.Button', prevConfig);
        this.topPageDisplay = Ext4.create('Ext.form.field.Display', displayConfig);
        this.topNextButton  = Ext4.create('Ext.Button', nextConfig);

        this.bottomPrevButton  = Ext4.create('Ext.Button', prevConfig);
        this.bottomPageDisplay = Ext4.create('Ext.form.field.Display', displayConfig);
        this.bottomNextButton  = Ext4.create('Ext.Button', nextConfig);

        var bbarButtons = [];
        var tbarPanelItems = [];
        if(this.canEdit){
            this.topSaveButton = Ext4.create('Ext.Button', saveButtonConfig); // We create this so we have something to point to when animating the save success dialog.
            this.topAddDatasetButton = Ext4.create('Ext.Button', addDatasetButtonConfig); // Create this or the bottom add dataset button gets shifted upwards by about 2px.
            tbarPanelItems.push(this.topSaveButton);
            tbarPanelItems.push(this.topAddDatasetButton);

            bbarButtons.push(saveButtonConfig);
            bbarButtons.push(addDatasetButtonConfig);
        }

        tbarPanelItems.push(this.cohortsCombo);

        var topPanel = Ext4.create('Ext.container.Container', {
            height: 25,
            border: false,
            flex: 1,
            layout: {
                type: 'hbox',
                align: 'stretch'
            },
            items: [{
                xtype: 'container',
                border: false, frame : false,
                width : 655,
                layout: {
                    type: 'hbox',
                    align: 'stretch'
                },
                defaults : {
                    style : 'margin-left: 4px; margin-right: 4px; margin-bottom: 3px;'
                },
                items: tbarPanelItems
            },{
                xtype: 'toolbar',
                style: {
                    border: 0,
                    padding: 0
                },
                flex: 1,
                items: [this.enablePagingCheckbox, '->', this.topPrevButton, this.topPageDisplay, this.topNextButton]
            }]
        });

        var bottomPanel = Ext4.create('Ext.container.Container', {
            // height: 25,
            border: false,
            flex: 1,
            layout: {
                type: 'hbox',
                align: 'stretch'
            },
            items: [{
                xtype: 'container',
                border: false, frame : false,
                width : 250,
                defaults : {
                    style : 'margin: 3px 4px; padding: 2px 8px;'
                },
                items: bbarButtons
            }, {
                xtype: 'toolbar',
                style: {
                    border: 0,
                    padding: 0
                },
                flex: 1,
                items: ['->', this.bottomPrevButton, this.bottomPageDisplay, this.bottomNextButton]
            }]
        });

        this.centerPanel = Ext4.create('Ext.panel.Panel', {
            layout : 'fit',
            border : false, frame : false,
            region : 'center',
            tbar   : [topPanel],
            bbar   : [bottomPanel],
            listeners : {
                afterrender : this.configureGrid,
                scope : this
            }
        });

        return this.centerPanel;
    },

    configureGrid : function() {

        var handler = function(json) {
            this.schedule = json.schedule;
            this.centerPanel.getEl().unmask();
            var columns;
            if(this.schedule.timepoints.length > 5){
                this.pagedColumns = this.schedule.timepoints.slice(0, 5);
                columns = this.pagedColumns;
            } else {
                this.enablePagingCheckbox.setValue(false);
                columns = this.schedule.timepoints;
            }
            this.initGrid(this.initColumns(columns), this.initScheduleStore(this.schedule, this.initFields()));
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
                itemmousedown: function(view, record, html, idx){
                    if(this.canEdit){
                        if(view.getSelectionModel().getCurrentPosition().column < 4){
                            if(view.getSelectionModel().getCurrentPosition().column == 2){
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

                            var timepoint = columns[view.getSelectionModel().getCurrentPosition().column -4];
                            var timepointId = timepoint.id;

                            // Make a copy of the object so when we set the value of required the dirty bit gets set.
                            var timepointValue = Ext4.apply({}, record.data[timepointId]);
                            if(record.data[timepointId] == undefined || record.data[timepointId] == ""){
                                timepointValue = Ext4.apply({}, columns[view.getSelectionModel().getCurrentPosition().column -4]);
                            }

                            if(!timepointValue.required){
                                timepointValue.required = true;
                            } else {
                                delete timepointValue.required;
                            }
                            record.set(timepointId.toString(), timepointValue);
                        }
                    }
                },
                itemclick : function(view, record, item, index, e, opts) {
                    var cls = e.target.className;
                    if (cls.search(/edit-views-link/i) >= 0)
                        this.onEditClick(view, record);
                }
            }
        });

        this.centerPanel.removeAll();
        this.centerPanel.add(this.gridPanel);

        // This is not approved -- just points out how horrendous layout is
        // 275 (datasets) + 50 (data) + (100 x # of timepoints)
        var calcWidth = 325 + ((columnItems.length - 2) * 100);
        if(calcWidth < 825){
            calcWidth = 825;
        }
        this.setWidth(calcWidth);
        this.centerPanel.setWidth(calcWidth);
        this.cohortsCombo.setVisible(true);
        if(this.schedule.timepoints.length > 5 && this.topPageDisplay.getValue() == 1 && this.topNextButton.disabled == true){
            //enable next buttons if we have more than one page.
            this.topNextButton.setDisabled(false);
            this.bottomNextButton.setDisabled(false);
        }
    },

    initFields : function(){

        var fields = [{
            name    : 'dataset',
            type    : 'string',
            mapping : 'dataset.label'
        },{
            name    : 'id',
            mapping : 'dataset'
        },{
            name : 'authorUserId',
            convert : function(v, record){
                if (record.raw.dataset.author)
                    return record.raw.dataset.author.userId;
                else return 0;
            }
        },{
            name : 'authorDisplayName',
            convert : function(v, record){
                if (record.raw.dataset.author)
                    return record.raw.dataset.author.displayName;
                else return '';
            }
        },
            {name : 'name',         mapping : 'dataset.name'},
            {name : 'datasetId',    mapping : 'dataset.id'},
            {name : 'dataType',     mapping : 'dataset.dataType'},
            {name : 'status',       mapping : 'dataset.status'},
            {name : 'category',     mapping : 'dataset.category'},
            {name : 'description',  mapping : 'dataset.description'},
            {name : 'entityId',     mapping : 'dataset.entityId'},
            {name : 'modified',     mapping : 'dataset.modified'},
            {name : 'refreshDate',  mapping : 'dataset.refreshDate',type : 'date'},
            {name : 'visible',      mapping : 'dataset.visible',    type : 'boolean'}
        ];

        for(var i = 0; i < this.schedule.timepoints.length; i++){
            fields.push({
                name: this.schedule.timepoints[i].id,
                mapping: this.schedule.timepoints[i].id
            });

            //Here we also update the cohortStore so we can filter by cohort.
            if(this.schedule.timepoints[i].cohort && this.cohortsStore.find('label', this.schedule.timepoints[i].cohort.label) == -1){
                this.cohortsStore.add({label: this.schedule.timepoints[i].cohort.label});
            }
        }

        return fields;
    },

    initColumns : function(visibleColumns){

        var statusTpl = '<tpl if="status == \'Draft\'">' +
                '<img data-qtip="Draft" height="16px" width="16px" src="' + LABKEY.ActionURL.getContextPath() + '/reports/icon_draft.png" alt="Draft">' +
                '</tpl>' +
                '<tpl if="status == \'Final\'">' +
                '<img data-qtip="Final" height="16px" width="16px" src="' + LABKEY.ActionURL.getContextPath() + '/reports/icon_final.png" alt="Final">' +
                '</tpl>' +
                '<tpl if="status == \'Locked\'">' +
                '<img data-qtip="Locked" height="16px" width="16px" src="' + LABKEY.ActionURL.getContextPath() + '/reports/icon_locked.png" alt="Locked">' +
                '</tpl>' +
                '<tpl if="status == \'Unlocked\'">' +
                '<img data-qtip="Unlocked" height="16px" width="16px" src="' + LABKEY.ActionURL.getContextPath() + '/reports/icon_unlocked.png" alt="Unlocked">' +
                '</tpl>';

        var urlRenderer = function(val) {
            if (val.type == "Standard")
            {
                return '<a href="' + LABKEY.ActionURL.buildURL('study', 'dataset.view', null, {datasetId: val.id}) + '">' +
                        '<img height="16px" width="16px" src="' + LABKEY.ActionURL.getContextPath() + '/reports/grid.gif" alt="dataset">' +
                        '</a>';
            }
            else if (val.type == "Placeholder")
            {
                return '<img height="16px" style="cursor: pointer" src="' +  LABKEY.ActionURL.getContextPath() + '/reports/link_data.png" alt="link data">';
            }
            return "&nbsp;";
        }

        var visitRenderer = function(val) {
            if (val.required){
                return '<div data-qtip="required" class="checked"></div>';
            }
            return '<div data-qtip="not required" class="unchecked"></div>';
        }

        var columnItems = [];

        if (this.canEdit) {
            columnItems.push({
                text     : '&nbsp;',
                width    : 40,
                sortable : false,
                renderer : function(view, meta, rec, idx, colIdx, store) {
                    if (rec.data.entityId)
                        return '<span height="16px" class="edit-views-link fa fa-pencil"></span>';
                    return '';
                },
                scope    : this
            });
        }

        columnItems.push({
            xtype     : 'templatecolumn',
            text      : 'Datasets',
            dataIndex : 'dataset',
            width     : 275,
            tpl       : '<div data-qtip="{dataset}">{dataset}</div>'
        },{
            text      : 'Data',
            dataIndex : 'id',
            width     : 50,
            tdCls     : 'type-column',
            style     : 'text-align: center',
            renderer  : urlRenderer
        },{
            xtype     : 'templatecolumn',
            text      : 'Status',
            width     : 75,
            sortable  : true,
            tdCls     : 'type-column',
            dataIndex : 'status',
            tpl       : statusTpl
        });

        var header;

        for(var i = 0; i < visibleColumns.length; i++){

            header = '';
            
            if(this.timepointType == "DATE"){
                if(visibleColumns[i].label != null){

                    header = '<span data-qtip="' + visibleColumns[i].label + '<br>Start Day: ' + visibleColumns[i].sequenceMin + ' <br>End Day: ' + visibleColumns[i].sequenceMax;
                    if(visibleColumns[i].cohort){
                        header = header + '<br> Cohort: ' + visibleColumns[i].cohort.label;
                    }
                    header = header + '">' + visibleColumns[i].label +'</span>';
                } else {
                    header = visibleColumns[i].sequenceMin;
                }
            } else {
                if(visibleColumns[i].label != null){
                    if(visibleColumns[i].sequenceMin == visibleColumns[i].sequenceMax){
                        header = '<span data-qtip="' + visibleColumns[i].label + '<br>Sequence: ' + visibleColumns[i].sequenceMin;
                    } else {
                        header = '<span data-qtip="' + visibleColumns[i].label + '<br>Sequence: ' + visibleColumns[i].sequenceMin + ' - ' + visibleColumns[i].sequenceMax;
                    }
                    if(visibleColumns[i].cohort){
                        header = header + '<br> Cohort: ' + visibleColumns[i].cohort.label;
                    }
                    header = header + '">' + visibleColumns[i].sequenceMin + '<br>' + visibleColumns[i].label +'</span>';
                } else {
                    header = '<span>' + visibleColumns[i].sequenceMin;
                }
            }

            var newCol = {
                text: header,
                dataIndex: visibleColumns[i].id,
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
                    root: 'data',
                    idProperty: 'datasetId'
                },
                writer: {
                    type : 'json',
                    root : 'schedule',
                    allowSingle : false
                }

            },
            listeners: {
                scope: this,
                beforesync: function(){
                    // Mask grid.
                    this.centerPanel.setLoading("Saving...");
                },
                write: function(){
                    //Get rid of mask, indicate saved.
                    this.centerPanel.setLoading(false);
                    Ext4.MessageBox.show({
                        title: 'Success',
                        msg: 'Changes saved sucessfully.',
                        width: 200,
                        closable: false,
                        animateTarget: this.topSaveButton
                    });
                    new Ext4.util.DelayedTask(function(){
                        var returnUrl = LABKEY.ActionURL.getParameter("returnUrl");
                        if (returnUrl)
                        {
                            window.location.href = returnUrl;
                        }
                        else
                        {
                            Ext4.MessageBox.hide();
                        }
                    }).delay(1500);
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
        if (!this.needsSaving() && LABKEY.ActionURL.getParameter("returnUrl"))
        {
            window.location.href = LABKEY.ActionURL.getParameter("returnUrl");
        }
        this.gridPanel.getStore().sync();
    },

    filterCohort : function(combo, newValue){
        this.filteredColumns = [];
        var columns, i;

        if(newValue == 'All Cohorts') {
            this.filterColumns = false;
            columns = this.schedule.timepoints;
        }
        else if(newValue =="Unassigned") {
            this.filterColumns = true;
            //filter timepoints, call initGrid with filtered list.
            for(i=0; i < this.schedule.timepoints.length; i++){
                if(!this.schedule.timepoints[i].cohort){
                    this.filteredColumns.push(this.schedule.timepoints[i]);
                }
            }
            columns = this.filteredColumns;
        }
        else {
            this.filterColumns = true;
            //filter timepoints, call initGrid with filtered list.
            for(i=0; i < this.schedule.timepoints.length; i++){
                if(this.schedule.timepoints[i].cohort && this.schedule.timepoints[i].cohort.label == newValue){
                    this.filteredColumns.push(this.schedule.timepoints[i]);
                }
            }
            columns = this.filteredColumns;
        }

        if(this.enablePagingCheckbox.getValue()){
            //if paging then call changePage(0) and reset value of page counter
            this.topPageDisplay.setValue(1);
            this.changePage(0);
        }
        else {
            //else initGrid with all columns
            this.initGrid(this.initColumns(columns), this.scheduleStore);
        }
    },

    enablePaging : function(checkbox, checked){
        if(checked){
            this.topPrevButton.setVisible(true);
            this.bottomPrevButton.setVisible(true);
            this.topNextButton.setVisible(true);
            this.bottomNextButton.setVisible(true);
            this.topPageDisplay.setVisible(true);
            this.bottomPageDisplay.setVisible(true);
            //Change the column model to use a paged set of columns.

            this.pagedColumns = []; // Current set of columns in the 'page'.

            var value = this.topPageDisplay.getValue();
            if(value > 1){
                this.topPrevButton.setDisabled(false);
                this.bottomPrevButton.setDisabled(false);
            }
            this.topNextButton.setDisabled(false);
            this.bottomNextButton.setDisabled(false);
            this.topPageDisplay.setDisabled(false);
            this.bottomPageDisplay.setDisabled(false);
            this.changePage(0);
        } else {
            if(this.filterColumns){
                // use the filteredColumns set of columns.
                this.initGrid(this.initColumns(this.filteredColumns), this.scheduleStore);

            } else {
                // use the unfiltered set of columns.
                this.initGrid(this.initColumns(this.schedule.timepoints), this.scheduleStore);
            }
            this.topPrevButton.setDisabled(true);
            this.bottomPrevButton.setDisabled(true);

            this.topNextButton.setDisabled(true);
            this.bottomNextButton.setDisabled(true);

            this.topPageDisplay.setDisabled(true);
            this.bottomPageDisplay.setDisabled(true);

            this.topPrevButton.setVisible(false);
            this.bottomPrevButton.setVisible(false);

            this.topNextButton.setVisible(false);
            this.bottomNextButton.setVisible(false);

            this.topPageDisplay.setVisible(false);
            this.bottomPageDisplay.setVisible(false);
            
            this.topPageDisplay.setValue(1);
            this.bottomPageDisplay.setValue(1);
        }
    },

    nextPage : function() {
        var val = this.topPageDisplay.getValue();
        val++;
        this.topPageDisplay.setValue(val);
        this.bottomPageDisplay.setValue(val);
        if(val > 1 && this.topPrevButton.disabled){
            this.topPrevButton.setDisabled(false);
            this.bottomPrevButton.setDisabled(false);
        }
        this.changePage(--val);
    },

    previousPage : function() {
        var val = this.topPageDisplay.getValue();
        val--;
        this.topPageDisplay.setValue(val);
        this.bottomPageDisplay.setValue(val);
        if(val == 1){
            this.topPrevButton.setDisabled(true);
            this.bottomPrevButton.setDisabled(true);
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
            this.topNextButton.setDisabled(true);
            this.bottomNextButton.setDisabled(true);
        } else {
            if(this.topNextButton.disabled){
                this.topNextButton.setDisabled(false);
                this.bottomNextButton.setDisabled(false);
            }
        }

        this.pagedColumns = [];

        for(var i = firstColumn; i <= lastColumn; i++){
            this.pagedColumns.push(columns[i]);
        }
        this.initGrid(this.initColumns(this.pagedColumns), this.scheduleStore);
    },

    needsSaving : function(){
        if (!this.scheduleStore)
            return false;
        return this.scheduleStore.getUpdatedRecords().length > 0 || this.scheduleStore.getNewRecords().length > 0 || this.scheduleStore.getRemovedRecords.length > 0;
    },

    beforeUnload : function(){
        if (this.needsSaving())
        {
            return "Please save your changes."
        }
    },

    initializeCategoriesStore : function(){

        var config = {
            pageSize: 100,
            model   : 'Dataset.Browser.Category',
            autoLoad: true,
            autoSync: false,
            data: this.categories,
            proxy   : {
                type   : 'memory',
                extraParams : {
                    // These parameters are required for specific webpart filtering
                    pageId : this.pageId,
                    index  : this.index
                },
                reader : {
                    type : 'json',
                    root : 'categories'
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

    getCategories : function(){
         Ext4.Ajax.request({
            url     : LABKEY.ActionURL.buildURL('reports', 'getCategories.api'),
            method  : 'GET',
            success : function(response){
                this.categories = Ext4.decode(response.responseText);
                this.addDatasetDialog();
            },
            failure : function(response){
                var resp = Ext4.decode(response.responseText);
                if(resp && resp.exception){
                    Ext4.Msg.alert('Failure', resp.exception);
                } else {
                    Ext4.Msg.alert('Failure', 'Unable to retrieve categories.');
                }
            },
            scope   : this
        });
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
            typeAheadDelay : 10,
            minChars       : 0,
            autoSelect     : false,
            queryMode      : 'local',
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
            width: 340,
            listeners: {
                scope: this,
                afterrender: function(){
                    this.datasetNameField.focus(true, 125);
                }
            }
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

        var addDatasetButton = Ext4.create('Ext.button.Button', {
            text: 'Next',
            scope: this,
            handler: this.addDatasetHandler
        });

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
                        addDatasetButton.setText("Done");
                    } else {
                        addDatasetButton.setText("Next");
                    }
                }
            }
        });

        datasetPanelItems.push(this.addDatasetRadioGroup);

        var datasetPanel = Ext4.create('Ext.form.Panel', {
            border: false,
            title: '',
            defaults: {
                margin: '10 0 0 25'
            },
            items: datasetPanelItems
        });

        var bbarItems = [{xtype: 'tbfill'}];

        var cancelDatasetButton = Ext4.create('Ext.button.Button', {
            xtype: 'button',
            text: 'Cancel',
            scope: this,
            handler: function(btn){
                this.addDatasetWindow.close();
            }
        });

        bbarItems.push(cancelDatasetButton, addDatasetButton);

        this.addDatasetWindow = Ext4.create('Ext.window.Window', {
            title   : 'New Dataset',
            height  : 225,
            width   : 400,
            modal   : true,
            layout  : 'fit',
            bodyStyle : 'border: none',
            bodyBorder : false,
            buttons : bbarItems,
            items   : [datasetPanel],
            autoShow: true
        });
    },

    linkDatasetDialog : function(datasetId, datasetLabel){

        var datasets = [], data;

        for(var i = 0; i < this.schedule.data.length; i++){
            data = this.schedule.data[i].dataset;
            if(data.type != 'Placeholder'){
                datasets.push({
                    label : data.label,
                    id    : data.id
                });
            }
        }

        var datasetStore = Ext4.create('Ext.data.Store', {
            fields: ['label', 'id'],
            data: datasets
        });

        this.datasetCombo = Ext4.create('Ext.form.field.ComboBox', {
            disabled: true,
            width: 220,
            allowBlank: false,
            cls : 'existing-dataset-combo',             // test marker
            editable: false,
            forceSelection: true,
            value: 'asdf',
            store: datasetStore,
            queryMode: 'local',
            displayField: 'label',
            valueField: 'id',
            margin: '10 0 0 85',
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

        var existingRadio = {
            boxLabel: 'Link to existing dataset',
            name: 'deftype',
            inputValue: 'linkToTarget'
        };

        var linkDoneButton = Ext4.create('Ext.Button', {
            text: 'Next',
            handler: this.linkDatasetHandler,
            scope: this
        });

        this.linkDatasetGroup = Ext4.create('Ext.form.RadioGroup', {
            columns: 1,
            vertical: true,
            margin: '10 0 0 45',
            items: [importRadio, manualRadio, existingRadio],
            listeners: {
                scope: this,
                change: function(rgroup, newValue){
                    if(newValue.deftype == 'linkToTarget'){
                        linkDoneButton.setText('Done');
                        this.datasetCombo.setDisabled(false);
                    } else {
                        linkDoneButton.setText('Next');
                        this.datasetCombo.setDisabled(true);
                    }
                }
            }
        });

        this.linkDatasetWindow = Ext4.create('Ext.window.Window', {
            title: 'Define Dataset',
            height: 225,
            width: 400,
            layout: 'fit',
            bodyStyle : 'border: none;',
            modal: true,
            scope: this,
            autoShow : true,
            buttons : [{
                xtype: 'button',
                align: 'right',
                text: 'Cancel',
                handler: function(){
                    this.linkDatasetWindow.close();
                },
                scope: this
            }, linkDoneButton],
            items: [{
                xtype: 'form',
                border: false,
                title: '',
                defaults: {
                    margin: '10 0 0 25'
                },
                items: [{
                    xtype: 'displayfield',
                    value: "Define " + Ext4.htmlEncode(datasetLabel),
                    width: 340
                },this.linkDatasetGroup, this.datasetCombo]
            }]
        });
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
        json.type = this.linkDatasetGroup.getValue().deftype;
        json.expectationDataset = this.expectationDatasetId;

        if(json.type == 'linkToTarget'){
            json.targetDataset = this.datasetCombo.getValue();
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
                    Ext4.Msg.alert('Failure', 'An unknown failure has occurred');
                }
            },
            scope   : this
        });
    },

    onEditClick : function(view, record) {

        var formItems = [];

        /* Record 'entityId' is required*/
        var editable = true;
        if (record.data.entityId == undefined || record.data.entityId == "")
        {
            console.warn('Entity ID is required');
            editable = false;
        }

        // hidden items
        formItems.push({
            xtype : 'hidden',
            style : 'display:none;',
            name  : 'id',
            value : record.data.entityId
        },{
            xtype : 'hidden',
            style : 'display:none;',
            name  : 'dataType',
            value : record.data.dataType
        });

        var viewForm = Ext4.create('LABKEY.study.DataViewPropertiesPanel', {
            record          : record,
            extraItems      : formItems,
            visibleFields   : {
                viewName: true,
                author  : true,
                status  : true,
                datacutdate : true,
                category    : true,
                description : true,
                visible     : true,
                created     : true,
                modified    : true
            },
            buttons     : [{
                text : 'Save',
                formBind: true,
                handler : function(btn) {
                    var form = btn.up('form').getForm();
                    if (form.isValid())
                    {
                        if (!form.getValues().category) {
                            // In order to clear the category
                            form.setValues({category: 0});
                        }
                        Ext4.Ajax.request({
                            url     : LABKEY.ActionURL.buildURL('reports', 'editView.api'),
                            method  : 'POST',
                            params  : form.getValues(),
                            success : function(){
                                this.configureGrid();
                                editWindow.close();
                            },
                            failure : function(response){
                                Ext4.Msg.alert('Failure', Ext4.decode(response.responseText).exception);
                            },
                            scope : this
                        });
                    }
                },
                scope   : this
            }]
        });

        var editWindow = Ext4.create('Ext.window.Window', {
            width  : 450,
            height : 425,
            layout : 'fit',
            cls    : 'data-window',
            draggable : false,
            modal  : true,
            title  : record.data.name,
            defaults: {
                border: false, frame: false
            },
            bodyPadding : 10,
            items : viewForm,
            autoShow : true,
            scope : this
        });
    }
});
