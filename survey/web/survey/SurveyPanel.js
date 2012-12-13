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

        Ext4.apply(config, {
            border: true,
            width: 770,
            layout: {
                type: 'hbox',
                pack: 'start',
                align: 'stretchmax'
            },
            items: []
        });

        this.callParent([config]);
    },

    initComponent : function() {

        if (this.surveyDesignId)
        {
            // query the DB for the survey design metadata from the given survey design ID
            Ext4.Ajax.request({
                url     : LABKEY.ActionURL.buildURL('survey', 'getSurveyTemplate.api'),
                method  : 'POST',
                jsonData: {rowId : this.surveyDesignId},
                success : function(resp){
                    var o = Ext4.decode(resp.responseText);

                    if (o.success)
                    {
                        var metadata = Ext4.JSON.decode(o.survey.metadata);
                        this.getSurveyLayout(metadata);
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

    getSurveyLayout : function(surveyConfig) {
        this.surveyLayout = 'auto';
        if (surveyConfig.survey && surveyConfig.survey.layout)
            this.surveyLayout = surveyConfig.survey.layout;
    },

    generateSurveySections : function(surveyConfig) {

        // TODO: what survey config validation is needed here? (verify survey, layout, sections, etc.)
        this.sections = [];

        this.addSurveyStartPanel(surveyConfig.survey.labelCaption);

        // add each of the survey sections as a panel to the sections array
        if (surveyConfig.survey)
        {
            Ext4.each(surveyConfig.survey.sections, function(section){
                var sectionPanel = Ext4.create('Ext.form.Panel', {
                    border: false,
                    header: this.surveyLayout == 'card' ? false :
                            (section.header != undefined ? section.header : true),
                    collapsed: section.collapsed != undefined ? section.collapsed : false,
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

                // for card layout, add the section title as a displayfield instead of a panel header
                if (this.surveyLayout == 'card' && section.title)
                {
                    var txt = Ext.DomHelper.markup({tag:'div', cls:'labkey-nav-page-header', html: section.title}) +
                            Ext.DomHelper.markup({tag:'div', html:'&nbsp;'});
                    sectionPanel.add({xtype:'displayfield', value: txt});
                }

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
                        }

                        // make the field label for required questions bold and end with an *
                        if (question.nullable != undefined && !question.nullable)
                        {
                            config.allowBlank = false;
                            config.labelStyle = "font-weight: bold;";
                            config.fieldLabel = config.fieldLabel + "*";
                        }

                        // TODO: any question customizations?

                        sectionPanel.add(config);
                    }
                }

                this.sections.push(sectionPanel);
            }, this);
        }

        this.addSurveyEndPanel();

        this.setSurveyLayout(surveyConfig);
    },

    addSurveyStartPanel : function(labelCaption) {

        // add an initial panel that has the survey label text field
        this.sections.push(Ext4.create('Ext.form.Panel', {
            title: 'Begin Survey',
            header: false,
            border: false,
            minHeight: 60,
            items: [{
                xtype: 'textfield',
                fieldLabel: (labelCaption || 'Survey Label') + '*',
                labelStyle: 'font-weight: bold;',
                labelWidth: 250,
                labelSeparator: '',
                padding: 10,
                allowBlank: false,
                width: 700
            }]
        }));
    },

    addSurveyEndPanel : function() {

        // add a final panel that has the Save/Submit buttons and required field checks
        // TODO: the layout of this panel will have to change when we add text about which required fields are missing
        this.sections.push(Ext4.create('Ext.panel.Panel', {
            title: 'End Survey',
            layout: {
                type: 'hbox',
                align: 'middle',
                pack: 'center'
            },
            header: false,
            border: false,
            minHeight: 100,
            items: [{
                xtype: 'button',
                text: 'Save',
                disabled: true,
                width: 150,
                height: 30
            },{
                xtype: 'label',
                width: 100,
                value: null
            },{
                xtype: 'button',
                text: 'Submit completed form',
                disabled: true,
                width: 250,
                height: 30
            }]
        }));
    },

    setSurveyLayout: function(surveyConfig) {

        this.currentStep = 0;

        // in card layout, we add a side bar with the section titles and next/previous buttons
        var bbar = [];
        if (this.surveyLayout == 'card')
        {
            this.width += 250;

            this.sideBar = Ext4.create('Ext.panel.Panel', {
                name: 'sidebar',
                width: 250,
                border: false,
                cls: 'extContainer',
                tpl: [
                    '<div class="labkey-ancillary-wizard-background">',
                    '<ol class="labkey-ancillary-wizard-steps">',
                    '<tpl for="steps">',

                        '<tpl if="values.currentStep == true">',
                        '<li class="labkey-ancillary-wizard-active-step">{value}</li>',
                        '</tpl>',

                        '<tpl if="values.currentStep == false">',
                        '<li>{value}</li>',
                        '</tpl>',

                    '</tpl>',
                    '</ol>',
                    '</div>'
                ],
                data: {steps: this.getStepsDataArr()}
            });
            this.add(this.sideBar);

            this.prevBtn = Ext4.create('Ext.button.Button', {
                text: 'Previous',
                disabled: true,
                scope: this,
                handler: function(cmp){
                    this.currentStep--;
                    this.updateStep();
            }});

            this.nextBtn = Ext4.create('Ext.button.Button', {
                text: 'Next',
                scope: this,
                handler: function(cmp){
                    this.currentStep++;
                    this.updateStep();
            }});
            bbar = ['->', this.prevBtn, this.nextBtn];
        }

        this.centerPanel = Ext4.create('Ext.panel.Panel', {
            layout: this.surveyLayout,
            border: false,
            minHeight: this.surveyLayout == 'card' ? 500 : undefined,
            bodyStyle : 'padding: 20px;',
            activeItem: 0,
            flex: 1,
            items: this.sections,
            bbar: bbar.length > 0 ? bbar : undefined
        });
        this.add(this.centerPanel);
    },

    getStepsDataArr : function() {
        var steps = [];
        for (var i = 0; i < this.sections.length; i++)
        {
            steps.push({value: this.sections[i].title, currentStep: i == this.currentStep});
        }
        return steps;
    },

    updateStep : function() {
        this.sideBar.update({steps: this.getStepsDataArr()});
        this.centerPanel.getLayout().setActiveItem(this.currentStep);

        this.prevBtn.setDisabled(this.currentStep == 0);
        this.nextBtn.setDisabled(this.currentStep == this.sections.length-1);
    },

    onFailure : function(resp){
        var error = Ext4.decode(resp.responseText).exception;
        if (error)
            Ext4.MessageBox.alert('Error', error);
        else
            Ext4.MessageBox.alert('Error', 'An unknown error has ocurred, unable to save the survey.');
    }
});