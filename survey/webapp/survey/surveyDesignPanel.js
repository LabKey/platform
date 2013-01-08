/*
 * Copyright (c) 2012-2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

LABKEY.requiresScript("SQVSelector.js");

Ext4.define('LABKEY.ext4.SurveyDesignPanel', {

    extend : 'Ext.panel.Panel',

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

                if (form.isValid()) {

                    var values = form.getValues();

                    // hack: since we are not able to update the CodeMirror input field via selenium, we reshow the
                    // textarea and enter the value there, so check to see if the metadata textarea has a value first
                    var metadata = Ext4.get(this.metadataId).dom.value;
                    values.metadata = metadata != null && metadata.length > 0 ? metadata :this.codeMirror.getValue();

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
                                Ext.MessageBox.alert('Error', o.errorInfo.message);

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

        var model = Ext4.create('LABKEY.SQVModel', {});
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

        items.push(model.makeSchemaComboConfig({
            name    : 'schemaName',
            itemId  : 'schemaCombo',
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
            //border  : false,
            frame   : false,
            region  : 'west',
            bodyPadding : 20,
            collapsible : true,
            fieldDefaults  : {
                labelWidth : 100,
                width      : 325,
                style      : 'padding: 4px 0',
                labelSeparator : ''
            },
            items   : items
        });

        return this.formPanel;
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
            //border  : false,
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
            }
        }, this);

        return formPanel;
    },

    onFailure : function(resp){
        var o = Ext4.decode(resp.responseText);

        var error = o.exception;
        if(error)
            Ext.MessageBox.alert('Error', error);
        else
            Ext.MessageBox.alert('Error', 'An unknown error has ocurred, unable to save the survey.');
    },

    initCombo : function(cmp, value) {

        if (cmp.getStore().getCount() == 0)
        {
            this.initCombo.defer(20, this, [cmp, value]);
            return;
        }
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
                    '        "keyColumn": "Key",<br/>' +
                    '        "schema": "lists",<br/>' +
                    '        "displayColumn": "Value",<br/>' +
                    '        "schemaName": "lists",<br/>' +
                    '        "queryName": "lookup1",<br/>' +
                    '        "table": "lookup1",<br/>' +
                    '        "isPublic": true,<br/>' +
                    '        "public": true<br/>' +
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
                    '        "name": "cb_extjs",<br/>' +
                    '        "fieldLabel": "CB (ExtJS)",<br/>' +
                    '        "queryMode": "local",<br/>' +
                    '        "displayField": "value",<br/>' +
                    '        "valueField": "value",<br/>' +
                    '        "emptyText": "Select...",<br/>' +
                    '        "forceSelection": true,<br/>' +
                    '        "store": {<br/>' +
                    '            "fields": ["value"],<br/>' +
                    '            "data" : [<br/>' +
                    '                {"value": "Yes"},<br/>' +
                    '                {"value": "No"}<br/>' +
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
                    '          "question" : "cb_extjs",<br/>' +
                    '          "fn" : "function(me, cmp, newValue, oldValue){if (me) me.setVisible(newValue == \'Yes\');} "<br/>' +
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
            title   : 'Question Examples',
            collapsible : true,
            collapsed   : true,
            items   : items
        });
    }
});
