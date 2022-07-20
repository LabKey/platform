/*
 * Copyright (c) 2013-2019 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.define('LABKEY.ext4.ScriptReportPanel', {

    extend: 'Ext.tab.Panel',

    mixins: {
        externaleditor: 'LABKEY.ext4.ExternalEditorHelper',
        jupyterOptions: 'LABKEY.ext4.JupyterReportOptions'
    },

    border: false,

    frame: false,

    sourceAndHelp: true,

    /**
     * {boolean} reportConfig properties will be html decoded upon construction
     */
    htmlEncodedProps: false,

    constructor: function(config){
        this.mixins.externaleditor.constructor.apply(this, arguments);
        this.mixins.jupyterOptions.constructor.apply(this, arguments);
        this.callParent([config]);
    },

    initComponent : function() {

        Ext4.QuickTips.init();

        this.reportConfig.script = this.script;

        if (this.htmlEncodedProps) {
            Ext4.apply(this.reportConfig, {
                dataRegionName: Ext4.htmlDecode(this.reportConfig.dataRegionName),
                schemaName: Ext4.htmlDecode(this.reportConfig.schemaName),
                queryName: Ext4.htmlDecode(this.reportConfig.queryName),
                viewName: Ext4.htmlDecode(this.reportConfig.viewName)
            });
        }

        this.items = [this.createViewPanel()];

        if (this.reportConfig.schemaName && this.reportConfig.queryName) {
            this.items.push(this.createDataPanel());
        }

        this.items.push(this.createSourcePanel());
        this.items.push(this.createHelpPanel());
        if (this.sourceAndHelp) {
            if (this.preferSourceTab)
                this.activeTab = this.items.length-2;
        }

        this.errorTpl = new Ext4.XTemplate(
                '<span class="labkey-error" style="display: block; margin-top: 50px; margin-left: 50px;"><div>Failed to retrieve report results : {exception}</div>',
                '<pre>',
                '<tpl for="stackTrace">',
                '<div>{.}</div>',
                '</tpl>',
                '</pre>',
                '</span>'
        );

        this.callParent();

        window.onbeforeunload = LABKEY.beforeunload(this.beforeUnload, this);
    },

    markDirty : function(dirty) {
        this.dirty = dirty;
    },

    resetDirty : function() {
        this.dirty = false;
        if (this.codeMirror)
            this.reportConfig.script = this.codeMirror.getValue();
    },

    isDirty : function() {
        return this.dirty || (this.codeMirror && this.codeMirror.getValue().replace(/\s/g, '') != this.reportConfig.script.replace(/\s/g, ''));
    },

    beforeUnload : function() {
        if (this.isDirty()) {
            return 'please save your changes';
        }
    },

    createViewPanel : function() {

        var panelId = Ext4.id();

        return {
            title   : 'Report',
            html    : {
                tag : 'div',
                children : [
                    {tag : 'div', cls : 'reportView', id : panelId},
                    {tag : 'div', cls : 'reportView', id : 'backgroundReportDiv'}
                ]
            },
            bodyPadding : 10,
            autoScroll  : true,
            listeners : {
                activate : {fn : function(cmp){

                    var url = this.getViewURL();
                    if (this.isViewConfigChanged(url))
                    {
                        var config = this.getViewConfig(url);

                        cmp.getEl().mask("Loading report results...");
                        Ext4.Ajax.request({
                            url : this.getViewURL(),
                            method: 'POST',
                            success: function(resp){
                                // issue 18430, unmask before we load the HTML, just in case there is a javascript error
                                // in the rendered content
                                cmp.getEl().unmask();
                                // Update the view div with the returned HTML, and make sure scripts are run
                                LABKEY.Utils.loadAjaxContent(resp, panelId, function() {
                                    cmp.doLayout();
                                });
                            },
                            failure : function(resp, exp) {
                                this.viewFailure(cmp, resp, exp);
                            },
                            jsonData: config.parameters,
                            scope   : this
                        });
                    }
                }, scope : this}
            }
        };
    },

    viewFailure : function(cmp, resp, exp) {

        var error = null;
        if (resp && resp.responseText && resp.getResponseHeader('Content-Type'))
        {
            var contentType = resp.getResponseHeader('Content-Type');
            if (contentType.indexOf('application/json') >= 0)
            {
                var json = LABKEY.Utils.decode(resp.responseText);
                error = this.errorTpl.apply(json);
            }
        }
        if (!error) {
            error = this.errorTpl.apply({exception: LABKEY.Utils.getMsgFromError(resp, exp)});
        }
        Ext4.get(cmp.getEl()).update(error);
        cmp.getEl().unmask();
        this.prevScriptSource = null;
        this.prevViewURL = null;
    },

    isViewConfigChanged : function(viewURL) {

        if (!this.prevScriptSource)
            return true;
        if (this.codeMirror && this.codeMirror.getValue() != this.prevScriptSource)
            return true;
        if (viewURL != this.prevViewURL)
            return true;

        var form = this.formPanel.getForm();
        if (form && form.isValid())
        {
            if (this.prevFormValues != form.getValues(true))
                return true;
        }
        return false;
    },

    getViewConfig : function(viewURL) {

        var form = this.formPanel.getForm();
        var data = {};

        if (form && form.isValid())
        {
            data = this.getScriptFormValues(form);
            this.prevFormValues = form.getValues(true);
        }

        var script = this.codeMirror ? this.codeMirror.getValue() : this.reportConfig.script;

        data.script = script;
        data.dataRegionName = this.reportConfig.dataRegionName;

        // save previous values
        this.prevScriptSource = script;
        this.prevViewURL = viewURL;

        return {parameters : data};
    },

    getScriptFormValues: function(form) {
        var values = form.getValues();
        values.useDefaultOutputFormat = values.useCustomOutputFormat === 'true' ? 'false' : 'true';
        return values;
    },

    createDataPanel : function() {

        return {
            xtype       : 'box',
            title       : 'Data',
            autoScroll  : true,
            cls         : 'iScroll reportData',
            bodyPadding : 10,
            listeners   : {
                render : {fn : function(cmp){this.renderDataGrid(cmp);}, scope : this}
            }
        };

    },

    renderDataGrid : function(cmp) {
        var filters = LABKEY.Filter.getFiltersFromUrl(this.initialURL, this.reportConfig.dataRegionName);
        var sort = LABKEY.Filter.getSortFromUrl(this.initialURL, this.reportConfig.dataRegionName);
        var params = LABKEY.Filter.getQueryParamsFromUrl(this.initialURL, this.reportConfig.dataRegionName);

        var config = {
            schemaName  : this.reportConfig.schemaName,
            queryName   : this.reportConfig.queryName,
            viewName    : this.reportConfig.viewName,
            parameters  : params,
            removeableFilters   : filters,
            removeableSort      : sort,
            dataRegionName      : this.reportConfig.dataRegionName + '_report',
            frame       : 'none',
            showDetailsColumn       : false,
            showUpdateColumn        : false,
            showRecordSelectors     : false,
            showSurroundingBorder   : false,
            showPagination          : true,
            disableAnalytics        : true,
            allowHeaderLock         : false,
            buttonBar   : {
                includeStandardButton: false,
                suppressWarnings: true,
                items: [LABKEY.QueryWebPart.standardButtons.exportRows, LABKEY.QueryWebPart.standardButtons.print]
            },
            success : this.onDataSuccess,
            failure : this.onDataFailure,
            scope   : this
        };

        if (this.viewName)
            config.viewName = this.viewName;

        var wp = new LABKEY.QueryWebPart(config);

        wp.render(cmp.getId());
    },

    onDataSuccess : function(dr) {

        if (dr)
        {
            this.dataRegion = dr;

            // On first load of the QWP, initialize the "previous view URL" to match the current dataregion state.  This
            // prevents an unnecessary refresh of the report in scenarios like "Source" -> "View" -> "Data" -> "View".
            if (!this.initialLoad)
            {
                this.initialLoad = true;
                this.prevViewURL = this.getViewURL();
            }

            // show any filter messages on the enclosing dataregion's msg area
            var outerRegion = LABKEY.DataRegions[this.reportConfig.dataRegionName];
            var msgPart = 'filter';

            if (outerRegion)
            {
                if (dr.hasMessage(msgPart))
                {
                    outerRegion.addMessage(dr.getMessage(msgPart), msgPart);
                }
                else
                {
                    outerRegion.removeMessage(msgPart);
                }
            }
        }
    },

    onDataFailure : function(cmp) {

        Ext4.get(cmp.getEl()).update('<span class="labkey-error" style="width: 600px; height: 400px; display: block; margin-left: 250px;">Failed to retrieve data grid.</span>');
    },

    createSourcePanel : function() {

        var items = [];
        var id = Ext4.id();

        // boolean helpers for the report access levels
        var accessIsPublic = this.reportConfig.reportAccess == 'public';
        var accessIsCustom = this.reportConfig.reportAccess == 'custom';
        var accessIsPrivate = this.reportConfig.reportAccess == 'private';

        // boolean indicating if the user can create script reports (is developer and at least author in container)
        var isScriptEditor = LABKEY.user.isAnalyst && LABKEY.user.canInsert;

        items.push({
            xtype : 'fieldset',
            title : 'Script Source',
            items : [{
                xtype   : 'box',
                frame   : false,
                html    : '<textarea rows="25" name="metadata" id="' + id + '"></textarea>',
                listeners : {
                    render : {fn : function(cmp){

                        var code = Ext4.get(id);
                        var size = cmp.getSize();

                        if (code) {

                            this.codeMirror = CodeMirror.fromTextArea(code.dom, {
                                mode            : this.reportConfig.editAreaSyntax ? this.reportConfig.editAreaSyntax : 'text/plain',
                                lineNumbers     : true,
                                lineWrapping    : true,
                                readOnly        : this.readOnly || !isScriptEditor,
                                indentUnit      : 3
                            });

                            this.codeMirror.setSize(null, size.height + 'px');
                            this.codeMirror.setValue(this.reportConfig.script);

                            LABKEY.codemirror.RegisterEditorInstance('script-report-editor', this.codeMirror);
                        }

                        // for new reports give the option of a report specific initialization behavior
                        if (!this.reportConfig.reportId)
                            this.onNewReport();

                    }, scope : this}
                }
            }]
        });

        items.push({
            xtype : 'fieldset',
            title : 'Options',
            hidden: this.readOnly || !isScriptEditor,
            defaults    : {xtype : 'checkbox', labelWidth : 12},
            items : [
                {
                    name : 'shareReport',
                    boxLabel : 'Make this report available to all users' + (this.reportConfig.reportAccess != null
                                ? '&nbsp;<span data-qtip="Current report access level: ' + this.reportConfig.reportAccess + '"><i class="fa fa-question-circle-o"></i></span>'
                                : ''),
                    checked : accessIsPublic,
                    disabled: accessIsCustom,
                    listeners : {
                        scope: this,
                        'change': function(cb, value) {
                            this.down('checkbox[name=sourceTabVisible]').setDisabled(!value);
                            if (!value)
                                this.down('checkbox[name=sourceTabVisible]').setValue(null);
                        }
                    }
                },{
                    name : 'sourceTabVisible',
                    boxLabel : 'Show source tab to all users',
                    checked : this.reportConfig.sourceTabVisible,
                    disabled : this.reportConfig.reportAccess == null || accessIsPrivate
                },{
                    name : 'inheritable',
                    hidden : !this.reportConfig.allowInherit,
                    boxLabel : 'Make this report available in child folders&nbsp;' +
                            '<span data-qtip="If this check box is selected, this report will be available in data grids of child folders where the schema and table are the same as this data grid."><i class="fa fa-question-circle-o"></i></span>',
                    checked : this.reportConfig.inheritable
                },{
                    name : 'runInBackground',
                    hidden : !this.reportConfig.supportsPipeline,
                    boxLabel : 'Run this report in the background as a pipeline job',
                    checked : this.reportConfig.runInBackground,
                    listeners : {
                        scope: this,
                        'change': function(cb, value) {this.runInBackground = value;}
                    }
                }
            ]
        });

        if (this.reportConfig.knitrOptions)
        {
            items.push({
                xtype : 'fieldset',
                title : 'Knitr Options',
                collapsible : true,
                collapsed   : true,
                defaults    : {xtype : 'radio', labelWidth : 12},
                hidden      : this.readOnly || !isScriptEditor,
                items : [
                    {name : 'knitrFormat',
                        inputValue : 'None',
                        boxLabel : 'None&nbsp;' +
                                '<span data-qtip="The source is run without going through knitr."><i class="fa fa-question-circle-o"></i></span>',
                        checked : this.reportConfig.knitrFormat == 'None'},
                    {name : 'knitrFormat',
                        inputValue : 'Html',
                        boxLabel : 'Html&nbsp;' +
                                '<span data-qtip="Use knitr to process html source"><i class="fa fa-question-circle-o"></i></span>',
                        checked : this.reportConfig.knitrFormat == 'Html'},
                    {name : 'knitrFormat',
                        inputValue : 'Markdown',
                        boxLabel : 'Markdown&nbsp;' +
                                '<span data-qtip="Use knitr to process markdown source"><i class="fa fa-question-circle-o"></i></span>',
                        checked : this.reportConfig.knitrFormat == 'Markdown',
                        listeners : {
                            scope: this,
                            change: function(cb, value) {
                                this.down('checkbox[name=useCustomOutputFormat]').setDisabled(!value);
                            }
                        }
                    },
                    {name : 'useCustomOutputFormat',
                        xtype : 'checkbox',
                        inputValue : 'true',
                        margin : '5 0 0 20',
                        boxLabel : 'Use advanced rmarkdown output_options (pandoc only) &nbsp;' +
                        '<span data-qtip="If unchecked, rmarkdown will use default output format: html_document_base(keep_md=TRUE, self_contained=FALSE, fig_caption=TRUE, theme=NULL, css=NULL, smart=TRUE, highlight=&quot;default&quot;). Enable to use customized output_options."><i class="fa fa-question-circle-o"></i></span>',
                        checked : this.reportConfig.useDefaultOutputFormat === null || this.reportConfig.useDefaultOutputFormat === false,
                        disabled: this.reportConfig.knitrFormat !== 'Markdown',
                        listeners : {
                            scope: this,
                            change: function(cb, value) {
                                this.down('hidden[name=useDefaultOutputFormat]').setValue(!value);
                                this.down('textarea[name=rmarkdownOutputOptions]').setDisabled(!value);
                            },
                            disable: function() {
                                this.down('textarea[name=rmarkdownOutputOptions]').setDisabled(true);
                            },
                            enable: function() {
                                this.down('textarea[name=rmarkdownOutputOptions]').setDisabled(false);
                            }
                        }
                    },
                    {name : 'useDefaultOutputFormat', xtype : 'hidden', value: this.reportConfig.useDefaultOutputFormat !== false ? 'true' : 'false'},
                    // checkbox requires SpringActionController.FIELD_MARKER=='@' to handle 'clear' correctly
                    {name : '@useCustomOutputFormat', xtype : 'hidden'},
                    { name : 'rmarkdownOutputOptions',
                        xtype : 'textarea',
                        value : this.reportConfig.rmarkdownOutputOptions,
                        margin : '5 0 0 20',
                        grow : true,
                        width : 450,
                        disabled: this.reportConfig.useDefaultOutputFormat !== false || this.reportConfig.knitrFormat !== 'Markdown',
                        hideLabel : true
                    },
                    { xtype : 'label',
                      html : 'Dependencies&nbsp;' +
                              '<span data-qtip="Add a semi-colon delimited list of javascript, CSS, or library dependencies here."><i class="fa fa-question-circle-o"></i></span>'},
                    { name : 'scriptDependencies',
                        xtype : 'textarea',
                        value : this.reportConfig.scriptDependencies,
                        margin : '5 0 0 0',
                        grow : true,
                        width : 450,
                        hideLabel : true
                    }
                ]
            });
        }

        if (this.reportConfig.javascriptOptions)
        {
            items.push({
                xtype : 'fieldset',
                title : 'JavaScript Options',
                collapsible : true,
                collapsed   : true,
                hidden      : this.readOnly || !isScriptEditor,
                defaults    : {xtype : 'checkbox', labelWidth : 12},
                items : [
                    {name : 'useGetDataApi',
                        boxLabel : 'Use GetData API&nbsp;' +
                        '<span data-qtip="Uses the GetData API to retrieve data. Allows you to pass the data through one or more transforms before retrieving it. ' +
                                    'See the documentation at : www.labkey.org/download/clientapi_docs/javascript-api/symbols/LABKEY.Query.GetData.html"><i class="fa fa-question-circle-o"></i></span>',
                        checked : this.reportConfig.useGetDataApi,
                        uncheckedValue : false}
                ]
            });
        }

        this.getJupyterReportOptions(items, isScriptEditor);

        if (this.reportConfig.thumbnailOptions)
        {
            // thumbnail options
            var thumbnails = [];

            thumbnails.push({name : 'thumbnailType',
                boxLabel : 'Auto-generate&nbsp;' +
                        '<span data-qtip="Auto-generate a new thumbnail based on the first available output from this report (i.e. image, pdf, etc.)"><i class="fa fa-question-circle-o"></i></span>',
                inputValue : 'AUTO', checked : this.reportConfig.thumbnailType == 'AUTO'});
            thumbnails.push({name : 'thumbnailType',
                boxLabel : 'None&nbsp;' +
                        '<span data-qtip="Use the default static image for this report"><i class="fa fa-question-circle-o"></i></span>',
                inputValue : 'NONE', checked : this.reportConfig.thumbnailType == 'NONE'});

            if (this.reportConfig.thumbnailType == 'CUSTOM')
                thumbnails.push({name : 'thumbnailType', boxLabel : 'Keep existing', inputValue : 'CUSTOM', checked : true});

            items.push({
                xtype : 'fieldset',
                title : 'Report Thumbnail',
                defaults    : {xtype : 'radio'},
                collapsible : true,
                collapsed   : true,
                hidden      : this.readOnly || !isScriptEditor,
                items       : thumbnails
            });
        }

        if (this.sharedScripts && this.sharedScripts.length > 0)
        {
            var scripts = [];

            for (var i=0; i < this.sharedScripts.length; i++)
            {
                var script = this.sharedScripts[i];
                scripts.push({name : 'includedReports', boxLabel : script.name, inputValue : script.reportId, checked : script.included});
            }

            items.push({
                xtype : 'fieldset',
                title : 'Shared Scripts',
                collapsible : true,
                collapsed   : true,
                defaults    : {xtype : 'checkbox'},
                hidden      : this.readOnly || !isScriptEditor,
                items : scripts
            });
        }

        if (this.reportConfig.studyOptions && LABKEY.moduleContext.study)
        {
            var subjectNoun = LABKEY.moduleContext.study.subject.nounSingular;
            items.push({
                xtype : 'fieldset',
                title : 'Study Options',
                collapsible : true,
                collapsed   : true,
                hidden      : this.readOnly || !isScriptEditor,
                defaults    : {xtype : 'checkbox', labelWidth : 12},
                items : [
                    {name : 'filterParam',
                        inputValue : 'participantId',
                        boxLabel : subjectNoun + ' chart&nbsp;' +
                        '<span data-qtip="' + subjectNoun + ' chart views show measures for only one ' + subjectNoun + ' at a time. ' + subjectNoun +
                                                ' chart views allow the user to step through charts for each ' + subjectNoun + ' shown in any dataset grid."><i class="fa fa-question-circle-o"></i></span>',
                        checked : this.reportConfig.filterParam == 'participantId'},
                    {name : 'cached',
                        boxLabel : 'Automatically cache this report for faster reloading',
                        checked : this.reportConfig.cached}
                ]
            });
        }

        items.push({
            xtype: 'button',
            text: 'Save',
            hidden: this.readOnly,
            scope: this,
            handler: function() {
                if (this.reportConfig.reportId) {
                    this.save(this.saveURL);
                }
                else {
                    this.showSaveReportPrompt(this.saveURL, 'Save Report', false);
                }
            }
        });

        items.push({
            xtype: 'button',
            text: 'Save As',
            hidden: !isScriptEditor || this.reportConfig.reportId == null,
            style: 'margin-left: 5px;',
            scope: this,
            handler: function() {
                this.showSaveReportPrompt(this.saveURL, 'Save Report As', true);
            }
        });

        items.push({
            xtype: 'button',
            text: 'Share Report',
            hidden: !this.allowShareReport || this.reportConfig.reportId == null,
            style: 'margin-left: 5px;',
            scope: this,
            handler: function() {
                window.location = LABKEY.ActionURL.buildURL('reports', 'shareReport', null, {
                    reportId: this.reportConfig.reportId
                });
            }
        });

        if (this.externalEditSettings) {
            items.push(this.getExternalEditBtn());
        }

        items.push({
            xtype: 'button',
            text: 'Cancel',
            hidden: !isScriptEditor,
            style: 'margin-left: 5px;',
            scope: this,
            handler: function() {
                this.resetDirty();
                if (this.redirectUrl) {
                    window.location = this.redirectUrl;
                }
                else {
                    window.location.reload();
                }
            }
        });

        // hidden elements
        items.push({xtype : 'hidden', name : 'reportType', value : this.reportConfig.reportType});
        items.push({xtype : 'hidden', name : 'schemaName', value : this.reportConfig.schemaName});
        items.push({xtype : 'hidden', name : 'queryName', value : this.reportConfig.queryName});
        if (this.reportConfig.viewName)
            items.push({xtype : 'hidden', name : 'viewName', value : this.reportConfig.viewName});

        items.push({xtype : 'hidden', name : 'dataRegionName', value : this.reportConfig.dataRegionName});
        items.push({xtype : 'hidden', name : 'redirectUrl', value : this.redirectUrl});

        this.formPanel = Ext4.create('Ext.form.Panel', {
            title   : 'Source',
            cls     : 'reportSource',
            hidden  : !this.sourceAndHelp,
            frame   : false,
            bodyPadding : 10,
            fieldDefaults  : {
                labelWidth : 100,
                style      : 'padding: 4px 0',
                labelSeparator : ''
            },
            items   : items,
            listeners : {
                dirtychange : {fn : function(cmp, dirty){this.markDirty(dirty);}, scope : this},
                render: {
                    fn : this.showMaskOnFormLoad,
                    scope : this
                }
            }
        });

        return this.formPanel;
    },

    showSaveReportPrompt : function(saveUrl, title, isSaveAs, initExternalWindow) {
        Ext4.MessageBox.show({
            title   : title,
            msg     : 'Please enter a report name:',
            buttons : Ext4.MessageBox.OKCANCEL,
            fn      : function(btnId, name) {
                if (btnId == 'ok')
                    this.save(saveUrl, name, isSaveAs, initExternalWindow);
            },
            prompt  : true,
            scope   : this
        });
    },

    createHelpPanel : function() {

        return {
            title   : 'Help',
            frame   : false,
            hidden  : !this.sourceAndHelp || !this.reportConfig.helpHtml,
            bodyPadding : 10,
            html    : this.reportConfig.helpHtml
        };
    },

    // Build up an AJAX url that includes all the report rendering parameters.  Ideally, we would AJAX post
    // the entire form instead of creating a custom URL this way, but this lets us track changes more easily.
    // CONSIDER: AJAX post the form and track dirty on the form, data filters, data sorts, etc.
    //
    getViewURL : function() {

        var url = this.dataRegion ? this.baseURL : this.initialURL;

        if (this.dataRegion)
        {
            var prefix = this.dataRegion.name + '.';
            var params = LABKEY.ActionURL.getParameters(this.dataRegion.requestURL);

            for (var name in params)
            {
                if (name.substr(0, prefix.length) == prefix)
                {
                    // Translate "Data" tab dataregion params to use view tab dataregion name
                    var newName = this.reportConfig.dataRegionName + '.' + name.substr(prefix.length);
                    url = url + '&' + encodeURIComponent(newName) + '=' + encodeURIComponent(params[name]);
                }
            }
        }

        return url;
    },

    save : function(url, name, isSaveAs, initExternalWindow) {

        var form = this.formPanel.getForm();

        if (form && form.isValid())
        {
            var data = this.getScriptFormValues(form);

            if (!isSaveAs && this.reportConfig.reportId) {
                data.reportId = this.reportConfig.reportId;
            }
            else {
                data.reportName = name;
                data.redirectUrl = null; // force redirect back to the newly saved report
            }

            data.script = this.codeMirror.getValue();

            if (initExternalWindow)
                this.initExternalWindow();

            Ext4.Ajax.request({
                url : url,
                method  : 'POST',
                success : function(resp, opt) {
                    var o = Ext4.decode(resp.responseText);

                    if (o.success) {
                        this.resetDirty();
                        if (o.externalUrl)
                            this.openExternalEditor(o);
                        else
                            window.location = o.redirect;
                    }
                    else {
                        if (initExternalWindow)
                            this.closeExternalEditor();
                        LABKEY.Utils.displayAjaxErrorResponse(resp, opt);
                    }
                },
                failure : function(resp, opt) {
                    if (initExternalWindow)
                        this.closeExternalEditor();
                    LABKEY.Utils.displayAjaxErrorResponse(resp, opt);
                },
                jsonData: data,
                scope   : this
            });
        }
    }

});
