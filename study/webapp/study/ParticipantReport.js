/*
 * Copyright (c) 2012-2016 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('LABKEY.ext4.ParticipantReport', {

    extend : 'Ext.panel.Panel',

    minWidth: 625,

    frame     : false,

    border    : false,

    editable  : false,

    fitted    : false,

    transposed : false,

    allowOverflow: true,

    layout: 'border',

    allowCustomize : false,

    allowFilter: true,

    shrinkWrap: 0, // both width & height depend on content

    REPORT_PAGE_TEMPLATE: {
        headerTemplate : [
            '<table class="report" cellspacing=0>',
            '<tpl for="pages">',
                '{[this.resetGrid(),""]}',
                '{[this.setPageIndex(xindex),""]}',
        // PAGE TEMPLATE
                '<tr><td class="break-spacer">&nbsp;<br>&nbsp;</td></tr>',
                '<tr><td colspan="{[this.data.fields.length]}">',
                    '<div style="padding-left:5px; padding-bottom:15px;">',
                    '<table width="100%">',
                        '<tr><td colspan=2 class="lk-report-subjectid">{[ this.getHtml(values.headerValue) ]}</td></tr>',
                        '<tr style="border-top: solid 1px #DDDDDD;"><td class="lk-report-column-header" align=left style="padding: 10px 0px 10px 0px;">Cohort:&nbsp;</td><td class="lk-report-cell" align=left style="{parent.style}" width="100%;">{[this.getCohortHtml(values.headerValue)]}</td></tr>',
                        '<tr style="border-top: solid 1px #DDDDDD;vertical-align:text-top;"><td class="lk-report-column-header" align=left style="padding: 10px 0px 10px 0px;">Groups:&nbsp;</td><td class="lk-report-cell" align=left style="{parent.style}" width="100%;">{[this.getGroupsHtml(values.headerValue)]}</td></tr>',
                        '<tr style="padding-bottom: 10px;"><td colspan=2>&nbsp;</td></tr>',
                        '<tr style="border-top: solid 1px #DDDDDD;padding-bottom: 10px;"><td colspan=2>&nbsp;</td></tr>',
        // note nested <tpl>, this will make values==datavalue and parent==field
                        '<tpl for="this.data.pageFields">' +
                            '<tpl for="this.data.pages[this.data.pageIndex].pageFieldData.asArray[values.index]">',
                            '   <tr><td align=left class="lk-report-column-header" data-qtip="{[parent.qtip]}">{[this.getPageField(parent)]}:&nbsp;</td><td class="lk-report-cell" "align=left style="{parent.style}">{[this.getPageFieldHtml(values)]}</td></tr>',
                            '</tpl>',
                        '</tpl>',
                    '</table>',
                    '</div>',
                '</td></tr>'
        ],
        // GRID TEMPLATES
        originalGrid : [
                '<tr>',
                    '<tpl for="this.data.gridFields">',
                        '<th style="border:solid 1px #DDDDDD;padding: 4px;vertical-align:top;" class="lk-report-column-header" data-qtip="{qtip}">{[this.getCaptionHtml(values)]}</th>',
                    '</tpl>',
                '</tr>',
                '<tpl for="rows">',
                    '<tr class="{[this.getGridRowClass()]}">',
        // again nested tpl
                        '<tpl for="this.data.gridFields">',
                            '{[ this.getGridCellHtml(parent.asArray[values.index], false) ]}',
                        '</tpl>',
                    '</tr>',
                '</tpl>'
        ],
        transposeGrid : [
                '<tpl for="this.data.gridFields">',
                    // use the first gridField for the header row (likely visit label)
                    '<tpl if="values.rowIndex == 0">',
                        '<tr>',
                            '<th class="lk-report-column-header" style="vertical-align:top">&nbsp;</th>',
                            '<tpl for="this.data.pages[this.data.pageIndex].rows">',
                                '{[ this.getGridCellHtml(values.asArray[parent.index], true) ]}',
                            '</tpl>',
                        '</tr>',
                    '<tpl else>',
                        '<tr class="{[this.getGridRowClass()]}">',
                            '<td style="border: solid 1px #DDDDDD;" data-qtip="{qtip}">{[this.getCaptionHtml(values)]}</td>',
                            '<tpl for="this.data.pages[this.data.pageIndex].rows">',
                                '{[ this.getGridCellHtml(values.asArray[parent.index], false) ]}',
                            '</tpl>',
                        '</tr>',
                    '</tpl>',
                '</tpl>'
        ],
        footerTemplate : [
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
        getCohortHtml : function(header)
        {
            // don't wrap on the page field values
            var rec = this.subjectGroupMap[header.value];
            var html;
            if (rec && rec.cohort){
                html = Ext4.util.Format.htmlEncode(rec.cohort);
                return html.replace(/\s/g, '&nbsp;');
            }
        },
        getGroupsHtml : function(header)
        {
            var rec = this.subjectGroupMap[header.value];
            var html = [];
            if (rec && rec.groups){
                for (var i=0;i<rec.groups.length;i++)
                    html.push(Ext4.util.Format.htmlEncode(rec.groups[i]));

                if (html.length){
                    html = html.join(',<br>');
                    return html.replace(/\s/g, '&nbsp;')
                }
            }
        },
        on : {
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
                    // Issue 18619: mask ptid in demo mode
                    if (page.headerValue.displayValue)
                        page.headerValue.displayValue = LABKEY.id(page.headerValue.displayValue);

                    // store the index of the page, for transpose template
                    page.index = p;
                }

                // set rowIndex for each gridField
                var idx = 0;
                // set rowIndex for each gridField and the default style
                Ext4.each(data.gridFields, function(field){
                    field.rowIndex = idx++;
                    field.style = "border: solid 1px #DDDDDD";
                    field.className = "lk-report-cell";
                });

                // we don't want the subject id showing in the page break list (since it's already on the header)
                data.pageFields.shift();

                // for the remaining pageFields, find the row in the page that has data to be pulled up to the page level
                if (data.pageFields.length > 0)
                {
                    idx = data.pageFields[0].index;
                    for (var i=0; i < data.pages.length; i++)
                    {
                        page = data.pages[i];
                        page['pageFieldData'] = page.first;
                        for (var r=0; r < page.rows.length; r++)
                        {
                            var row = page.rows[r];
                            if (row.asArray[idx].value)
                            {
                                page['pageFieldData'] = row;
                                break;
                            }
                        }
                    }
                }
            }
        }
    },

    constructor : function(config) {

        Ext4.QuickTips.init();

        Ext4.applyIf(config, {
            printMode : LABKEY.ActionURL.getParameter('_print') != undefined,
            subjectNoun: {singular : 'Participant', plural : 'Participants', columnName: 'Participant'}
        });

        this.callParent([config]);
    },

    initComponent : function() {

        this.customMode = false;
        this.items = [];
        this.pendingRequests = 0;
        this.maxRowCount = 10000;

        this.previewPanel = Ext4.create('Ext.panel.Panel', {
            bodyStyle : 'padding: 0 20px;',
            autoScroll  : !this.printMode,
            height : 400,
            region : 'center',
            border : false, frame : false,
            html   : '<span style="width: 400px; display: block; margin-left: auto; margin-right: auto; text-align: center;">' +
                    ((!this.reportId && !this.allowCustomize) ? 'Unable to initialize report. Please provide a Report Identifier.' : 'Preview Area') +
                    '</span>'
        });

        this.exportForm = Ext4.create('Ext.form.Panel', {
            border : false, frame : false,
            standardSubmit  : true,
            items           : [
                {xtype : 'hidden', name : 'htmlFragment'},
                {xtype : 'hidden', name : 'X-LABKEY-CSRF'},
                {xtype : 'fileuploadfield', name : 'file', hidden : true}
            ]
        });

        if (this.allowCustomize) {
            this.items.push(this.initNorthPanel());
        }
        this.items.push(this.initCenterPanel());

        this.configureTasks();
        this.callParent();

        // customization
        this.on('enableCustomMode',  this.onEnableCustomMode,  this);
        this.on('disableCustomMode', this.onDisableCustomMode, this);
        this.on('afterrender', function(p) {
            if (this.reportId) {
                this.onDisableCustomMode();
                this.loadReport(this.reportId);
            }
            else if (this.allowCustomize) {
                this.customize();
            }
        }, this, {single: true});

        this.markDirty(false);
        window.onbeforeunload = LABKEY.beforeunload(this.beforeUnload, this);
    },

    configureTasks : function() {
        if (!this.filterTask)
            this.filterTask = new Ext4.util.DelayedTask(this.runFilters, this);

        if (!this.generateTask) {
            // call generateTemplateConfig() to use this task
            this.generateTask = new Ext4.util.DelayedTask(function() {

                var measures = this.getMeasures();
                var sorts = this.getSorts();

                if (measures.length > 0) {
                    this.queryLimited = this._inCustomMode();
                    var limit = this.queryLimited ? 50 : this.maxRowCount;

                    this.mask();
                    this.pendingRequests++;
                    Ext4.Ajax.request({
                        url     : LABKEY.ActionURL.buildURL('visualization', 'getData.api'),
                        method  : 'POST',
                        jsonData: {
                            measures : measures,
                            sorts    : sorts,
                            limit    : limit
                        },
                        success : function(response) {
                            this.response = Ext4.decode(response.responseText);

                            if (this.response.rowCount == this.maxRowCount) {

                                var el = this.previewEl || this.previewPanel.getEl().id + '-body';
                                Ext4.get(el).update('<span class="labkey-error" style="width: 600px; height: 400px; display: block; margin-left: 250px;"><i>' +
                                        'The number of results returned has exceeded an internal limit. Please select fewer measures and/or select fewer participants ' +
                                        'to reduce the number of rows returned.' +
                                        '</i></span>');
                                if (!this._inCustomMode()) {
                                    this.updateStatus('');
                                }

                                this.unmask();
                                this.pendingRequests--;
                                if (this.allowFilter) {
                                    this.showFilter(this.filterSet ? this.filterSet : []);
                                }
                            }
                            else
                                onLoad.call(this);
                        },
                        failure : this.onFailure,
                        scope   : this
                    });

                    this.subjectGroupMap = {};
                    this.pendingRequests++;
                    Ext4.Ajax.request({
                        url     : LABKEY.ActionURL.buildURL('participant-group', 'browseParticipantGroups.api', null, {type: ['participantGroup', 'cohort'], includeParticipantIds: true}),
                        method  : 'GET',
                        success : function(response){
                            var response = Ext4.decode(response.responseText);
                            for (var i=0;i<response.groups.length;i++){
                                var row = response.groups[i];
                                if(row.participantIds){
                                    for (var j=0;j<row.participantIds.length;j++){
                                        var id = row.participantIds[j];

                                        if(!this.subjectGroupMap[id]){
                                            this.subjectGroupMap[id] = {
                                                cohort: null,
                                                groups: []
                                            }
                                        }
                                        if (row.type == 'cohort')
                                            this.subjectGroupMap[id].cohort = row.label;
                                        else if (row.type == 'participantGroup')
                                            this.subjectGroupMap[id].groups.push(row.label);
                                    }
                                }
                            }
                            onLoad.call(this);
                        },
                        failure : this.onFailure,
                        scope   : this
                    });

                    var onLoad = function() {
                        this.pendingRequests--;
                        if (this.pendingRequests == 0) {
                            this.unmask();
                            this.renderData();
                        }
                    };
                }
                else {
                    this.previewPanel.update('');
                }

            }, this);
        }
    },

    initNorthPanel : function() {

        if (!this.northPanel) {

            // Define custom models
            if (!Ext4.ModelManager.isRegistered('LABKEY.query.Measures')) {
                Ext4.define('LABKEY.query.Measures', {
                    extend : 'Ext.data.Model',
                    fields : [
                        {name : 'id'},
                        {name : 'name'},
                        {name : 'label'},
                        {name : 'description'},
                        {name : 'isUserDefined', type : 'boolean'},
                        {name : 'isDemographic', type : 'boolean'},
                        {name : 'queryName'},
                        {name : 'schemaName'},
                        {name : 'type'},
                        {name : 'alias'}
                    ],
                    proxy : {
                        type : 'memory',
                        reader : {
                            type : 'json',
                            root : 'measures'
                        }
                    }
                });
            }

            this.pageFieldStore = Ext4.create('Ext.data.Store', { model : 'LABKEY.query.Measures' });
            this.gridFieldStore = Ext4.create('Ext.data.Store', { model : 'LABKEY.query.Measures' });

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
                handler : function() { this.onSaveAs(); },
                scope   : this
            });

            this.northPanel = Ext4.create('Ext.panel.Panel', {
                bodyPadding : 20,
                hidden   : true,
                preventHeader : true,
                frame : false,
                cls   : 'report-config-panel',
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

            this.formPanel = Ext4.create('Ext.form.Panel', {
                bodyPadding : 20,
                itemId      : 'selectionForm',
                hidden      : this.hideSave,
                flex        : 1,
                items       : [this.reportName, this.reportDescription, this.reportPermission],
                border      : false, frame : false,
                fieldDefaults : {
                    anchor  : '100%',
                    maxWidth : 650,
                    labelWidth : 150,
                    labelSeparator : ''
                }
            });

            this.designerPanel = Ext4.create('Ext.panel.Panel', {
                height      : 200,
                layout      : 'fit',
                border      : false, frame : false,
                flex        : 0.8,
                minButtonWidth : 150,
                buttonAlign : 'left',
                items       : [{
                    xtype   : 'grid',
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
                                icon    : LABKEY.contextPath + '/' + LABKEY.extJsRoot_42 + '/resources/themes/images/access/qtip/close.gif',
                                tooltip : 'Delete'
                            }],
                            listeners : {
                                click : function(col, grid, idx) {
                                    this.gridFieldStore.removeAt(idx);
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
                }],
                fbar        : [{
                    xtype: 'button',
                    text:'Choose Measures',
                    handler: function () {
                        this.onShowMeasures();
                    },
                    scope: this
                }]
            });

            this.onShowMeasures = function(doShow) {
                if (doShow !== false)
                    doShow = true;
                var fn;
                if (doShow) {
                    fn = function(recs) {
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
                            handler : this.onShowMeasures,
                            scope   : this
                        }
                    ]
                });
                this.onShowMeasures(false);
            }
            else {
                this.northPanel.add(this.formPanel, this.designerPanel);
            }
        }

        return this.northPanel;
    },

    initCenterPanel : function() {
        if (!this.centerPanel) {
            if (!this.printMode) {
                this.centerPanel = Ext4.create('Ext.panel.Panel', {
                    itemId   : 'reportcenter',
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
                                scope   : this
                            }]
                        },{
                            text    : 'Transpose',
                            tooltip : 'Tranpose the data grids so that the rows become columns (i.e. visits vs. measures as columns)',
                            handler : function(btn) {
                                this.transposed = !this.transposed; // transposed refers to changing the grid render so that visits are accross the top (as columns)
                                this.renderData();
                            },
                            scope   : this
                        },'->',{
                            xtype: 'displayfield',
                            itemId: 'lengthreport',
                            value : '<i>Showing 0 Results</i>'
                        }]
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
                            if (this.shrinkWrap) {
                                // hack to set the initial northpanel width to match the center panel's
                                this.northPanel.setWidth(panel.getWidth());
                                if (this.customMode)
                                    this.northPanel.show();
                            }
                        }
                    }
                });
            }
            else {
                this.centerPanel = this.previewPanel;
            }
        }

        return this.centerPanel;
    },

    updateStatus : function(msg) {
        var tb = this.centerPanel.getDockedItems()[0];
        if (tb) {
            var status = tb.down('#lengthreport');
            if (status) {
                status.setValue('<i>' + (Ext4.isNumber(msg) ? 'Showing ' + msg + ' Results' : msg) + '</i>');
            }
        }
    },

    loadReport : function(reportId) {

        Ext4.Ajax.request({
            url     : LABKEY.ActionURL.buildURL('reports', 'getReport.api'),
            method  : 'GET',
            params  : {reportId : reportId},
            success : function(response) {
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
            this.reportPermission.setValue({'public' : config['public']});

        if (this.gridFieldStore) {

            var rawData = [];
            for (var i=0; i < config.measures.length; i++) {
                rawData.push(Ext4.clone(config.measures[i].measure));
            }
            this.gridFieldStore.loadRawData({measures : rawData});

            if (config.groups)
                this.filterSet = config.groups;

            if (this.filterSet)
                this.runFilterSet(this.filterSet);
            else
                this.generateTemplateConfig();
        }

        this.markDirty(false);
    },

    renderData : function(qr) {
        // store the getData API query response
        if (qr)
            this.response = qr;

        // build the array for the template based on whether it is to be transposed
        this.REPORT_PAGE_TEMPLATE.template = this.REPORT_PAGE_TEMPLATE.headerTemplate.concat(
                this.transposed ? this.REPORT_PAGE_TEMPLATE.transposeGrid : this.REPORT_PAGE_TEMPLATE.originalGrid,
                this.REPORT_PAGE_TEMPLATE.footerTemplate
            );

        this.REPORT_PAGE_TEMPLATE.subjectGroupMap = this.subjectGroupMap;

        var config = {
            pageFields : [],
            pageBreakInfo : [],
            gridFields : [],
            rowBreakInfo : [],
            subjectGroupMap : this.subjectGroupMap,
            reportTemplate : this.REPORT_PAGE_TEMPLATE,
            transposed : this.transposed      
        };

        if (this.pageFieldStore.getCount() > 0) {

            for (var i=0; i < this.pageFieldStore.getCount(); i++) {
                var mappedColName = this.getColumnFromMeasure(this.pageFieldsStore.getAt(i).data, this.response.measureToColumn);
                if (mappedColName) {
                    if (i==0)
                        config.pageBreakInfo.push({name : mappedColName, rowspan: false});
                    config.pageFields.push(mappedColName);
                }
            }
        }
        else {
            // try to look for a participant ID measure to use automatically
            if (this.response.measureToColumn[this.subjectColumn]) {
                config.pageBreakInfo.push({name : this.response.measureToColumn[this.subjectColumn], rowspan: false});
                config.pageFields.push(this.response.measureToColumn[this.subjectColumn]);
            }
        }

        // as long as there is page break info then we can render the report
        if (config.pageBreakInfo.length > 0) {

            if (this.response.measureToColumn[this.subjectVisitColumn + '/Visit/Label']) {
                config.gridFields.push(this.response.measureToColumn[this.subjectVisitColumn + '/Visit/Label']);
                config.rowBreakInfo.push({name: this.response.measureToColumn[this.subjectVisitColumn + '/Visit/Label'], rowspans : true});
            }

            if (this.response.measureToColumn[this.subjectVisitColumn + '/VisitDate']) {
                config.gridFields.push(this.response.measureToColumn[this.subjectVisitColumn + '/VisitDate']);
                config.rowBreakInfo.push({name: this.response.measureToColumn[this.subjectVisitColumn + '/VisitDate'], rowspans : true});
            }

            for (i=0; i < this.gridFieldStore.getCount(); i++) {

                var item = this.gridFieldStore.getAt(i);
                mappedColName = this.getColumnFromMeasure(item.data, this.response.measureToColumn);
                if (mappedColName) {

                    // map any demographic data to the pagefields else push them into the grid fields
                    if (item.data.isDemographic)
                        config.pageFields.push(mappedColName);
                    else
                        config.gridFields.push(mappedColName);
                }
            }

            // add the tooltip for the grid fields
            for (i=0; i < this.response.metaData.fields.length; i++) {
                var field = this.response.metaData.fields[i];

                var rec = this.gridFieldStore.findRecord('alias', field.fieldKeyPath, 0, false, true, true);
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
            }

            config.renderTo = this.previewEl || this.previewPanel.getEl().id + '-body';

            Ext4.get(config.renderTo).update('');
            this.templateReport = Ext4.create('LABKEY.TemplateReport', config);
            this.templateReport.on('afterdatatransform', function(th, reportData) {
                this.updateStatus(this._inCustomMode() ? 'Showing partial results while in edit mode. Close the edit panel above to see all results.' : reportData.pages.length);
            }, this);

            if (this.printMode) {
                this.templateReport.on('afterrender', this.goToPrint, this);
            }
            else if (this.fitted) {
                this.templateReport.on('afterrender', this.fitToReport, this);
            }

            if (this.filteredSubjects && this.filteredSubjects.length == 0) {
                this.empty();
            }
            else {
                this.templateReport.loadData(this.response);
            }

            if (this.allowFilter && !this.printMode) {
                this.on('resize', this.showFilter, this);
                this.showFilter(this.filterSet ? this.filterSet : []);
            }
        }
    },

    /**
     * Get the column name in the returned query result from the specified measure. Takes into account
     * scenarios where duplicate column names from different tables can be specified. In this case
     * getData will return entries in the map which use the unique column aliases.
     */
    getColumnFromMeasure : function(measure, measureToColMap) {

        // try the more specific alias, else fall back to the less granular name mapping
        var alias = LABKEY.Utils.getMeasureAlias(measure);

        var mappedColName = measureToColMap[alias];
        if (!mappedColName)
            mappedColName = measureToColMap[measure.name];

        return mappedColName;
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
        if (this.isCustomizable())
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
        if (this.centerPanel.rendered)
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

        // when we close the customize dialog, show all results if the current query is limited
        if (this.queryLimited)
            this.generateTask.delay(500);
    },

    generateTemplateConfig : function(delay) {
        if (this._inCustomMode())
            this.markDirty(true);
        var d = Ext4.isNumber(delay) ? delay : 500;
        this.generateTask.delay(d);
    },

    showFilter : function(selection) {

        if (this.allowFilter) {
            if (!this.filterPanel) {
                var pConfig = {
                    filterType: selection.length && selection[0].type == 'participant' ? 'participant' : 'group',
                    subjectNoun: this.subjectNoun,
                    displayMode: 'BOTH',
                    normalWrap: true,
                    listeners : {
                        selectionchange: function (){
                            this.filterTask.delay(1500);
                            if (this.allowCustomize)
                                this.markDirty(true);
                        },
                        beginInitSelection: function () {
                            var panelHeight = this.filterPanel.getHeight();
                            if (panelHeight > this.filterWindow.getHeight())
                                this.filterWindow.setHeight(panelHeight > this.filterWindow.maxHeight ? this.filterWindow.maxHeight : panelHeight);
                        },
                        initSelectionComplete: function () {
                            this.filterWindow.addCls('initSelectionComplete'); // for ParticipantReportTest
                        },
                        scope : this
                    }
                };

                if (!this.isNew()) {
                    pConfig.selection = selection;
                }

                this.filterPanel = Ext4.create('LABKEY.study.ParticipantFilterPanel', pConfig);
            }

            //Issue 15668: In IE7 the floating filter window does not render properly
            if (Ext4.isIE7) {
                var filterDivEl = Ext4.get(this.filterDiv);
                filterDivEl.position('absolute', 11001);
            }

            if (this.filterWindow) {
                this.filterWindow.calculatePosition();
            }
            else {
                var fb = Ext4.create('Ext.Button', {
                    text    : 'Filter Report',
                    hidden  : this.fitted,
                    handler : function(b) {
                        this.filterWindow.show();
                        b.hide();
                    },
                    scope: this
                });

                this.filterWindow = Ext4.create('LABKEY.ext4.ReportFilterWindow', {
                    renderTo: this.filterDiv,
                    items    : [this.filterPanel],
                    shadow: Ext4.isIE7 ? false : "sides",
                    bodyStyle: 'overflow-y: auto; overflow-x: hidden;',
                    relative : this.centerPanel,
                    alignConfig : {
                        position : 'tl-tl',
                        offsets  : [-9, 27]
                    },
                    collapseDirection : 'left',
                    closeAction : 'hide',
                    listeners: {
                        scope: this,
                        close: function() { fb.show(); },
                        resize: function(win, width, height) {
                            var panels = Ext4.ComponentQuery.query('labkey-filterselectpanel', this.filterPanel);
                            Ext4.each(panels, function(panel) {
                                panel.setWidth(width - 10);
                            });
                        }
                    },
                    scope    : this
                });

                this.centerPanel.getDockedItems('toolbar')[0].insert((this.centerPanel.getDockedItems('toolbar')[0].items.length-2), fb);

                if (this.fitted) {
                    this.filterWindow.show();
                    this.filterWindow.collapse();
                }
            }
        }
    },

    empty : function() {
        if (this.templateReport) {
            var el = this.previewEl || this.previewPanel.getEl().id + '-body';
            Ext4.get(el).update('<span style="width: 400px; display: block; margin-left: auto; margin-right: auto"><i>No matching results</i></span>');
        }
        this.updateStatus(0);
    },

    runFilterSet : function(filterSet) {
        if (this.allowFilter) {
            var json = [];
            var participants = [];
            if (filterSet) {
                for (var i=0; i < filterSet.length; i++){
                    if (filterSet[i].type != 'participant')
                        json.push(filterSet[i]);
                    else
                        participants.push(filterSet[i].id)
                }
            }

            this.resolveSubjects(json, participants, function(subjects){
                this.filteredSubjects = subjects;

                // in this case the query resulted in no matching subjects
                if (this.filteredSubjects.length > 0)
                    this.generateTemplateConfig(0);
                else
                {
                    this.empty();
                    if (this.allowFilter) {
                        this.showFilter(this.filterSet ? this.filterSet : []);
                    }
                }
            });
        }
    },

    runFilters : function() {
        if (this.allowFilter) {
            var all = this.filterPanel.getFilterPanel().allSelected();
            if (all) {
                if (this.filteredSubjects)
                    this.filteredSubjects = undefined;
                this.generateTemplateConfig(0);
            }
            else {
                var json = [];
                var participants = [];
                var filters = this.filterPanel.getSelection(true, false);

                if (filters.length == 0)
                    this.empty();

                for (var f=0; f < filters.length; f++) {
                    if(filters[f].get('type') != 'participant')
                        json.push(filters[f].data);
                    else
                        participants.push(filters[f].get('id'))
                }

                this.resolveSubjects(json, participants, function(subjects) {
                    this.filteredSubjects = subjects;

                    // in this case the query resulted in no matching subjects
                    if (this.filteredSubjects.length > 0)
                        this.generateTemplateConfig(0);
                    else
                        this.empty();
                });
            }
        }
    },

    resolveSubjects : function(groups, participants, callback, scope) {
        if(participants && participants.length && !groups)
            callback.call(scope || this, participants);
        else {
            Ext4.Ajax.request({
                url      : LABKEY.ActionURL.buildURL('participant-group', 'getSubjectsFromGroups.api'),
                method   : 'POST',
                jsonData : Ext4.encode({
                    groups : groups
                }),
                success  : function(response){

                    var json = Ext4.decode(response.responseText);
                    var subjects = json.subjects ? json.subjects : [];
                    if(participants && participants.length)
                        subjects = subjects.concat(participants);
                    callback.call(scope || this, subjects);

                },
                failure  : this.onFailure,
                scope    : this
            });
        }
    },

    onFailure : function(resp) {
        var o;
        try {
            o = Ext4.decode(resp.responseText);
        }
        catch (error) {

        }

        var msg = "";
        if (resp.status == 401) {
            msg = resp.statusText || "Unauthorized";
        }
        else if (o != undefined && o.exception) {
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
        if (this.visitBased)
        {
            sorts.push({name : this.subjectVisitColumn + '/Visit/DisplayOrder', queryName : firstMeasure.queryName,  schemaName : firstMeasure.schemaName});
            sorts.push({name : this.subjectVisitColumn + '/sequencenum', queryName : firstMeasure.queryName,  schemaName : firstMeasure.schemaName});
        }
        else
            sorts.push({name : this.subjectVisitColumn + '/VisitDate', queryName : firstMeasure.queryName,  schemaName : firstMeasure.schemaName});

        return sorts;
    },

    getCurrentReportConfig : function() {

        var config = {
            name        : this.reportName.getValue(),
            reportId    : this.reportId,
            description : this.reportDescription.getValue(),
            'public'    : this.reportPermission.getValue()['public'] || false,
            schemaName  : 'study',
            measures    : this.getMeasures()
        };

        // persist filters
        if (this.filterPanel) {
            var groups = [];

            var filters = this.filterPanel.getSelection(true, false);
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
            // flip the dirty but so form POST does not cause page navigation warning when exporting
            var dirty = this.dirty;
            this.dirty = false;

            this.exportForm.mon('actioncomplete', function(){this.dirty = dirty;}, this, {single : true});
            this.exportForm.getForm().setValues({htmlFragment : markup, 'X-LABKEY-CSRF' : LABKEY.CSRF});
            this.exportForm.submit({
                url     : LABKEY.ActionURL.buildURL('experiment', 'convertHtmlToExcel'),
                failure : this.onFailure,
                scope   : this
            });
        }
    },

    // show the select measures dialog
    selectMeasures : function(handler, show, scope) {
        if (!this.measuresDialog) {
            this.measuresDialog = Ext4.create('LABKEY.ext4.MeasuresDialog', {
                multiSelect : true,
                closeAction :'hide',
                filter : LABKEY.Query.Visualization.Filter.create({schemaName: 'study', queryType: LABKEY.Query.Visualization.Filter.QueryType.BUILT_IN}),
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
        else
        {
            // if the measures dialog already exists, call the on load function to re-select the measures (for cancel button or measure deletion) 
            this.onMeasuresStoreLoaded(this.measuresDialog.measurePanel.dataView);
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

            var selModel = mp.getSelectionModel();
            if (selModel) // might not be initialized yet
            {
                if (selModel.getSelection().length > 0)
                    selModel.deselectAll();

                Ext4.each(idArray, function(id) {
                    selModel.select(id, true);
                });
            }
        }
    },

    onSaveAs : function() {
        var saveAsWindow = Ext4.create('Ext.window.Window', {
            width  : 500,
            height : 300,
            autoShow: true,
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
                items       : [
                    {xtype: 'textfield', name : 'name', fieldLabel : 'Report Name', allowBlank : false},
                    {xtype: 'textarea', name : 'description', fieldLabel : 'Report Description'},
                    {
                        xtype      : 'radiogroup',
                        width      : 300,
                        hidden     : !this.allowShare,
                        fieldLabel : 'Viewable By',
                        items      : [
                            {boxLabel : 'All readers',  width : 100, name : 'public', checked : this.allowShare, inputValue : true},
                            {boxLabel : 'Only me',   width : 100, name : 'public', inputValue : false}
                        ]
                    }
                ],
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
                            data['public'] = values['public'] || false;
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
        if (this.previewPanel)
            this.previewPanel.getEl().mask(msg || 'loading data...');
        else if (this.filterWindow) {
            this.filterWindow.getEl().mask(msg || 'Filtering...');
        }
    },

    unmask : function() {
        if (this.previewPanel)
            this.previewPanel.getEl().unmask();
        else if (this.filterWindow) {
            this.filterWindow.getEl().unmask();
        }
    }
});
