/*
 * Copyright (c) 2012-2015 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('LABKEY.ext4.SurveyDesignPanel', {

    extend : 'Ext.panel.Panel',

//    requires : ['LABKEY.sqv.Model'],

    constructor : function(config) {

        Ext4.QuickTips.init();

        Ext4.applyIf(config, {
            frame   : false,
            layout : {
                type : 'border'
            }
        });
        this.callParent([config]);
    },

    initComponent : function() {

        this.items = [];

        this.items.push(this.createMainPanel());
        this.items.push(this.createTemplatePanel());
        this.items.push(this.createExamplesPanel());

        var createTemplateBtn = Ext4.create('Ext.button.Button', {
            text : 'Generate Survey Questions',
            formBind: true,
            handler : function(btn) {
                var form = this.formPanel.getForm();
                var values = form.getValues();
                if (values.schemaName && values.queryName)
                {
                    if (this.codeMirror.lineCount() > 1) {

                        Ext4.Msg.show({
                            title: "Generate Survey Questions",
                            msg: 'Your existing survey template will be overwritten with a new template. Do you wish to continue?',
                            buttons: Ext4.MessageBox.YESNO,
                            icon: Ext4.MessageBox.QUESTION,
                            fn : function(buttonId) {
                                if (buttonId == 'yes')
                                    this.generateSurveyQuestions(values.schemaName, values.queryName);
                            },
                            scope : this
                        });
                    }
                    else
                        this.generateSurveyQuestions(values.schemaName, values.queryName);
                }
                else
                    Ext4.Msg.show({
                         title: "Error",
                         msg: 'A schema and query must be selected first.',
                         buttons: Ext4.MessageBox.OK,
                         icon: Ext4.MessageBox.ERROR
                    });
            },
            scope   : this
        });

        var saveBtn = Ext4.create('Ext.button.Button', {
            text : 'Save Survey',
            formBind: true,
            handler : function(btn) {
                var form = this.formPanel.getForm();
                var surveyQuestions = this.codeMirror.getValue();

                if (surveyQuestions == null || surveyQuestions.length == 0) {

                    Ext4.Msg.show({
                         title: "Error",
                         msg: 'Survey questions cannot be empty.',
                         buttons: Ext4.MessageBox.OK,
                         icon: Ext4.MessageBox.ERROR
                    });
                }
                else if (form.isValid()) {

                    var values = form.getValues();

                    // hack: since we are not able to update the CodeMirror input field via selenium, we reshow the
                    // textarea and enter the value there, so check to see if the metadata textarea has a value first
                    var metadata = Ext4.get(this.metadataId).dom.value;
                    values.metadata = metadata != null && metadata.length > 0 ? metadata : surveyQuestions;

                    Ext4.Ajax.request({
                        url     : LABKEY.ActionURL.buildURL('survey', 'saveSurveyTemplate.api'),
                        method  : 'POST',
                        jsonData: values,
                        success : function(resp){
                            var o = Ext4.decode(resp.responseText);

                            if (o.success)
                            {
                                if (this.returnUrl)
                                    window.location = this.returnUrl;
                                else
                                    window.history.back();
                            }
                            else if (o.errorInfo)
                            {
                                Ext4.MessageBox.alert('Error', o.errorInfo.message);

                                var pos = {};
                                if (o.errorInfo.line)
                                    pos.line = o.errorInfo.line-1;
                                if (o.errorInfo.column)
                                    pos.ch = o.errorInfo.column;

                                if (this.codeMirror) {

                                    this.codeMirror.setCursor(pos);
                                    this.codeMirror.setSelection({ch:0, line:pos.line}, pos);
                                    this.codeMirror.scrollIntoView(pos);
                                }
                            }
                        },
                        failure : this.onFailure,
                        scope   : this
                    });

                }
                else
                    Ext4.Msg.show({
                         title: "Error",
                         msg: 'Please fill in all required fields.',
                         buttons: Ext4.MessageBox.OK,
                         icon: Ext4.MessageBox.ERROR
                    });
            },
            scope   : this
        });

        this.tbar = [createTemplateBtn, '->', saveBtn];

        // reload an existing survey
        this.on('render', function(cmp){

            if (this.surveyId) {

                Ext4.Ajax.request({
                    url     : LABKEY.ActionURL.buildURL('survey', 'getSurveyTemplate.api'),
                    method  : 'POST',
                    jsonData: {rowId : this.surveyId},
                    success : function(resp){
                        var o = Ext4.decode(resp.responseText);

                        if (o.success)
                            this.loadSurvey(o.survey);
                    },
                    failure : this.onFailure,
                    scope   : this
                });
            }
        }, this);

        this.callParent();
    },

    createMainPanel : function() {

        var model = Ext4.create('LABKEY.sqv.Model', {});

        model.changeQueryStore = function(containerId, schema){

            var me = this;
            me.queryCombo.setDisabled(false);
            me.queryCombo.clearValue();

            Ext4.Ajax.request({
                url     : LABKEY.ActionURL.buildURL('survey', 'getValidDesignQueries.api'),
                method  : 'POST',
                jsonData: {schemaName : schema},
                success : function(resp){

                    var o = Ext4.decode(resp.responseText);

                    if (o.queries)
                    {
                        me.queryStore.loadData(o.queries);
                        me.queryCombo.fireEvent('dataloaded', me.queryCombo);

                    }
                },
                failure : this.onFailure,
                scope   : this
            });
        };

        var items = [];

        items.push({
            xtype : 'textfield',
            name  : 'label',
            fieldLabel : 'Label',
            allowBlank : false,
            emptyText : 'Enter a name for this Survey'

        });

        items.push({
            xtype : 'textarea',
            name  : 'description',
            fieldLabel : 'Description'
        });

        // by default filter out schemas that we know don't work
        if (!this.allSchemas)
        {
            var regex = new RegExp('^core|^assay|^audit|^exp|^pipeline|^issues|^flow|^nab|' +
                    '^samples|^announce|^plate|^wiki|^visc', 'i');
            var storeConfig = {
                filters : [function(item){
                    return !item.data.schema.match(regex);
                }]
            };
        }
        items.push(model.makeSchemaComboConfig({
            name    : 'schemaName',
            itemId  : 'schemaCombo',
            storeConfig: storeConfig,
            allowBlank : false
        }));
        items.push(model.makeQueryComboConfig({
            name : 'queryName',
            itemId : 'queryCombo',
            allowBlank : false
        }));

        items.push({
            xtype : 'hidden',
            name  : 'rowId',
            value : this.surveyId
        });

        this.formPanel = Ext4.create('Ext.form.Panel', {
            border  : false,
            frame   : false,
            fieldDefaults  : {
                labelWidth : 100,
                width      : 325,
                style      : 'padding: 4px 0',
                labelSeparator : ''
            },
            items   : items
        });

        this.configOptionsPanel = Ext4.create('Ext.panel.Panel', {
            border: false,
            autoScroll: true,
            height: 460,
            title: 'Survey Configuration Options',
            cls: ' config-options',
            html: '<table>'
                + '<tr><td colspan="2"><span class="vartype">{String}</span> <span class="varname">beforeLoad.fn</span></td></tr>'
                + '<tr><td class="spacer"></td><td>A javascript function to run prior to creating the survey panel. Useful for loading custom scripts, the specified function '
                +   'is called with two parameters : callback & scope which should be invoked after the furnished function has run, for example '
                +   '<i>"fn": "function(callback, scope){ LABKEY.requiresScript(\'myscript.js\', callback, scope); }"</i>'
                +   '</td></tr>'
                + '<tr><td colspan="2"><span class="vartype">{Object}</span> <span class="varname">footerWiki</span></td></tr>'
                + '<tr><td class="spacer"></td><td>Display a wiki below the survey panel</td></tr>'
                + '<tr><td colspan="2"><span class="vartype">{String}</span> <span class="varname">footerWiki.containerPath</span></td></tr>'
                + '<tr><td class="spacer"></td><td>The container path for the wiki. Defaults to current container.</td></tr>'
                + '<tr><td colspan="2"><span class="vartype">{String}</span> <span class="varname">footerWiki.name</span></td></tr>'
                + '<tr><td class="spacer"></td><td>The name of the wiki</td></tr>'
                + '<tr><td colspan="2"><span class="vartype">{Object}</span> <span class="varname">headerWiki</span></td></tr>'
                + '<tr><td class="spacer"></td><td>Display a wiki above the survey panel</td></tr>'
                + '<tr><td colspan="2"><span class="vartype">{String}</span> <span class="varname">headerWiki.containerPath</span></td></tr>'
                + '<tr><td class="spacer"></td><td>The container path for the wiki. Defaults to current container.</td></tr>'
                + '<tr><td colspan="2"><span class="vartype">{String}</span> <span class="varname">headerWiki.name</span></td></tr>'
                + '<tr><td class="spacer"></td><td>The name of the wiki</td></tr>'
                + '<tr><td colspan="2"><span class="vartype">{String}</span> <span class="varname">layout</span></td></tr>'
                + '<tr><td class="spacer"></td><td>Options are "card" (wizard like layout with section titles listed in side bar) or "auto" (vertical display of sections). Defaults to "auto".</td></tr>'
                + '<tr><td colspan="2"><span class="vartype">{Integer}</span> <span class="varname">mainPanelWidth</span></td></tr>'
                + '<tr><td class="spacer"></td><td>In card layout, the width of the main section panel. Defaults to 800.</td></tr>'
                + '<tr><td colspan="2"><span class="vartype">{Boolean}</span> <span class="varname">navigateOnSave</span></td></tr>'
                + '<tr><td class="spacer"></td><td>True to navigate away from the survey form after the save action. Navigation will take the user to the returnURL, if provided, or to the project begin action. Defaults to false.</td></tr>'
                + '<tr><td colspan="2"><span class="vartype">{Array}</span> <span class="varname">sections</span></td></tr>'
                + '<tr><td class="spacer"></td><td>An array of survey section panel config objects.</td></tr>'
                + '<tr><td colspan="2"><span class="vartype">{Boolean}</span> <span class="varname">sections.border</span></td></tr>'
                + '<tr><td class="spacer"></td><td>True to display a 1px border around the section. Defaults to false.</td></tr>'
                + '<tr><td colspan="2"><span class="vartype">{Boolean}</span> <span class="varname">sections.collapsed</span></td></tr>'
                + '<tr><td class="spacer"></td><td>In auto layout, true to start the section panel collapsed. Defaults to false.</td></tr>'
                + '<tr><td colspan="2"><span class="vartype">{Boolean}</span> <span class="varname">sections.collapsible</span></td></tr>'
                + '<tr><td class="spacer"></td><td>In auto layout, true to allow the section panel to be collapsed. Defaults to true.</td></tr>'
                + '<tr><td colspan="2"><span class="vartype">{Integer}</span> <span class="varname">sections.defaultLabelWidth</span></td></tr>'
                + '<tr><td class="spacer"></td><td>The default width to use for the question labels in this section. Defaults to 350.</td></tr>'
                + '<tr><td colspan="2"><span class="vartype">{String}</span> <span class="varname">sections.description</span></td></tr>'
                + '<tr><td class="spacer"></td><td>The text to display at the beginning of the section panel.</td></tr>'
                + '<tr><td colspan="2"><span class="vartype">{String}</span> <span class="varname">sections.extAlias</span></td></tr>'
                + '<tr><td class="spacer"></td><td>For custom survey development, the ext alias for a custom component.</td></tr>'
                + '<tr><td colspan="2"><span class="vartype">{Boolean}</span> <span class="varname">sections.header</span></td></tr>'
                + '<tr><td class="spacer"></td><td>Whether to show the Ext panel header for this section. Defaults to true.</td></tr>'
                + '<tr><td colspan="2"><span class="vartype">{Boolean}</span> <span class="varname">sections.initDisabled</span></td></tr>'
                + '<tr><td class="spacer"></td><td>In card layout, disabled the section title in the side bar. Defaults to false.</td></tr>'
                + '<tr><td colspan="2"><span class="vartype">{Boolean}</span> <span class="varname">sections.layoutHorizontal</span></td></tr>'
                + '<tr><td class="spacer"></td><td>If true, use a table layout with numColumns providing the number of columns. Defaults to false.</td></tr>'
                + '<tr><td colspan="2"><span class="vartype">{Integer}</span> <span class="varname">sections.numColumns</span></td></tr>'
                + '<tr><td class="spacer"></td><td>The number of columns to use in table layout for layoutHorizontal=true. Defaults to 1.</td></tr>'
                + '<tr><td colspan="2"><span class="vartype">{Integer}</span> <span class="varname">sections.padding</span></td></tr>'
                + '<tr><td class="spacer"></td><td>The padding to use between questions in this section. Defaults to 10.</td></tr>'
                + '<tr><td colspan="2"><span class="vartype">{Array}</span> <span class="varname">sections.questions</span></td></tr>'
                + '<tr><td class="spacer"></td><td>An array of question config objects to use for this section.</td></tr>'
                + '<tr><td colspan="2"><span class="vartype">{Object}</span> <span class="varname">sections.questions.extConfig</span></td></tr>'
                + '<tr><td class="spacer"></td><td>An ExtJS config object.</td></tr>'
                + '<tr><td colspan="2"><span class="vartype">{Boolean}</span> <span class="varname">sections.questions.required</span></td></tr>'
                + '<tr><td class="spacer"></td><td>Whether an answer is required before results can be submitted.</td></tr>'
                + '<tr><td colspan="2"><span class="vartype">{String}</span> <span class="varname">sections.questions.shortCaption</span></td></tr>'
                + '<tr><td class="spacer"></td><td>The text to display on the survey end panel for missing required questions.</td></tr>'
                + '<tr><td colspan="2"><span class="vartype">{Boolean}</span> <span class="varname">sections.questions.hidden</span></td></tr>'
                + '<tr><td class="spacer"></td><td>The default display state of this question (used with listeners).</td></tr>'
                + '<tr><td colspan="2"><span class="vartype">{Object}</span> <span class="varname">sections.questions.listeners</span></td></tr>'
                + '<tr><td class="spacer"></td><td>JavaScript listener functions to be added to questions for skip logic or additional validation (currently on \'change\' is supported).</td></tr>'
                + '<tr><td colspan="2"><span class="vartype">{Object}</span> <span class="varname">sections.questions.listeners.change</span></td></tr>'
                + '<tr><td class="spacer"></td><td>Listener action.</td></tr>'
                + '<tr><td colspan="2"><span class="vartype">{String or Array}</span> <span class="varname">sections.questions.listeners.change.question</span></td></tr>'
                + '<tr><td class="spacer"></td><td>Name(s) of parent question(s).</td></tr>'
                + '<tr><td colspan="2"><span class="vartype">{String}</span> <span class="varname">sections.questions.listeners.change.fn</span></td></tr>'
                + '<tr><td class="spacer"></td><td>JavaScript function to be executed on parent.</td></tr>'
                + '<tr><td colspan="2"><span class="vartype">{String}</span> <span class="varname">sections.title</span></td></tr>'
                + '<tr><td class="spacer"></td><td>The title text to display for the section (auto layout displays title in header, card layout displays title in side bar).</td></tr>'
                + '<tr><td colspan="2"><span class="vartype">{Boolean}</span> <span class="varname">showCounts</span></td></tr>'
                + '<tr><td class="spacer"></td><td>If true, show counts of completed questions next to the section title. Defaults to false.</td></tr>'
                + '<tr><td colspan="2"><span class="vartype">{Integer}</span> <span class="varname">sideBarWidth</span></td></tr>'
                + '<tr><td class="spacer"></td><td>In card layout, the width of the side bar (i.e. section title) panel. Defaults to 250.</td></tr>'
                + '<tr><td colspan="2"><span class="vartype">{Object}</span> <span class="varname">start</span></td></tr>'
                + '<tr><td class="spacer"></td><td>Config options for the first section of the survey.</td></tr>'
                + '<tr><td colspan="2"><span class="vartype">{String}</span> <span class="varname">start.description</span></td></tr>'
                + '<tr><td class="spacer"></td><td>The description to be added below the survey label field on the start section.</td></tr>'
                + '<tr><td colspan="2"><span class="vartype">{String}</span> <span class="varname">start.labelCaption</span></td></tr>'
                + '<tr><td class="spacer"></td><td>The label for the survey label field. Defaults to "Survey Label".</td></tr>'
                + '<tr><td colspan="2"><span class="vartype">{Integer}</span> <span class="varname">start.labelWidth</span></td></tr>'
                + '<tr><td class="spacer"></td><td>The label width for the survey label field. Defaults to 350.</td></tr>'
                + '<tr><td colspan="2"><span class="vartype">{String}</span> <span class="varname">start.sectionTitle</span></td></tr>'
                + '<tr><td class="spacer"></td><td>The title for the start section. Defaults to "Start".</td></tr>'
                + '<tr><td colspan="2"><span class="vartype">{Boolean}</span> <span class="varname">start.useDefaultLabel</span></td></tr>'
                + '<tr><td class="spacer"></td><td>If true, the survey label will be hidden and populated with the current date/time.</td></tr>'
                + '</table>'
        });

        return Ext4.create('Ext.panel.Panel', {
            frame   : false,
            region  : 'west',
            collapsible : true,
            defaults: {
                width: 355,
                bodyPadding : 15
            },
            items   : [this.formPanel, this.configOptionsPanel]
        });

        //return this.formPanel;
    },

    generateSurveyQuestions : function(schemaName, queryName) {

        Ext4.Ajax.request({
            url     : LABKEY.ActionURL.buildURL('survey', 'createSurveyTemplate.api'),
            method  : 'POST',
            jsonData: {queryName : queryName, schemaName : schemaName},
            success : function(resp){
                var o = Ext4.decode(resp.responseText);

                // refresh the code area
                if (o.survey) {
                    this.codeMirror.setValue(resp.responseText);
                }
            },
            failure : this.onFailure,
            scope   : this
        });
    },

    createTemplatePanel : function() {

        var items = [];

        this.metadataId = Ext4.id();
        var formPanel = Ext4.create('Ext.form.Panel', {
            frame   : false,
            html    : '<textarea rows="44" name="metadata" id="' + this.metadataId + '"></textarea>',
            region  : 'center',
            items   : items
        });
        formPanel.on('render', function(cmp){

            var code = Ext4.get(this.metadataId);
            var size = cmp.getSize();

            if (code) {

                this.codeMirror = CodeMirror.fromTextArea(code.dom, {
                    mode    : {name : 'javascript', json : true},
                    lineNumbers     : true,
                    lineWrapping    : true,
                    indentUnit : 3
                });

                this.codeMirror.setSize(null, size.height + 'px');
                LABKEY.codemirror.RegisterEditorInstance('metadata', this.codeMirror);

                // Issue 18776: different browsers set textarea size differently for rows="40", so set outer panel height plus the height of the top bar
                this.setHeight(size.height + 32);
            }
        }, this);

        return formPanel;
    },

    onFailure : function(resp){
        var o = Ext4.decode(resp.responseText);

        var error = o.exception;
        if(error)
            Ext4.MessageBox.alert('Error', error);
        else
            Ext4.MessageBox.alert('Error', 'An unknown error has occurred, unable to save the survey.');
    },

    initCombo : function(cmp, value) {

        if (cmp.getStore().getCount() == 0)
            Ext4.defer(this.initCombo, 20, this, [cmp, value]);
        else
            cmp.select(value, true);
    },

    loadSurvey : function(survey) {

        var form = this.formPanel.getForm();
        if (form) {

            var schemaCombo = this.down('#schemaCombo');
            if (schemaCombo && survey.schemaName)
            {
                this.initCombo(schemaCombo, survey.schemaName);
            }

            form.setValues(survey);
            this.codeMirror.setValue(survey.metadata);
        }
    },

    createExamplesPanel : function() {
        var items = [];

        items.push({
            title : 'Attachment',
            html  : '<code style="white-space:pre-wrap;">{<br/>' +
                    '    "jsonType": "string",<br/>' +
                    '    "hidden": false,<br/>' +
                    '    "width": 800,<br/>' +
                    '    "inputType": "file",<br/>' +
                    '    "name": "attfield",<br/>' +
                    '    "caption": "Attachment Field",<br/>' +
                    '    "shortCaption": "Attachment Field",<br/>' +
                    '    "required": false<br/>' +
                    '}</code>'
        });

        items.push({
            title : 'Checkbox (Single)',
            html  : '<code style="white-space:pre-wrap;">{<br/>' +
                    '    "jsonType": "boolean",<br/>' +
                    '    "hidden": false,<br/>' +
                    '    "width": 800,<br/>' +
                    '    "inputType": "checkbox",<br/>' +
                    '    "name": "boolfield",<br/>' +
                    '    "caption": "Boolean Field",<br/>' +
                    '    "shortCaption": "Boolean Field",<br/>' +
                    '    "required": false<br/>' +
                    '}</code>'
        });

        items.push({
            title : '<span style="font-style: italic;">Checkbox Group (ExtJS)</span>',
            html  : '<code style="white-space:pre-wrap;">{<br/>' +
                    '  "extConfig": {<br/>' +
                    '    "xtype": "fieldcontainer",<br/>' +
                    '    "width": 800,<br/>' +
                    '    "hidden": false,<br/>' +
                    '    "name": "checkbox_group",<br/>' +
                    '    "margin": "10px 10px 15px",<br/>' +
                    '    "fieldLabel": "CB Group (ExtJS)",<br/>' +
                    '    "items": [{<br/>' +
                    '      "xtype": "panel",<br/>' +
                    '      "border": true,<br/>' +
                    '      "bodyStyle":"padding-left:5px;",<br/>' +
                    '      "defaults": { <br/>' +
                    '        "xtype": "checkbox", <br/>' +
                    '        "inputValue": "true", <br/>' +
                    '        "uncheckedValue": "false"<br/>' +
                    '      },<br/>' +
                    '      "items": [<br/>' +
                    '          { <br/>' +
                    '            "boxLabel": "CB 1", <br/>' +
                    '            "name": "checkbox_1" <br/>' +
                    '          },<br/>' +
                    '          { <br/>' +
                    '            "boxLabel": "CB 2", <br/>' +
                    '            "name": "checkbox_2" <br/>' +
                    '          },<br/>' +
                    '          { <br/>' +
                    '            "boxLabel": "CB 3", <br/>' +
                    '            "name": "checkbox_3" <br/>' +
                    '          }<br/>' +
                    '      ]<br/>' +
                    '    }]<br/>' +
                    '  }<br/>' +
                    '}</code>'
        });

        items.push({
            title : 'Combobox (Lookup)',
            html  : '<code style="white-space:pre-wrap;">{<br/>' +
                    '    "jsonType": "int",<br/>' +
                    '    "hidden": false,<br/>' +
                    '    "width": 800,<br/>' +
                    '    "inputType": "text",<br/>' +
                    '    "name": "lkfield",<br/>' +
                    '    "caption": "Lookup Field",<br/>' +
                    '    "shortCaption": "Lookup Field",<br/>' +
                    '    "required": false,<br/>' +
                    '    "lookup": {<br/>' +
                    '       "keyColumn": "Key",<br/>' +
                    '       "displayColumn": "Value",<br/>' +
                    '       "schemaName": "lists",<br/>' +
                    '       "queryName": "lookup1",<br/>' +
                    '       "containerPath": "/Project/..."<br/>' +
                    '    }<br/>' +
                    '}</code>'
        });

        items.push({
            title : '<span style="font-style: italic;">Combobox (ExtJS)</span>',
            html  : '<code style="white-space:pre-wrap;">{<br/>' +
                    '    "extConfig": {<br/>' +
                    '        "width": 800,<br/>' +
                    '        "hidden": false,<br/>' +
                    '        "xtype": "combo",<br/>' +
                    '        "name": "gender",<br/>' +
                    '        "fieldLabel": "Gender (ExtJS)",<br/>' +
                    '        "queryMode": "local",<br/>' +
                    '        "displayField": "value",<br/>' +
                    '        "valueField": "value",<br/>' +
                    '        "emptyText": "Select...",<br/>' +
                    '        "forceSelection": true,<br/>' +
                    '        "store": {<br/>' +
                    '            "fields": ["value"],<br/>' +
                    '            "data" : [<br/>' +
                    '                {"value": "Female"},<br/>' +
                    '                {"value": "Male"}<br/>' +
                    '            ]<br/>' +
                    '        }<br/>' +
                    '    }<br/>' +
                    '}</code>'
        });

        items.push({
            title : 'Date Picker',
            html  : '<code style="white-space:pre-wrap;">{<br/>' +
                    '    "jsonType": "date",<br/>' +
                    '    "hidden": false,<br/>' +
                    '    "width": 800,<br/>' +
                    '    "inputType": "text",<br/>' +
                    '    "name": "dtfield",<br/>' +
                    '    "caption": "Date Field",<br/>' +
                    '    "shortCaption": "Date Field",<br/>' +
                    '    "required": false<br/>' +
                    '}</code>'
        });

        items.push({
            title : '<span style="font-style: italic;">Display Field (ExtJS)</span>',
            html  : '<code style="white-space:pre-wrap;">{<br/>' +
                    '    "extConfig": {<br/>' +
                    '        "width": 800,<br/>' +
                    '        "xtype": "displayfield",<br/>' +
                    '        "name": "dsplfield",<br/>' +
                    '        "hideLabel": true,<br/>' +
                    '        "value": "Some text."<br/>' +
                    '    }<br/>' +
                    '}</code>'
        });

        items.push({
            title : 'Double (Numeric)',
            html  : '<code style="white-space:pre-wrap;">{<br/>' +
                    '    "jsonType": "float",<br/>' +
                    '    "hidden": false,<br/>' +
                    '    "width": 800,<br/>' +
                    '    "inputType": "text",<br/>' +
                    '    "name": "dblfield",<br/>' +
                    '    "caption": "Double Field",<br/>' +
                    '    "shortCaption": "Double Field",<br/>' +
                    '    "required": false<br/>' +
                    '}</code>'
        });

        items.push({
            title : 'Integer',
            html  : '<code style="white-space:pre-wrap;">{<br/>' +
                    '    "jsonType": "int",<br/>' +
                    '    "hidden": false,<br/>' +
                    '    "width": 800,<br/>' +
                    '    "inputType": "text",<br/>' +
                    '    "name": "intfield",<br/>' +
                    '    "caption": "Integer Field",<br/>' +
                    '    "shortCaption": "Integer Field",<br/>' +
                    '    "required": false<br/>' +
                    '}</code>'
        });

        items.push({
            title : '<span style="font-style: italic;">Number Range (ExtJS)</span>',
            html  : '<code style="white-space:pre-wrap;">{<br/>' +
                    '    "extConfig": {<br/>' +
                    '        "xtype": "fieldcontainer",<br/>' +
                    '        "fieldLabel": "Number Range",<br/>' +
                    '        "margin": "10px 10px 15px",<br/>' +
                    '        "layout": "hbox",<br/>' +
                    '        "width": 800,<br/>' +
                    '        "items": [<br/>' +
                    '            { <br/>' +
                    '              "xtype": "numberfield", <br/>' +
                    '              "fieldLabel": "Min", <br/>' +
                    '              "name": "min_num", <br/>' +
                    '              "width": 175 <br/>' +
                    '            },<br/>' +
                    '            { <br/>' +
                    '              "xtype": "label", <br/>' +
                    '              "width": 25 <br/>' +
                    '            },<br/>' +
                    '            { <br/>' +
                    '              "xtype": "numberfield", <br/>' +
                    '              "fieldLabel": "Max", <br/>' +
                    '              "name": "max_num", <br/>' +
                    '              "width": 175 <br/>' +
                    '            }<br/>' +
                    '        ]<br/>' +
                    '    }<br/>' +
                    '}</code>'
        });

        items.push({
            title : '<span style="font-style: italic;">Positive Integer (ExtJS)</span>',
            html  : '<code style="white-space:pre-wrap;">{<br/>' +
                    '    "extConfig": {<br/>' +
                    '        "xtype": "numberfield",<br/>' +
                    '        "fieldLabel": "Positive Integer",<br/>' +
                    '        "name": "pos_int",<br/>' +
                    '        "allowDecimals": false,<br/>' +
                    '        "minValue": 0,<br/>' +
                    '        "width": 800<br/>' +
                    '    }<br/>' +
                    '}</code>'
        });

        items.push({
            title : '<span style="font-style: italic;">Survey Grid Question (ExtJS)</span>',
            html  : '<code style="white-space:pre-wrap;">{<br/>' +
                    ' "extConfig": {<br/>' +
                    '     "xtype": "surveygridquestion",<br/>' +
                    '     "name": "gridquestion",<br/>' +
                    '     "columns": {<br/>' +
                    '       "items": [{<br/>' +
                    '          "text": "Field 1", <br/>' +
                    '          "dataIndex": "field1", <br/>' +
                    '          "width": 350,<br/>' +
                    '          "editor": {<br/>' +
                    '             "xtype": "combo", <br/>' +
                    '             "queryMode": "local", <br/>' +
                    '             "displayField":"value",<br/>' +
                    '             "valueField": "value", <br/>' +
                    '             "forceSelection": true,<br/>' +
                    '             "store": {<br/>' +
                    '               "fields": ["value"], <br/>' +
                    '               "data" : [{<br/>' +
                    '                 "value": "Value 1"<br/>' +
                    '               }, {<br/>' +
                    '                 "value": "Value 2"<br/>' +
                    '               }, {<br/>' +
                    '                 "value": "Value 3"<br/>' +
                    '               }]<br/>' +
                    '             }<br/>' +
                    '          }<br/>' +
                    '        },<br/>' +
                    '        {<br/>' +
                    '            "text": "Field 2", <br/>' +
                    '            "dataIndex": "field2", <br/>' +
                    '            "width": 200,<br/>' +
                    '            "editor": {<br/>' +
                    '              "xtype": "textfield"<br/>' +
                    '            }<br/>' +
                    '        },<br/>' +
                    '        {<br/>' +
                    '            "text": "Field 3", <br/>' +
                    '            "dataIndex": "field3", <br/>' +
                    '            "width": 200,<br/>' +
                    '            "editor": {<br/>' +
                    '              "xtype": "textfield"<br/>' +
                    '             }<br/>' +
                    '        }]<br/>' +
                    '    },<br/>' +
                    '    "store": {<br/>' +
                    '        "xtype": "json",<br/>' +
                    '        "fields": [<br/>' +
                    '            "field1",<br/>' +
                    '            "field2",<br/>' +
                    '            "field3"<br/>' +
                    '        ]<br/>' +
                    '    }<br/>' +
                    ' }<br/>' +
                    '}</code>'
        });

        items.push({
            title : 'Survey Header/Footer Wiki',
            html  : '<code style="white-space:pre-wrap;">{"survey":{<br/>' +
                    '   "headerWiki": {<br/>' +
                    '      "name": "wiki_name",<br/>' +
                    '      "containerPath": "/Project/..."<br/>' +
                    '   },<br/>' +
                    '   "footerWiki": {<br/>' +
                    '      "name": "wiki_name",<br/>' +
                    '      "containerPath": "/Project/..."<br/>' +
                    '   },<br/>' +
                    '   ...<br/>' +
                    '}</code>'
        });

        items.push({
            title : 'Text Area',
            html  : '<code style="white-space:pre-wrap;">{<br/>' +
                    '    "jsonType": "string",<br/>' +
                    '    "hidden": false,<br/>' +
                    '    "width": 800,<br/>' +
                    '    "inputType": "textarea",<br/>' +
                    '    "name": "txtfield",<br/>' +
                    '    "caption": "Textarea Field",<br/>' +
                    '    "shortCaption": "Textarea Field",<br/>' +
                    '    "required": false<br/>' +
                    '}</code>'
        });

        items.push({
            title : 'Text Field (w/ Skip Logic)',
            html  : '<code style="white-space:pre-wrap;">{<br/>' +
                    '    "jsonType": "string",<br/>' +
                    '    "width": 800,<br/>' +
                    '    "inputType": "text",<br/>' +
                    '    "name": "strfield",<br/>' +
                    '    "caption": "String Field",<br/>' +
                    '    "shortCaption": "String Field",<br/>' +
                    '    "required": false,<br/>' +
                    '    "hidden": true,<br/>' +
                    '    "listeners" : {<br/>' +
                    '       "change" : {<br/>' +
                    '          "question" : "gender",<br/>' +
                    '          "fn" : "function(me, cmp, newValue, oldValue){if (me) me.setVisible(newValue == \'Female\');} "<br/>' +
                    '        }<br/>' +
                    '    }<br/>' +
                    '}</code>'
        });

        return Ext4.create('Ext.panel.Panel', {
            layout: {
                type             : 'accordion',
                titleCollapse    : true,
                animate          : true,
                hideCollapseTool : true
            },
            defaults: {
                collapsed : true,
                bodyPadding : 5,
                autoScroll  : true
            },
            frame   : false,
            region  : 'east',
            width   : 300,
            title   : 'Question Metadata Examples',
            collapsible : true,
            collapsed   : true,
            items   : items
        });
    }
});
