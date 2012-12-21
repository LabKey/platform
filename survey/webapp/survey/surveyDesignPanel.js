/*
 * Copyright (c) 2012 LabKey Corporation
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
            //border  : false,
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
                    values.metadata = this.codeMirror.getValue();

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
            name : 'schemaName',
            allowBlank : false
        }));
        items.push(model.makeQueryComboConfig({
            name : 'queryName',
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

        var id = Ext4.id();
        var formPanel = Ext4.create('Ext.form.Panel', {
            //border  : false,
            frame   : false,
            bodyPadding : 20,
            html : '<textarea rows="34" name="metadata" id="' + id + '"></textarea>',
            region  : 'center',
/*
            fieldDefaults  : {
                labelWidth : 100,
                width      : 375,
                style      : 'padding: 4px 0',
                labelSeparator : ''
            },
*/
            items   : items
        });
        formPanel.on('render', function(cmp){

            var code = Ext4.get(id);
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

    loadSurvey : function(survey) {

        var form = this.formPanel.getForm();
        if (form) {

            form.setValues(survey);
            this.codeMirror.setValue(survey.metadata);
        }
    }
});
