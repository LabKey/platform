/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

LABKEY.requiresExt4ClientAPI();
LABKEY.requiresScript("/extWidgets/Ext4Helper.js");

Ext4.define('LABKEY.ext4.SurveyPanel', {

    extend : 'Ext.panel.Panel',

    constructor : function(config){

        Ext4.applyIf(config, {
            border: true,
            bodyStyle: 'background-color: transparent;',
            width: 740,
            items: [],
            buttons: [],
            buttonAlign: 'center'
        });

        this.callParent([config]);
    },

    initComponent : function() {

        if (this.surveyDesignId)
        {
            Ext4.Ajax.request({
                url     : LABKEY.ActionURL.buildURL('survey', 'getSurveyTemplate.api'),
                method  : 'POST',
                jsonData: {rowId : this.surveyDesignId},
                success : function(resp){
                    var o = Ext4.decode(resp.responseText);

                    if (o.success)
                    {
                        var metadata = Ext4.JSON.decode(o.survey.metadata);
                        this.generateSurveySections(metadata);
                    }
                },
                failure : this.onFailure,
                scope   : this
            });
        }
        else
            this.hidden = true;

        this.callParent();
    },

    setSurveyLayout: function(layout) {

        // in card layout, we need to add previous/next buttons
        this.layout = layout;
        if (this.layout == 'card')
        {
            this.buttons.add({
                text: 'Previous',
                disabled: true
            });
            this.buttons.add({
                text: 'Next',
                disabled: true
            });
        }
    },

    generateSurveySections: function(surveyConfig) {

        // TODO: what survey config validation is needed here? (verify survey, layout, sections, etc.)

        this.setSurveyLayout(surveyConfig.survey.layout);

        // at the top level each item is a panel for a given survey section
        Ext4.each(surveyConfig.survey.sections, function(section){
            var sectionPanel = Ext4.create('Ext.form.Panel', {
                border: false,
                bodyStyle: 'background-color: transparent; padding: 5px;',
                header: section.header != undefined ? section.header : true,
                collapsible: section.collapsible != undefined ? section.collapsible : true,
                title: section.title || '',
                titleCollapse: true,
                defaults: {
                    labelWidth: section.defaultLabelWidth || 250,
                    labelSeparator: '',
                    padding: 10
                },
                items: []
            });

            // there is a section description, add it as the first item for that section
            if (section.description)
            {
                sectionPanel.add({
                    xtype: 'displayfield',
                    hideLabel: true,
                    value: section.description
                })
            }

            // each section can have a set of questions to be added using the FormHelper
            if (section.questions)
            {
                for (var i = 0; i < section.questions.length; i++)
                {
                    var question = section.questions[i];

                    // not currently using helpPopups for the survey question field labels
                    question.helpPopup = [];

                    var config;
                    if (question.extConfig)
                    {
                        // if the question is defined as an ext component, pass it through
                        config = question.extConfig;
                    }
                    else
                    {
                        // if the question is not defined as ext, use the column metadata form helper
                        config = LABKEY.ext.Ext4Helper.getFormEditorConfig(question);

                        // make the field label for required questions bold and end with an *
                        if (question.nullable != undefined && !question.nullable)
                        {
                            config.allowBlank = false;
                            config.labelStyle = "font-weight: bold;";
                            config.fieldLabel = config.fieldLabel + "*";
                        }

                        // TODO: any question customizations?
                    }

                    sectionPanel.add(config);
                }
            }

            this.add(sectionPanel);
        }, this);
    },

    onFailure : function(resp){
        var error = Ext4.decode(resp.responseText).exception;
        if(error){
            Ext.MessageBox.alert('Error', error);
        } else {
            Ext.MessageBox.alert('Error', 'An unknown error has ocurred, unable to save the chart.');
        }
    }
});