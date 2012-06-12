/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

LABKEY.requiresExt4Sandbox(true);


Ext4.define('LABKEY.ext4.GenericChartPanel', {

    extend : 'Ext.panel.Panel',

    constructor : function(config) {

        Ext4.QuickTips.init();

        Ext4.applyIf(config, {
            frame   : false,
            border  : false,
            layout    : 'border',
            editable  : false
        });

        // delayed task to redraw the chart
        this.updateChartTask = new Ext4.util.DelayedTask(function(){

            if (this.isConfigurationChanged())
            {
                this.viewPanel.getEl().mask('loading data...');
                LABKEY.Query.selectRows(this.getQueryConfig());
            }

        }, this);

        this.reportLoaded = true;
        this.callParent([config]);
    },

    initComponent : function() {

        this.items = [];

        this.centerPanel = Ext4.create('Ext.tab.Panel', {
            border   : false, frame : false,
            region   : 'center',
            header : false,
            headerPosition : 'left',
            items    : [this.getViewPanel(), this.getDataPanel()]
        });

        Ext4.define('MeasureModel',{
            extend: 'Ext.data.Model',
            fields: [
                {name: 'label', mapping: 'shortCaption', type: 'string'},
                {name: 'name', type: 'string'},
                {name: 'hidden', type: 'boolean'},
                {name: 'measure', type: 'boolean'},
                {name: 'type'}
            ]
        });

        this.yMeasureStore = Ext4.create('Ext.data.Store', {
            model: 'MeasureModel',
            proxy: {
                type: 'memory',
                reader: {
                    type: 'json'
                }
            },
            listeners: {
                load: function(store){
                    this.yMeasureStore.filterBy(function(record, id){
                        var type = record.get('type');
                        var hidden = record.get('hidden');
                        return (!hidden && (type == 'int' || type == 'float'))
                    });
                },
                scope: this
            }
        });

        this.xMeasureStore = Ext4.create('Ext.data.Store', {
            model: 'MeasureModel',
            proxy: {
                type: 'memory',
                reader: {
                    type: 'json'
                }
            },
            listeners: {
                load: function(store){
                    this.xMeasureStore.filterBy(function(record, id){
                        var hidden = record.get('hidden');
                        return !hidden
                    });
                },
                scope: this
            }
        });

        this.yMeasureGrid = Ext4.create('Ext.grid.Panel', {
            store: this.yMeasureStore,
            columns: [
                {header: 'Measure', dataIndex: 'label', flex: 1}
            ],
            listeners: {
                select: function(selModel, record, index){
                    this.yMeasureChoice = selModel.getSelection()[0].data;
                    this.yOkBtn.setDisabled(false);
                },
                scope: this
            }
        });

        this.xMeasureGrid = Ext4.create('Ext.grid.Panel', {
            store: this.xMeasureStore,
            columns: [
                {header: 'Measure', dataIndex: 'label', flex: 1}
            ],
            listeners: {
                select: function(selModel, record, index){
                    this.xMeasureChoice = selModel.getSelection()[0].data;
                    this.xOkBtn.setDisabled(false);
                },
                scope: this
            }
        });

        this.yOkBtn = Ext4.create("Ext.Button", {
            text: 'Ok',
            disabled: true,
            handler: function(){
                this.yAxisMeasure = this.yMeasureChoice;
                this.yMeasureWindow.hide();
                this.fireEvent('renderPlot');
            },
            scope: this
        });

        this.xOkBtn = Ext4.create("Ext.Button", {
            text: 'Ok',
            disabled: true,
            handler: function(){
                this.xAxisMeasure = this.xMeasureChoice;
                this.xMeasureWindow.hide();
                this.fireEvent('renderPlot');
            },
            scope: this
        });

        this.yCancelBtn = Ext4.create("Ext.Button", {
            text: 'Cancel',
            handler: function(){
                this.yMeasureWindow.hide();
            },
            scope: this
        });

        this.xCancelBtn = Ext4.create("Ext.Button", {
            text: 'Cancel',
            handler: function(){
                this.xMeasureWindow.hide();
            },
            scope: this
        });

        this.yMeasureWindow = Ext4.create('Ext.window.Window', {
            title: 'Y Axis',
            width: '600',
            layout: 'fit',
            modal: true,
            items: [this.yMeasureGrid],
            bbar: [
                '->',this.yOkBtn, this.yCancelBtn
            ]
        });

        this.xMeasureWindow = Ext4.create('Ext.window.Window', {
            title: 'X Axis',
            width: '600',
            layout: 'fit',
            modal: true,
            items: [this.xMeasureGrid],
            bbar: [
                '->',this.xOkBtn, this.xCancelBtn
            ]
        });

        this.items.push(this.centerPanel);
        this.items.push(this.getNorthPanel());

        this.callParent();

        this.on('tabchange', this.onTabChange, this);
        this.on('renderPlot', this.renderPlot, this);

        if (this.reportId)
            this.loadReport(this.reportId);
        else
            this.on('render', this.ensureQuerySettings, this);

        // customization
        this.on('enableCustomMode',  this.onEnableCustomMode,  this);
        this.on('disableCustomMode', this.onDisableCustomMode, this);

        this.markDirty(false);
        window.onbeforeunload = LABKEY.beforeunload(this.beforeUnload, this);
    },

    getViewPanel : function() {

        if (!this.viewPanel)
        {
            this.viewPanel = Ext4.create('Ext.panel.Panel', {
                flex        : 1,
                layout      : 'fit',
                title       : 'View',
                bodyStyle   : 'overflow-y: auto;',
                cls         : 'iScroll',
                ui          : 'custom',
                listeners   : {
                    activate  : this.viewPanelActivate,
                    scope: this
                }
            });
        }
        this.viewPanel.on('resize', function(cmp) {
            // only re-render after the initial chart rendering
            if (this.chartData)
                this.renderPlot();
        }, this);

        return this.viewPanel;
    },

    getDataPanel : function() {

        if (!this.dataPanel)
        {
            var dataGrid = Ext4.create('Ext.Component', {
                autoScroll  : true,
                cls         : 'iScroll',
                listeners   : {
                    render : {fn : function(cmp){this.renderDataGrid(cmp.getId());}, scope : this}
                }
            });

            this.dataPanel = Ext4.create('Ext.panel.Panel', {
                flex        : 1,
                layout      : 'fit',
                title       : 'Data',
                padding     : '10',
                border      : false,
                frame       : false,
                cls         : 'iScroll',
                items       : dataGrid
            });
        }
        return this.dataPanel;
    },

    isNew : function() {
        return !this.reportId;
    },

    getNorthPanel : function() {

        var formItems = [];

        this.reportName = Ext4.create('Ext.form.field.Text', {
            fieldLabel : 'Report Name',
            allowBlank : false,
            readOnly   : !this.isNew(),
            listeners : {
                change : function() {this.markDirty(true);},
                scope : this
            }
        });

        this.reportDescription = Ext4.create('Ext.form.field.TextArea', {
            fieldLabel : 'Report Description',
            listeners : {
                change : function() {this.markDirty(true);},
                scope : this
            }
        });

        this.reportPermission = Ext4.create('Ext.form.RadioGroup', {
            xtype      : 'radiogroup',
            width      : 300,
            hidden     : !this.allowShare,
            fieldLabel : 'Viewable By',
            items      : [
                {boxLabel : 'All readers',  width : 100, name : 'public', checked : this.allowShare, inputValue : true},
                {boxLabel : 'Only me',   width : 100, name : 'public', inputValue : false}]
        });
        formItems.push(this.reportName, this.reportDescription, this.reportPermission);

        this.formPanel = Ext4.create('Ext.form.Panel', {
            bodyPadding : 20,
            itemId      : 'selectionForm',
            hidden      : this.hideSave,
            flex        : 1,
            items       : formItems,
            border      : false, frame : false,
            fieldDefaults : {
                anchor  : '100%',
                maxWidth : 650,
                labelWidth : 150,
                labelSeparator : ''
            }
        });

        this.saveButton = Ext4.create('Ext.button.Button', {
            text    : 'Save',
            hidden  : this.hideSave,
            handler : function() {
                var form = this.northPanel.getComponent('selectionForm').getForm();

                if (form.isValid()) {
                    var data = this.getCurrentReportConfig();
                    this.saveReport(data);
                }
                else {
                    var msg = 'Please enter all the required information.';

                    if (!this.reportName.getValue()) {
                        msg = 'Report name must be specified.';
                    }
                    Ext4.Msg.show({
                         title: "Error",
                         msg: msg,
                         buttons: Ext4.MessageBox.OK,
                         icon: Ext4.MessageBox.ERROR
                    });
                }
            },
            scope   : this
        });

        this.saveAsButton = Ext4.create('Ext.button.Button', {
            text    : 'Save As',
            hidden  : this.isNew() || this.hideSave,
            handler : function() {
                this.onSaveAs();
            },
            scope   : this
        });

        this.northPanel = Ext4.create('Ext.panel.Panel', {
            bodyPadding : 20,
            hidden      : true,
            preventHeader : true,
            frame       : false,
            region      : 'north',
            items       : this.formPanel,
            buttons  : [{
                text    : 'Cancel',
                handler : function() {
                    this.customize();
                },
                scope   : this
            }, this.saveButton, this.saveAsButton
            ]
        });

        return this.northPanel;
    },

    renderDataGrid : function(renderTo) {
        var urlParams = LABKEY.ActionURL.getParameters(this.baseUrl);
        var filterUrl = urlParams['filterUrl'];

        var userFilters;
        if (this.userFilters)
            userFilters = this.userFilters;
        else
            userFilters = LABKEY.Filter.getFiltersFromUrl(filterUrl, this.dataRegionName);

        var userSort = LABKEY.Filter.getSortFromUrl(filterUrl, this.dataRegionName);

        var wp = new LABKEY.QueryWebPart({
            schemaName  : this.schemaName,
            queryName   : this.queryName,
            frame       : 'none',
            showBorders : false,
            removeableFilters       : userFilters,
            removeableSort          : userSort,
            showSurroundingBorder   : false,
            showDetailsColumn       : false,
            showUpdateColumn        : false,
            showRecordSelectors     : false,
            buttonBar   : {
                position : 'none'
            }
        });

        // save the dataregion
        this.panelDataRegionName = wp.dataRegionName;

        wp.render(renderTo);
    },

    // Returns a configuration based on the baseUrl plus any filters applied on the dataregion panel
    // the configuration can be used to make a selectRows request
    getQueryConfig : function(serialize) {

        var dataRegion = LABKEY.DataRegions[this.panelDataRegionName];
        var config = {
            schemaName  : this.schemaName,
            queryName   : this.queryName
        };

        if(!serialize){
            config.success = this.onSelectRowsSuccess;
            config.failure = function(){this.viewPanel.getEl().unmask();};
            config.scope = this;
        }

        var filters;

        if (dataRegion)
            filters = dataRegion.getUserFilterArray();
        else
            filters = this.userFilters || [];

        if (serialize)
        {
            var newFilters = [];

            for (var i=0; i < filters.length; i++) {
                var f = filters[i];
                newFilters.push({name : f.getColumnName(), value : f.getValue(), type : f.getFilterType().getURLSuffix()});
            }
            filters = newFilters;
        }
        config['filterArray'] = filters;

        return config;
    },

    getChartConfig : function() {

        var config = {};

        if (this.xAxisMeasure)
        {
            config.xAxisMeasure = {
                label   : this.xAxisMeasure.label,
                name    : this.xAxisMeasure.name,
                hidden  : this.xAxisMeasure.hidden,
                measure : this.xAxisMeasure.measure,
                type    : this.xAxisMeasure.type
            }
        }

        if (this.yAxisMeasure)
        {
            config.yAxisMeasure = {
                label   : this.yAxisMeasure.label,
                name    : this.yAxisMeasure.name,
                hidden  : this.yAxisMeasure.hidden,
                measure : this.yAxisMeasure.measure,
                type    : this.yAxisMeasure.type
            }
        }

        return config;
    },

    ensureQuerySettings : function() {

        if (!this.schemaName || !this.queryName)
        {
            var formItems = [];
            var queryStore = this.initializeQueryStore();
            var queryId = Ext.id();

            this.schemaName = 'study';

            formItems.push({
                xtype       : 'combo',
                fieldLabel  : 'Schema',
                name        : 'schema',
                store       : this.initializeSchemaStore(),
                editable    : false,
                value       : this.schemaName,
                queryMode      : 'local',
                displayField   : 'name',
                valueField     : 'name',
                emptyText      : 'None',
                listeners   : {
                    change : {fn : function(cmp, newValue) {
                        this.schemaName = newValue;
                        this.queryName = null;
                        var proxy = queryStore.getProxy();
                        if (proxy)
                            queryStore.load({params : {schemaName : newValue}});

                        var queryCombo = Ext4.getCmp(queryId);
                        if (queryCombo)
                            queryCombo.clearValue();
                    }, scope : this}
                }
            });

            formItems.push({
                xtype       : 'combo',
                id          : queryId,
                fieldLabel  : 'Query',
                name        : 'query',
                store       : queryStore,
                editable    : false,
                allowBlank  : false,
                displayField   : 'name',
                triggerAction  : 'all',
                typeAhead      : true,
                valueField     : 'name',
                emptyText      : 'None',
                listeners   : {
                    change : {fn : function(cmp, newValue) {this.queryName = newValue;}, scope : this}
                }
            });

            var formPanel = Ext4.create('Ext.form.Panel', {
                border  : false,
                frame   : false,
                buttonAlign : 'left',
                fieldDefaults  : {
                    labelWidth : 100,
                    width      : 375,
                    style      : 'padding: 4px 0',
                    labelSeparator : ''
                },
                items   : formItems,
                buttons     : [{
                    text : 'Save',
                    formBind: true,
                    handler : function(btn) {
                        var form = btn.up('form').getForm();
                        if (form.isValid())
                        {
                            dialog.hide();

                            // fire an event or call some method to render data
                        }
                    },
                    scope   : this
                },{
                    text : 'Cancel',
                    handler : function(btn) {window.history.back()}
                }]
            });

            var dialog = Ext4.create('Ext.window.Window', {
                width  : 450,
                height : 200,
                layout : 'fit',
                border : false,
                frame  : false,
                closable : false,
                draggable : false,
                modal  : true,
                title  : 'Select Chart Query',
                bodyPadding : 20,
                items : formPanel,
                scope : this
            });

            dialog.show();
        }
    },

    /**
     * Create the store for the schema
     */
    initializeSchemaStore : function() {

        Ext4.define('LABKEY.data.Schema', {
            extend : 'Ext.data.Model',
            fields : [
                {name : 'name'},
                {name : 'description'}
            ]
        });

        // manually define for now, we could query at some point
        var schemaStore = Ext4.create('Ext.data.Store', {
            model : 'LABKEY.data.Schema',
            data  : [
                {name : 'study'},
                {name : 'assay'},
                {name : 'lists'}
            ]
        });

        return schemaStore;
    },

    /**
     * Create the store for the schema
     */
    initializeQueryStore : function() {

        Ext4.define('LABKEY.data.Queries', {
            extend : 'Ext.data.Model',
            fields : [
                {name : 'name'},
                {name : 'description'},
                {name : 'isUserDefined', type : 'boolean'}
            ]
        });

        var config = {
            model   : 'LABKEY.data.Queries',
            autoLoad: false,
            pageSize: 10000,
            proxy   : {
                type   : 'ajax',
                url : LABKEY.ActionURL.buildURL('query', 'getQueries'),
                extraParams : {
                    schemaName  : 'study'
                },
                reader : {
                    type : 'json',
                    root : 'queries'
                }
            }
        };

        return Ext4.create('Ext.data.Store', config);
    },

    markDirty : function(dirty) {
        this.dirty = dirty;
    },

    isDirty : function() {
        return this.dirty;
    },

    beforeUnload : function() {
        if (this.isDirty()) {
            return 'please save your changes';
        }
    },

    customize : function() {

        this.fireEvent((this.customMode ? 'disableCustomMode' : 'enableCustomMode'), this);
    },

    onEnableCustomMode : function() {

        this.northPanel.show();
        this.customMode = true;
    },

    onDisableCustomMode : function() {

        this.customMode = false;

        if (this.northPanel)
            this.northPanel.hide();
    },

    getCurrentReportConfig : function() {

        var config = {
            name        : this.reportName.getValue(),
            reportId    : this.reportId,
            description : this.reportDescription.getValue(),
            public      : this.reportPermission.getValue().public || false,
            schemaName  : this.schemaName,
            queryName   : this.queryName,
            renderType  : this.renderType,
            jsonData    : {
                queryConfig : this.getQueryConfig(true),
                chartConfig : this.getChartConfig()
            }
        };

        // chart options

        return config;
    },

    saveReport : function(data) {

        Ext4.Ajax.request({
            url     : LABKEY.ActionURL.buildURL('visualization', 'saveGenericReport.api'),
            method  : 'POST',
            jsonData: data,
            success : function(resp){
                // if you want to stay on page, you need to refresh anyway to update attachments
                var msgbox = Ext4.create('Ext.window.Window', {
                    title    : 'Saved',
                    html     : '<div style="margin-left: auto; margin-right: auto;"><span class="labkey-message">Report Saved successfully</span></div>',
                    modal    : false,
                    closable : false,
                    width    : 300,
                    height   : 100
                });
                msgbox.show();
                msgbox.getEl().fadeOut({duration : 2250, callback : function(){
                    msgbox.hide();
                }});

                var o = Ext4.decode(resp.responseText);

                // Modify Title (hack: hardcode the webpart id since this is really not a webpart, just
                // using a webpart frame, will need to start passing in the real id if this ever
                // becomes a true webpart
                var titleEl = Ext4.query('span[class=labkey-wp-title-text]:first', 'webpart_-1');
                if (titleEl && (titleEl.length >= 1))
                {
                    titleEl[0].innerHTML = LABKEY.Utils.encodeHtml(data.name);
                }

                var navTitle = Ext4.query('table[class=labkey-nav-trail] span[class=labkey-nav-page-header]');
                if (navTitle && (navTitle.length >= 1))
                {
                    navTitle[0].innerHTML = LABKEY.Utils.encodeHtml(data.name);
                }

                this.reportId = o.reportId;
                this.loadReport(this.reportId);
                this.customize();
            },
            failure : this.onFailure,
            scope   : this
        });
    },

    onSaveAs : function() {
        var formItems = [];

        formItems.push(Ext4.create('Ext.form.field.Text', {name : 'name', fieldLabel : 'Report Name', allowBlank : false}));
        formItems.push(Ext4.create('Ext.form.field.TextArea', {name : 'description', fieldLabel : 'Report Description'}));

        var permissions = Ext4.create('Ext.form.RadioGroup', {
            xtype      : 'radiogroup',
            width      : 300,
            hidden     : !this.allowShare,
            fieldLabel : 'Viewable By',
            items      : [
                {boxLabel : 'All readers',  width : 100, name : 'public', checked : this.allowShare, inputValue : true},
                {boxLabel : 'Only me',   width : 100, name : 'public', inputValue : false}]
        });
        formItems.push(permissions);

        var saveAsWindow = Ext4.create('Ext.window.Window', {
            width  : 500,
            height : 300,
            layout : 'fit',
            draggable : false,
            modal  : true,
            title  : 'Save As',
            defaults: {
                border: false, frame: false
            },
            bodyPadding : 20,
            items  : [{
                xtype : 'form',
                fieldDefaults : {
                    anchor  : '100%',
                    maxWidth : 450,
                    labelWidth : 150,
                    labelSeparator : ''
                },
                items       : formItems,
                buttonAlign : 'left',
                buttons     : [{
                    text : 'Save',
                    formBind: true,
                    handler : function(btn) {
                        var form = btn.up('form').getForm();

                        if (form.isValid()) {
                            var data = this.getCurrentReportConfig();
                            var values = form.getValues();

                            data.name = values.name;
                            data.description = values.description;
                            data.public = values.public || false;
                            data.reportId = null;

                            this.saveReport(data);
                        }
                        else {
                            Ext4.Msg.show({
                                 title: "Error",
                                 msg: 'Report name must be specified.',
                                 buttons: Ext4.MessageBox.OK,
                                 icon: Ext4.MessageBox.ERROR
                            });
                        }
                        saveAsWindow.close();
                    },
                    scope   : this
                }]
            }],
            scope : this
        });

        saveAsWindow.show();
    },

    loadReport : function(reportId) {

        this.reportLoaded = false;
        Ext4.Ajax.request({
            url     : LABKEY.ActionURL.buildURL('visualization', 'getGenericReport.api'),
            method  : 'GET',
            params  : {reportId : reportId},
            success : function(response){
                this.reportName.setReadOnly(true);
                this.saveAsButton.setVisible(true);
                this.loadSavedConfig(Ext4.decode(response.responseText).reportConfig);
            },
            failure : this.onFailure,
            scope   : this
        });
    },

    loadSavedConfig : function(config) {

        this.allowCustomize = config.editable;

        this.schemaName = config.schemaName;
        this.queryName = config.queryName;
        this.renderType = config.renderType;
        
        if (this.reportName)
            this.reportName.setValue(config.name);

        if (this.reportDescription && config.description != null)
            this.reportDescription.setValue(config.description);

        if (this.reportPermission)
            this.reportPermission.setValue({public : config.public});

        var json = Ext4.decode(config.jsonData)
        if (json.queryConfig.filterArray)
        {
            this.userFilters = [];

            for (var i=0; i < json.queryConfig.filterArray.length; i++)
            {
                var f = json.queryConfig.filterArray[i];
                this.userFilters.push(LABKEY.Filter.create(f.name,  f.value, LABKEY.Filter.getFilterTypeForURLSuffix(f.type)));
            }
        }

        if (json.chartConfig)
        {
            this.yAxisMeasure = json.chartConfig.yAxisMeasure;
            this.xAxisMeasure = json.chartConfig.xAxisMeasure;
        }

        this.markDirty(false);
        this.reportLoaded = true;
        this.updateChartTask.delay(500);
    },

    renderPlot: function() {
        if(!this.yAxisMeasure){

            if (this.autoColumnYName)
            {
                var measure = this.yMeasureStore.findRecord('name', this.autoColumnYName);
                if (measure)
                    this.yAxisMeasure = measure.data;
            }

            if (!this.yAxisMeasure)
            {
                this.showYMeasureWindow();
                return;
            }
        }

        if(!this.xAxisMeasure && this.renderType != 'box_plot'){

            if (this.autoColumnXName)
            {
                measure = this.xMeasureStore.findRecord('name', this.autoColumnXName);
                if (measure)
                    this.xAxisMeasure = measure.data;
            }

            if (!this.xAxisMeasure)
            {
                this.showXMeasureWindow();
                return;
            }
        }
        
        var scales = {}, geom, plotConfig, newChartDiv, labels, yMin, yMax, yPadding;
        var xMeasureName = this.xAxisMeasure ? this.xAxisMeasure.name : this.chartData.queryName;
        var yMeasureName = this.yAxisMeasure.name;

        var xClickHandler = function(scopedThis){
            return function(){
                scopedThis.showXMeasureWindow();
            }
        };

        var yClickHandler = function(scopedThis){
            return function(){
                scopedThis.showYMeasureWindow();
            }
        };

        this.viewPanel.removeAll();
        newChartDiv = Ext4.create('Ext.container.Container', {
            height: 150,
            border: 1,
            autoEl: {tag: 'div'}
        });
        this.viewPanel.add(newChartDiv);

        if(this.renderType == "box_plot") {
            scales.x = {scaleType: 'discrete'};
            yMin = d3.min(this.chartData.rows, function(row){return row[yMeasureName]});
            yMax = d3.max(this.chartData.rows, function(row){return row[yMeasureName]});
            yPadding = ((yMax - yMin) * .1);
            scales.y = {min: yMin - yPadding, max: yMax + yPadding};
            geom = new LABKEY.vis.Geom.Boxplot();
        } else if(this.renderType == "scatter_plot"){
            scales.x = (this.xAxisMeasure.type == 'int' || this.xAxisMeasure.type == 'float') ?
                {scaleType: 'continuous', trans: 'linear'} :
                {scaleType: 'discrete'};
            geom = new LABKEY.vis.Geom.Point({
                opacity: .5
            });
        } else {
            return;
        }

        labels = {
            main: {value: this.chartData.queryName + ' - ' + this.yAxisMeasure.label},
            y: {
                lookClickable: true,
                value: this.yAxisMeasure.label,
                listeners: {
                    click: yClickHandler(this)
                }
            },
            x: {
                lookClickable: true,
                value: xMeasureName,
                listeners: {
                    click: xClickHandler(this)
                }
            }

        };

        plotConfig = this.generatePlotConfig(
                geom,
                newChartDiv.id,
                newChartDiv.getWidth(),
                newChartDiv.getHeight() - 25,
                this.chartData.rows,
                labels,
                scales,
                this.xAxisMeasure ? xMeasureName : function(row){return xMeasureName},
                this.yAxisMeasure.name
        );
        var plot = new LABKEY.vis.Plot(plotConfig);
        plot.render();
    },

    generatePlotConfig: function(geom, renderTo, width, height, data, labels, scales, xAxis, yAxis){
        var aes = {
            y: yAxis,
            x: xAxis
        };
        if(geom.type == "Boxplot"){
            aes.hoverText = function(x, stats){
                return x + ':\nMin: ' + stats.min + '\nMax: ' + stats.max + '\nQ1: ' + stats.Q1 + '\nQ2: ' + stats.Q2 +
                        '\nQ3: ' + stats.Q3;
            };
        } else if(geom.type == "Point"){
            aes.hoverText = function(row){
                return xAxis + ': ' + row[xAxis] + ', ' + yAxis + ': ' + row[yAxis];
            };
        }

        var plotConfig = {
            renderTo: renderTo,
            width: width,
            height: height,
            legendPos: 'none',
            labels: labels,
            layers: [new LABKEY.vis.Layer({geom: geom})],
            scales: scales,
            aes: aes,
            data: data
        };

        return plotConfig;
    },

    viewPanelActivate: function(){

        this.updateChartTask.delay(500);
    },

    createFilterString: function(filterArray){
        var filterParams = [];
        for (var i = 0; i < filterArray.length; i++){
            filterParams.push(filterArray[i].getURLParameterName() + '=' + filterArray[i].getURLParameterValue());
        }

        return filterParams.join('&');
    },

    onSelectRowsSuccess: function(response){
        this.viewPanel.getEl().unmask();
        this.chartData = response;
        this.yMeasureStore.loadRawData(this.chartData.metaData.fields);
        this.xMeasureStore.loadRawData(this.chartData.metaData.fields);

        this.renderPlot();
    },

    showYMeasureWindow: function(){
        this.yMeasureWindow.show();
    },

    showXMeasureWindow: function(){
        this.xMeasureWindow.show();
    },

    /**
     * used to determine if the new chart options are different from the
     * currently rendered options
     */
    isConfigurationChanged : function() {

        var queryCfg = this.getQueryConfig();

        // ugly race condition, haven't loaded a saved report yet
        if (!this.reportLoaded)
            return false;

        if (!this.chartData)
            return true;

        // check if the user filters have changed
        if (this.currentFilterStr == null)
        {
            this.currentFilterStr = this.createFilterString(queryCfg.filterArray);
            return true;
        }
        else
        {
            var filterStr = this.createFilterString(queryCfg.filterArray);

            if (this.currentFilterStr != filterStr)
            {
                this.currentFilterStr = filterStr;
                return true;
            }
        }

        return false;
    }
});
