/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
LABKEY.requiresExt4Sandbox(true);
LABKEY.requiresScript("study/ParticipantFilterPanel.js");

Ext4.tip.QuickTipManager.init();

Ext4.define('LABKEY.ext4.ParticipantReport', {

    extend : 'Ext.panel.Panel',

    REPORT_PAGE_TEMPLATE :
    {
        template : [
            '<table class="report" cellspacing=0>',
            '<tpl for="pages">',
                '{[this.resetGrid(),""]}',
        // PAGE TEMPLATE
                '<tr><td class="break-spacer">&nbsp;<br>&nbsp;</td></tr>',
                '<tr><td colspan="{[this.data.fields.length]}">',
                    '<div style="border:solid 1px #eeeeee; padding:5px; margin:10px;">',
                    '<table>',
                        '<tr><td colspan=2 style="padding:5px; font-weight:bold; font-size:1.3em; text-align:center;">{[ this.getHtml(values.headerValue) ]}</td></tr>',
        // note nested <tpl>, this will make values==datavalue and parent==field
                        '<tpl for="this.data.pageFields"><tpl for="parent.first.asArray[values.index]">',
                            '<tr><td align=right data-qtip="{[parent.qtip]}">{[this.getPageField(parent)]}:&nbsp;</td><td align=left style="{parent.style}">{[this.getPageFieldHtml(values)]}</td></tr>',
                        '</tpl></tpl>',
                    '</table>',
                    '</div>',
                '</td></tr>',
        // GRID TEMPLATE
                '<tr>',
                    '<tpl for="this.data.gridFields">',
                        '<th style="padding-right: 10px;" class="labkey-column-header" data-qtip="{qtip}">{[this.getCaptionHtml(values)]}</th>',
                    '</tpl>',
                '</tr>',
                '<tpl for="rows">',
                    '<tr class="{[this.getGridRowClass()]}">',
        // again nested tpl
                    '<tpl for="this.data.gridFields"><tpl for="parent.asArray[values.index]">',
                        '{[ this.getGridCellHtml(values) ]}',
                    '</tpl></tpl>',
                    '</tr>',
                '</tpl>',
            '</tpl>',
            '</table>'
            //'{[((new Date()).valueOf() - this.start)/1000.0]}'
        ],
        getPageField : function(field)
        {
            // don't wrap on the page field values
            var value = this.getCaptionHtml(field);
            if (value)
                return value.replace(/\s/g, '&nbsp;');
        },
        getPageFieldHtml : function(value)
        {
            // don't wrap on the page field values
            var html = this.getHtml(value);
            if (html)
                return html.replace(/\s/g, '&nbsp;');
        },
        on :
        {
            dataload : function(rpt, data)
            {
            },
            afterdatatransform : function(rpt, data)
            {
                // set headerValue field for each page
                var index = data.pageFields[0].index;
                for (var p=0 ; p<data.pages.length ; p++)
                {
                    var page = data.pages[p];
                    page.headerValue = page.first.asArray[index];
                    // tack on our own default url
                    if (!page.headerValue.url)
                        page.headerValue.url = LABKEY.ActionURL.buildURL('study', 'participant.view', null, {participantId : page.headerValue.value});
                }
                // we don't want the subject id showing in the page break list (since it's already on the header)
                data.pageFields.shift();
            }
        }
    },

    constructor : function(config) {

        Ext4.QuickTips.init();

        Ext4.applyIf(config, {
            layout    : 'border',
            frame     : false,
            border    : false,
            editable  : false,
            printMode : LABKEY.ActionURL.getParameter('_print') != undefined,
            fitted    : false,
            allowCustomize : false,
            subjectNoun    : {singular : 'Participant', plural : 'Participants'}
        });

        this.callParent([config]);
    },

    initComponent : function() {

        this.customMode = false;
        this.initialRender = true;
        this.items = [];

        this.previewPanel = Ext4.create('Ext.panel.Panel', {
            bodyStyle : 'padding: 0 20px;',
            autoScroll  : !this.printMode,
            region : 'center',
            border : false, frame : false,
            html   : '<span style="width: 400px; display: block; margin-left: auto; margin-right: auto">' +
                    ((!this.reportId && !this.allowCustomize) ? 'Unable to initialize report. Please provide a Report Identifier.' : 'Preview Area') +
                    '</span>'
        });

        this.exportForm = Ext4.create('Ext.form.Panel', {
            border : false, frame : false,
            standardSubmit  : true,
            items           : [
                {xtype : 'hidden', name : 'htmlFragment'},
                {xtype : 'hidden', name : 'X-LABKEY-CSRF'}
            ]
        });

        this.lengthReportField = Ext4.create('Ext.form.field.Display', {
            value : '<i>Showing 1000 Results</i>'
        });

        if (!this.printMode) {
            this.centerPanel = Ext4.create('Ext.panel.Panel', {
                border   : false, frame : false,
                layout   : 'fit',
                disabled : this.isNew(),
                region   : 'center',
                dockedItems : [{
                    xtype : 'toolbar',
                    dock  : 'top',
                    cls   : 'report-toolbar',
                    items : [{
                        text    : 'Export',
                        menu    : [{
                            text    : 'To Excel',
                            handler : function(){this.exportToXls();},
                            scope   : this}]
                    },{
                        text    : 'Print',
                        handler : function(b) {
                            this.fitToReport();
                            if (this.filterWindow)
                                this.filterWindow.hide();
                            window.print();
                        },
                        scope   : this
                    },{
                        text    : this.fitted ? 'Collapse' : 'Expand',
                        handler : function(btn) {
                            if (this.fitted) {
                                btn.setText('Expand');
                                this.fitted = false;
                                this.setHeight(600);
                            }
                            else {
                                btn.setText('Collapse');
                                this.fitToReport();
                            }
                        },
                        scope   : this
                    },'->',this.lengthReportField]
                }],
                items    : [this.previewPanel, this.exportForm],
                listeners: {
                    scope: this,
                    delay: 50,
                    afterrender: function(panel){
                        // Issue 14452: with IE8/Win7 the disabled mask doesnt render correctly on initial load
                        // this might have something to do w/ creating the panel prior to adding to the outer panel.
                        // this is a dirty fix, but does render the right mask
                        if(Ext4.isIE && panel.disabled){
                            panel.setDisabled(false);
                            panel.setDisabled(true);
                        }
                    }
                }
            });
        }
        else {
            this.centerPanel = this.previewPanel;
        }

        this.items.push(this.centerPanel);

        if (this.allowCustomize) {
            this.saveButton = Ext4.create('Ext.button.Button', {
                text    : 'Save',
                disabled: this.isNew(),
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
                hidden   : true,
                preventHeader : true,
                frame : false,
                region   : 'north',
                layout   : 'hbox',
                buttons  : [{
                    text    : 'Cancel',
                    handler : function() {
                        this.customize();
                        // revert back to the last config (if any)
                        if (this.storedTemplateConfig)
                            this.loadSavedConfig(this.storedTemplateConfig);
                    },
                    scope   : this
                }, this.saveButton, this.saveAsButton
                ]
            });
            this.items.push(this.northPanel);
        }
        this.initNorthPanel();

        // customization
        this.on('enableCustomMode',  this.onEnableCustomMode,  this);
        this.on('disableCustomMode', this.onDisableCustomMode, this);

        this.filterTask = new Ext4.util.DelayedTask(this.runFilters, this);

        // call generateTemplateConfig() to use this task
        this.generateTask = new Ext4.util.DelayedTask(function(){

            var measures = this.getMeasures();
            var sorts = this.getSorts();

            if (measures.length > 0) {
                this.mask();
                Ext4.Ajax.request({
                    url     : LABKEY.ActionURL.buildURL('visualization', 'getData.api'),
                    method  : 'POST',
                    jsonData: {
                        measures : measures,
                        sorts    : sorts
                    },
                    success : function(response){
                        this.unmask();
                        this.renderData(Ext4.decode(response.responseText));
                    },
                    failure : this.onFailure,
                    scope   : this
                });
            }
            else {
                this.previewPanel.update('');
            }

        }, this);

        if (this.reportId) {
            this.onDisableCustomMode();            
            this.loadReport(this.reportId);
        }
        else if (this.allowCustomize) {
            this.customize();
        }

        this.markDirty(false);
        window.onbeforeunload = LABKEY.beforeunload(this.beforeUnload, this);

        this.callParent([arguments]);
    },

    initNorthPanel : function() {

        if (this.allowCustomize) {
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

            Ext4.define('LABKEY.query.Measures', {
                extend : 'Ext.data.Model',
                fields : [
                    {name : 'id'},
                    {name : 'name'},
                    {name : 'label'},
                    {name : 'description'},
                    {name : 'isUserDefined',    type : 'boolean'},
                    {name : 'isDemographic',    type : 'boolean'},
                    {name : 'queryName'},
                    {name : 'schemaName'},
                    {name : 'type'}
                ],
                proxy : {
                    type : 'memory',
                    reader : {
                        type : 'json',
                        root : 'measures'
                    }
                }
            });

            // models Participant Groups and Cohorts mixed
            Ext4.define('LABKEY.study.GroupCohort', {
                extend : 'Ext.data.Model',
                fields : [
                    {name : 'id'},
                    {name : 'label'},
                    {name : 'description'},
                    {name : 'type'}
                ]
            });

            this.pageFieldStore = Ext4.create('Ext.data.Store', { model : 'LABKEY.query.Measures' });
            this.gridFieldStore = Ext4.create('Ext.data.Store', { model : 'LABKEY.query.Measures' });

            // TODO: figure out infos

/*
            var pageGrid = Ext4.create('Ext.grid.Panel', {
                title   : 'Page Fields',
                store   : this.pageFieldStore,
                //flex    : 1.2,
                columns : [
                    { header : 'Columns', dataIndex : 'name', flex : 1},
                    {
                        xtype : 'actioncolumn',
                        width : 40,
                        align : 'center',
                        sortable : false,
                        items : [{
                            icon    : LABKEY.contextPath + '/' + LABKEY.extJsRoot_40 + '/resources/themes/images/access/qtip/close.gif',
                            tooltip : 'Delete'
                        }],
                        listeners : {
                            click : function(col, grid, idx) {
                                this.pageFieldStore.removeAt(idx);
                                this.generateTemplateConfig();
                            },
                            scope : this
                        },
                        scope : this
                    }
                ],
                //height      : 200,
                viewConfig  : {
                    emptyText : 'Defaults to ' + this.subjectColumn,
                    plugins   : [{
                        ddGroup : 'ColumnSelection',
                        ptype   : 'gridviewdragdrop',
                        dragText: 'Drag and drop to reorder'
                    }],
                    listeners : {
                        drop : function(node, data, model, pos) {
                            this.generateTemplateConfig();
                        },
                        scope: this
                    },
                    scope : this
                }
            });

            var pageFieldsPanel = Ext4.create('Ext.panel.Panel', {
                height      : 200,
                layout      : 'fit',
                border      : false, frame : false,
                flex        : 1.2,
                items       : [pageGrid],
                fbar        : [{
                    type: 'button',
                    text:'Add Field',
                    handler: function() {
                        var callback = function(recs){
                            var rawData = []
                            for (var i=0; i < recs.length; i++) {
                                rawData.push(Ext4.clone(recs[i].data));
                            }
                            this.pageFieldStore.loadRawData({measures : rawData}, true);
                            this.generateTemplateConfig();
                        };
                        this.selectMeasures(callback, this);
                    }, scope: this}]

            });
*/

            var fieldGrid = Ext4.create('Ext.grid.Panel', {
                store   : this.gridFieldStore,
                cls     : 'selectedMeasures',
                border  : false, frame : false,
                columns : [
                    { header : 'Report Measures', dataIndex : 'label', flex : 1},
                    {
                        xtype : 'actioncolumn',
                        width : 40,
                        align : 'center',
                        sortable : false,
                        items : [{
                            icon    : LABKEY.contextPath + '/' + LABKEY.extJsRoot_40 + '/resources/themes/images/access/qtip/close.gif',
                            tooltip : 'Delete'
                        }],
                        listeners : {
                            click : function(col, grid, idx) {
                                this.gridFieldStore.removeAt(idx);
                                if (this.measuresDialog)
                                    this.onMeasuresStoreLoaded(this.measuresDialog.measurePanel);
                                this.generateTemplateConfig();
                            },
                            scope : this
                        },
                        scope : this
                    }
                ],
                viewConfig  : {
                    plugins   : [{
                        ddGroup  : 'ColumnSelection',
                        ptype    : 'gridviewdragdrop',
                        dragText : 'Drag and drop to reorder',
                        copy : true
                    }],
                    listeners : {
                        drop : this.generateTemplateConfig,
                        scope: this
                    },
                    scope : this
                }
            });

            this.measuresHandler = function(doShow) {
                if (doShow !== false)
                    doShow = true;
                var fn;
                if (doShow) {
                    fn = function(recs){
                        var rawData = [];
                        for (var i=0; i < recs.length; i++) {
                            rawData.push(Ext4.clone(recs[i].data));
                        }
                        this.enableUI(true);    // enable the UI if it is currently disabled
                        this.gridFieldStore.loadRawData({measures : rawData}, false);
                        this.generateTemplateConfig();
                    };
                }
                this.selectMeasures(fn, doShow, this);
            };

            this.designerPanel = Ext4.create('Ext.panel.Panel', {
                height      : 200,
                layout      : 'fit',
                border      : false, frame : false,
                flex        : 0.8,
                minButtonWidth : 150,
                buttonAlign : 'left',
                items       : [fieldGrid],
                fbar        : [{
                    xtype: 'button',
                    text:'Choose Measures',
                    handler: this.measuresHandler,
                    scope: this
                }]
            });

/*
            this.designerPanel = Ext4.create('Ext.panel.Panel', {
                //height : 250,
                width  : 500,
                layout: 'fit',
                border : false, frame : false,
                //region : 'center',
                //layout : 'vbox',
                defaults : {
                    style : 'padding-right: 20px'
                },
                //flex   : 4,
                items  : [gridFieldsPanel],
                scope : this
            });
*/

            if (this.isNew()) {
                this.northPanel.add({
                    xtype   : 'panel',
                    flex    : 1,
                    layout  : {
                        type : 'vbox',
                        align: 'center',
                        pack : 'center'
                    },
                    height  : 200,
                    border  : false, frame : false,
                    items   : [
                        {
                            xtype  : 'box',
                            width  : 275,
                            autoEl : {
                                tag : 'div',
                                html: 'To get started, choose some Measures:'
                            }
                        },{
                            xtype   : 'button',
                            text    :'Choose Measures',
                            handler : this.measuresHandler,
                            scope   : this
                        }
                    ]
                });
                this.measuresHandler(false);
            }
            else
                this.northPanel.add(this.formPanel, this.designerPanel);

            this.northPanel.show(); // might be hidden
        }
    },

    loadReport : function(reportId) {

        Ext4.Ajax.request({
            url     : LABKEY.ActionURL.buildURL('study-reports', 'getParticipantReport.api'),
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
        
        if (this.reportName)
            this.reportName.setValue(config.name);

        if (this.reportDescription && config.description != null)
            this.reportDescription.setValue(config.description);

        if (this.reportPermission)
            this.reportPermission.setValue({public : config.public});

        if (this.gridFieldStore) {

            var rawData = [];
            for (var i=0; i < config.measures.length; i++) {
                rawData.push(Ext4.clone(config.measures[i].measure));
            }
            this.gridFieldStore.loadRawData({measures : rawData});

            if (!this.reportGroups && config.groups)
                this.reportGroups = config.groups;

            if (this.initialRender && this.reportGroups)
                this.runFilterSet(this.reportGroups);
            else
                this.generateTemplateConfig();
            this.initialRender = false;
        }

        this.markDirty(false);
    },

    renderData : function(qr) {

        var config = {
            pageFields : [],
            pageBreakInfo : [],
            gridFields : [],
            rowBreakInfo : [],
            reportTemplate : this.REPORT_PAGE_TEMPLATE
        };

        this.lengthReportField.setValue('<i>Showing ' + qr.rows.length + ' Results</i>');

        if (this.pageFieldStore.getCount() > 0) {

            for (var i=0; i < this.pageFieldStore.getCount(); i++) {
                var mappedColName = qr.measureToColumn[this.pageFieldStore.getAt(i).data.name];
                if (mappedColName) {
                    if (i==0)
                        config.pageBreakInfo.push({name : mappedColName, rowspan: false});
                    config.pageFields.push(mappedColName);
                }
            }
        }
        else {
            // try to look for a participant ID measure to use automatically
            if (qr.measureToColumn[this.subjectColumn]) {
                config.pageBreakInfo.push({name : qr.measureToColumn[this.subjectColumn], rowspan: false});
                config.pageFields.push(qr.measureToColumn[this.subjectColumn]);
            }
        }

        // as long as there is page break info then we can render the report
        if (config.pageBreakInfo.length > 0) {

            if (qr.measureToColumn[this.subjectVisitColumn + '/Visit/Label'])
                config.gridFields.push(qr.measureToColumn[this.subjectVisitColumn + '/Visit/Label']);
            
            if (qr.measureToColumn[this.subjectVisitColumn + '/VisitDate'])
                config.gridFields.push(qr.measureToColumn[this.subjectVisitColumn + '/VisitDate']);

            for (i=0; i < this.gridFieldStore.getCount(); i++) {
                var item = this.gridFieldStore.getAt(i);
                var mappedColName = qr.measureToColumn[item.data.name];
                if (mappedColName) {

                    // map any demographic data to the pagefields else push them into the grid fields
                    if (item.data.isDemographic)
                        config.pageFields.push(mappedColName);
                    else
                        config.gridFields.push(mappedColName);
                }
            }

            // finally fix up the column names so that they don't display the long made-up names, the label
            // for the corresponding measure is probably the friendliest
            var columnToMeasure = {};

            for (var m in qr.measureToColumn) {
                if (qr.measureToColumn.hasOwnProperty(m)) {

                    // special case visit label and date
                    if (this.subjectVisitColumn + '/Visit/Label' == m)
                        columnToMeasure[qr.measureToColumn[m]] = 'Visit'
                    else if (this.subjectVisitColumn + '/VisitDate' == m)
                        columnToMeasure[qr.measureToColumn[m]] = 'Visit Date'
                    else
                        columnToMeasure[qr.measureToColumn[m]] = m;
                }
            }

            for (i=0; i < qr.metaData.fields.length; i++) {
                var field = qr.metaData.fields[i];

                var rec = this.gridFieldStore.findRecord('name', columnToMeasure[field.name], 0, false, true, true);
                if (rec)
                {
                    field.shortCaption = rec.data.label;
                    var $h = Ext4.util.Format.htmlEncode;
                    var qtip = "";
                    qtip += $h(rec.data.queryName);  // TODO use label if available
                    qtip += ": ";
                    qtip += $h(rec.data.label || rec.data.name);
                    if (rec.data.description)
                        qtip += "<br>" + $h(rec.data.description);
                    field.qtip = qtip;
                }
                else if (columnToMeasure[field.name])
                {
                    field.shortCaption = columnToMeasure[field.name];
                }
            }

            config.renderTo = this.previewEl || this.previewPanel.getEl().id + '-body';

            Ext4.get(config.renderTo).update('');
            this.templateReport = Ext4.create('LABKEY.TemplateReport', config);
            this.templateReport.on('afterdatatransform', function(th, reportData) {
                this.lengthReportField.setValue('<i>Showing ' + reportData.pages.length + ' Results</i>');
            }, this);

            if (this.printMode) {
                this.templateReport.on('afterrender', this.goToPrint, this);
            }
            else if (this.fitted) {
                this.templateReport.on('afterrender', this.fitToReport, this);
            }

            this.templateReport.loadData(qr);

            if (!this.printMode) {
                this.on('resize', this.showFilter, this);
                this.showFilter(this.reportGroups ? this.reportGroups : []);
            }
        }
    },

    fitToReport : function() {
        this.fitted = true;
        if (this.templateReport) {
            var _h = this.templateReport.getHeight();
            if (_h > 600) {
                this.setHeight(_h + 100 + (this._inCustomMode() ? 400 : 0));
            }
            else {
                this.setHeight(600);
            }
        }
    },

    goToPrint : function() {
        if (this.printMode) {
            this.fitToReport();
            window.print();
        }
    },

    isCustomizable : function() {
        return this.allowCustomize;
    },

    // private
    _inCustomMode : function() {
        return this.customMode;
    },

    customize : function() {

        if (!this.isCustomizable())
            return false;

        this.fireEvent((this._inCustomMode() ? 'disableCustomMode' : 'enableCustomMode'), this);
    },

    onEnableCustomMode : function() {

        // stash away the current config
        if (this.reportId) {
            this.storedTemplateConfig = this.getCurrentReportConfig();
            this.storedTemplateConfig.editable = this.allowCustomize;
        }
        // if the north panel hasn't been fully populated, initialize the dataset store, else
        // just show the panel
        this.northPanel.show();
        this.customMode = true;

        if (this.fitted)
            this.fitToReport();
    },

    onDisableCustomMode : function() {

        this.customMode = false;

        if (this.northPanel)
            this.northPanel.hide();

        if (this.fitted)
            this.fitToReport();
    },

    generateTemplateConfig : function(delay) {
        if (this._inCustomMode())
            this.markDirty(true);
        var d = Ext4.isNumber(delay) ? delay : 500;
        this.generateTask.delay(d);
    },

    showFilter : function(selection) {

        var me = this, panel;

        if (!this.filterPanel) {
            var pConfig = {
                listeners : {
                    selectionchange : function(){
                        this.filterTask.delay(1500);
                        if (this.allowCustomize)
                            this.markDirty(true);
                    },
                    scope : this
                }
            };

            if (!this.isNew())
                pConfig.selection = selection;

            panel = Ext4.create('LABKEY.study.ParticipantFilterPanel', pConfig);
            this.filterPanel = panel.getFilterPanel();

        }

        // This is an example of using it in the toolbar
//        this.centerPanel.getDockedItems('toolbar')[0].insert((this.centerPanel.getDockedItems('toolbar')[0].items.length-1),
//                Ext4.create('Ext.Button', {
//                    text : 'Filter Report',
//                    menu : Ext4.create('Ext.menu.Menu', {
//                        height : 400,
//                        layout : 'fit',
//                        items  : [panel],
//                        scope  : this
//                    }),
//                    scope : this
//                })
//        );

        if (this.filterWindow)
            this.filterWindow.calculatePosition();
        else {
            this.filterWindow = Ext4.create('LABKEY.ext4.ReportFilterWindow', {
                title    : 'Filter Report',
                items    : [panel],
                bodyStyle: 'overflow-y: auto; overflow-x: hidden;',
//                autoShow : true,
                relative : this.centerPanel,
                collapsed: true,
//                expandOnShow : true,
                scope    : this
            });
            if (!this.fitted) {
                  this.centerPanel.getDockedItems('toolbar')[0].insert((this.centerPanel.getDockedItems('toolbar')[0].items.length-2),
                          Ext4.create('Ext.Button', {
                              text : 'Filter Report',
                              handler : function(b) {
                                  this.filterWindow.show();
                                  b.hide();
                              },
                              scope: this
                          })
                  );
            }
            else
                this.filterWindow.show();
        }
    },

    empty : function() {
        this.templateReport.update('<i>No matching results</i>');
        this.lengthReportField.setValue('Showing 0 Results');
    },

    runFilterSet : function(filterSet) {
        var json = [];
        if (filterSet) {
            for (var i=0; i < filterSet.length; i++)
                json.push(filterSet[i]);
        }

        this.resolveSubjects(json, function(subjects){
            this.filteredSubjects = subjects;

            // in this case the query resulted in no matching subjects
            if (this.filteredSubjects.length > 0)
                this.generateTemplateConfig(0);
            else
                this.empty();
        });
    },

    runFilters : function() {
        var all = this.filterPanel.allSelected();
        if (all) {
            if (this.filteredSubjects)
                this.filteredSubjects = undefined;
            this.generateTemplateConfig(0);
        }
        else {
            var json = [];
            var filters = this.filterPanel.getSelection(true);

            if (filters.length == 0)
                return this.empty();

            for (var f=0; f < filters.length; f++) {
                json.push(filters[f].data);
            }

            this.resolveSubjects(json, function(subjects){
                this.filteredSubjects = subjects;

                // in this case the query resulted in no matching subjects
                if (this.filteredSubjects.length > 0)
                    this.generateTemplateConfig(0);
                else
                    this.empty();
            });
        }
    },

    resolveSubjects : function(groups, callback, scope) {
        Ext4.Ajax.request({
            url      : LABKEY.ActionURL.buildURL('participant-group', 'getSubjectsFromGroups.api'),
            method   : 'POST',
            jsonData : Ext4.encode({
                groups : groups
            }),
            success  : function(response){

                var json = Ext4.decode(response.responseText);
                var subjects = json.subjects ? json.subjects : [];
                callback.call(scope || this, subjects);

            },
            failure  : this.onFailure,
            scope    : this
        });
    },

    onFailure : function(resp) {
        var o;
        try {
            o = Ext4.decode(resp.responseText);
        }
        catch (error) {

        }

        var msg = "";
        if(resp.status == 401){
            msg = resp.statusText || "Unauthorized";
        }
        else if(o != undefined && o.exception){
            msg = o.exception;
        }
        else {
            msg = "There was a failure. If the problem persists please contact your administrator.";
        }
        this.unmask();
        Ext4.Msg.alert('Failure', msg);
    },

    // get the grid fields in a form that the visualization getData api can understand
    getMeasures : function() {

        var gridMeasures = [];
        var demMeasures = [];
        for (var i=0; i < this.gridFieldStore.getCount(); i++) {
            var item = this.gridFieldStore.getAt(i).data;

            if (item.isDemographic)
                demMeasures.push({measure : item, time : 'visit'});
            else
                gridMeasures.push({measure : item, time : 'visit'});
        }
        // make sure the non-demographic measures are first
        return gridMeasures.concat(demMeasures);
    },

    getSorts : function() {
        var sorts = [];
        var firstMeasure = this.gridFieldStore.getAt(0).data;

        // if we can help it, the sort should use the first non-demographic measure
        for (var i=0; i < this.gridFieldStore.getCount(); i++) {
            var item = this.gridFieldStore.getAt(i).data;
            if (!item.isDemographic) {
                firstMeasure = item;
                break;
            }
        }

        var sort = {name : this.subjectColumn, queryName : firstMeasure.queryName,  schemaName : firstMeasure.schemaName};
        if (this.filteredSubjects) {
            sort.values = this.filteredSubjects;
        }
        sorts.push(sort);
        sorts.push({name : this.subjectVisitColumn + '/VisitDate', queryName : firstMeasure.queryName,  schemaName : firstMeasure.schemaName});

        return sorts;
    },

    getCurrentReportConfig : function() {

        var config = {
            name        : this.reportName.getValue(),
            reportId    : this.reportId,
            description : this.reportDescription.getValue(),
            public      : this.reportPermission.getValue().public || false,
            schemaName  : 'study',
            measures    : this.getMeasures()
        };

        // persist filters
        if (this.filterPanel) {
            var groups = [];
            var filters = this.filterPanel.getSelection(true);
            for (var f=0; f < filters.length; f++) {
                groups.push(filters[f].data);
            }
            if (groups.length > 0)  {
                config.groups = groups;
            }
        }

        return config;
    },

    saveReport : function(data) {

        Ext4.Ajax.request({
            url     : LABKEY.ActionURL.buildURL('study-reports', 'saveParticipantReport.api'),
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

    exportToXls : function() {

        var markup = this.templateReport.getMarkup();
        if (markup)
        {
            var dirty = this.dirty;
            this.dirty = false;

            this.exportForm.mon('actioncomplete', function(){this.dirty = dirty;}, this, {single : true});
            this.exportForm.getForm().setValues({htmlFragment : markup, 'X-LABKEY-CSRF' : LABKEY.CSRF});
            this.exportForm.submit({
                url     : LABKEY.ActionURL.buildURL('experiment', 'convertHtmlToExcel'),
                failure : this.onFailure,
                scope   : this
            });

            var task = new Ext4.util.DelayedTask(function(){this.dirty = dirty;}, this);
            task.delay(1500);
        }
    },

    // show the select measures dialog
    selectMeasures : function(handler, show, scope) {
        if (!this.measuresDialog) {
            this.measuresDialog = new LABKEY.vis.MeasuresDialog({
                multiSelect : true,
                closeAction :'hide',
                filter : LABKEY.Visualization.Filter.create({schemaName: 'study', queryType: LABKEY.Visualization.Filter.QueryType.BUILT_IN}),
                allColumns    : true,
                canShowHidden : true,
                forceQuery    : true,
                listeners : {
                    'measuresStoreLoaded' : this.onMeasuresStoreLoaded,
                    scope : this
                },
                modal : true,
                scope : this
            });
        }
        this.measuresDialog.addListener('measuresSelected', function(recs) {
            if (handler) handler.call(scope || this, recs);
        }, this, {single : true});
        // competing windows
        if (this.filterWindow) {
            this.filterWindow.hide();
            this.measuresDialog.on('hide', function() { this.filterWindow.show(); }, this, {single: true});
        }

        if (show)
            this.measuresDialog.show();
    },

    isNew : function() {
        return !this.reportId;
    },

    enableUI : function(enable) {

        if (enable && !this.formPanel.rendered) {

            this.northPanel.removeAll();
            this.northPanel.add(this.formPanel, this.designerPanel);
            this.centerPanel.enable();
            this.saveButton.enable();
        }
    },

    onMeasuresStoreLoaded : function(mp) {
        if (this.gridFieldStore && mp.view) {
            var idArray = [];
            var s = mp.view.getStore();
            Ext4.each(this.gridFieldStore.getRange(), function(item) {
                idArray.push(s.findBy(function(rec){
                    return (
                            item.data.schemaName == rec.data.schemaName &&
                            item.data.queryName  == rec.data.queryName &&
                            item.data.name       == rec.data.name
                            );
                }, this));
            }, this);
            if (mp.getSelectionModel().grid) // might not be initialized yet
                mp.getSelectionModel().selectRows(idArray);
        }
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

    mask : function(msg) {
        if (!this.filterWindow || false)//this.filterWindow.isCollapsed())
            this.previewPanel.getEl().mask(msg || 'loading data...');
        else if (this.filterWindow) {
            this.filterWindow.getEl().mask(msg || 'Filtering...');
        }
    },

    unmask : function() {
        if (!this.filterWindow || false)//this.filterWindow.isCollapsed())
            this.previewPanel.getEl().unmask();
        else if (this.filterWindow) {
            this.filterWindow.getEl().unmask();
        }
    }
});
