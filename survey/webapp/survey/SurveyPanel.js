/*
 * Copyright (c) 2012-2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

LABKEY.requiresExt4ClientAPI();
LABKEY.requiresScript("/extWidgets/Ext4Helper.js");
LABKEY.requiresScript("/survey/SurveyGridQuestion.js");
LABKEY.requiresScript("/survey/AttachmentField.js");
LABKEY.requiresCss("/survey/Survey.css");

/*
 * This is the outer panel that should be used if you want your survey to have a wiki header or footer.
 * It will create the LABKEY.ext4.SurveyPanel as one of its items.
 */
Ext4.define('LABKEY.ext4.SurveyDisplayPanel', {

    extend : 'Ext.panel.Panel',

    constructor : function(config){

        Ext4.apply(config, {
            border: false,
            bodyStyle : 'background-color: transparent;',
            autoHeight: true,
            items: []
        });

        this.callParent([config]);
    },

    initComponent : function() {

        // pass through all of the config items that are needed for the SurveyPanel
        this.surveyFormPanel = Ext4.create('LABKEY.ext4.SurveyPanel', {
            itemId          : 'SurveyFormPanel', // used by sidebar section click function
            cls             : this.cls,
            rowId           : this.rowId,
            surveyDesignId  : this.surveyDesignId,
            responsesPk     : this.responsesPk,
            surveyLabel     : this.surveyLabel,
            isSubmitted     : this.isSubmitted,
            canEdit         : this.canEdit,
            returnURL       : this.returnURL,
            autosaveInterval: 60000
        });
        this.items = [this.surveyFormPanel];

        this.callParent();

        this.configureHeaderAndFooter();
    },

    configureHeaderAndFooter : function() {

        // if we have a saved surveyDesign, look it up to see if there is a header/footer wiki to add
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
                        if (metadata.survey && metadata.survey.headerWiki)
                        {
                            var headerWiki = metadata.survey.headerWiki;
                            if (!headerWiki.name)
                            {
                                //display error message for header wiki name missing
                                Ext4.get(this.headerRenderTo).update("<span class='labkey-error'>Error: This survey design has a header wiki, but the wiki name is missing from the config.<br/><br/></span>")
                            }
                            else
                            {
                                new LABKEY.WebPart({
                               		partName: 'Wiki',
                                    frame: 'none',
                               		renderTo: this.headerRenderTo,
                                    containerPath: headerWiki.containerPath || LABKEY.container.path,
                               		partConfig: {
                                           name: headerWiki.name
                                    }
                                }).render();
                            }

                            var footerWiki = metadata.survey.footerWiki;
                            if (!footerWiki.name)
                            {
                                //display error message for footer wiki name missing
                                Ext4.get(this.footerRenderTo).update("<span class='labkey-error'><br/>Error: This survey design has a footer wiki, but the wiki name is missing from the config.</span>")
                            }
                            else
                            {
                                new LABKEY.WebPart({
                               		partName: 'Wiki',
                                    frame: 'none',
                               		renderTo: this.footerRenderTo,
                                    containerPath: footerWiki.containerPath || LABKEY.container.path,
                               		partConfig: {
                                           name: footerWiki.name
                                    }
                                }).render();
                            }
                        }
                    }
                },
                failure : this.onFailure,
                scope   : this
            });
        }
    }
});

Ext4.define('LABKEY.ext4.SurveyPanel', {

    extend : 'Ext.form.Panel',

    constructor : function(config){

        Ext4.apply(config, {
            border: true,
            width: 870,
            minHeight: 25,
            layout: {
                type: 'hbox',
                pack: 'start',
                align: 'stretchmax'
            },
            trackResetOnLoad: true,
            items: [],
            sections: [],
            validStatus: {},
            changeHandlers: {},
            listeners: {
                scope: this,
                afterrender: function() {
                    if (this.sections && this.sections.length == 0)
                        this.setLoading(true);
                }
            }
        });

        this.callParent([config]);

        // add the listener for when to enable/disable the submit button
        this.addListener('validitychange', this.toggleSubmitBtn, this);

        // add listener for when to enable/disable the save button based on the form dirty state
        this.addListener('dirtychange', function(cmp, isDirty){
            // only toggle the save button if the survey label field is valid
            if (this.down('.textfield[itemId=surveyLabel]').isValid())
                this.toggleSaveBtn(isDirty, false);
        }, this);

        // add a delayed task for automatically saving the survey responses
        if (this.canEdit)
        {
            var autoSaveFn = function(count){
                // without btn/event arguments so we don't show the success msg
                this.saveSurvey(null, null, false);
            };
            this.autoSaveTask = Ext4.TaskManager.start({
                run: autoSaveFn,
                interval: this.autosaveInterval || 60000, // default is 1 min
                scope: this
            });

            // check dirty state on page navigation
            window.onbeforeunload = LABKEY.beforeunload(this.isSurveyDirty, this);
        }
    },

    initComponent : function() {

        this.getSurveyResponses();

        this.callParent();
    },

    getSurveyResponses : function() {
        this.rowMap = {};
        this.initialResponses = {};

        if (this.rowId)
        {
            // query the DB for the survey responses for the given rowId
            Ext4.Ajax.request({
                url     : LABKEY.ActionURL.buildURL('survey', 'getSurveyResponse.api'),
                method  : 'POST',
                jsonData: {rowId : this.rowId},
                success : function(resp){
                    var o = Ext4.decode(resp.responseText);

                    if (o.rowCount && o.rowCount === 1)
                    {
                        // save the raw row and process the entries so we can initialize the form
                        this.rowMap = o.rows[0];
                        Ext4.Object.each(this.rowMap, function(key, value){
                            var val = value.value;

                            // make sure to parse dates accordingly, as they come in as strings but datefield.setValue doesn't like that
                            if (Ext4.Date.parse(value.value, "Y/m/d H:i:s", true))
                                val = Ext4.Date.parse(value.value, "Y/m/d H:i:s", true);

                            this.initialResponses[key] = val;
                        }, this);

                        this.getSurveyDesign();
                        this.setSubmittedByInfo(o);
                    }
                    else
                        this.onFailure(resp, "The survey responses for rowId " + this.rowId + " could not be found and may have been deleted.", true);
                },
                failure : function(resp) { this.onFailure(resp, null, true); },
                scope   : this
            });
        }
        else
            this.getSurveyDesign();
    },

    getSurveyDesign : function() {

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
                        this.setLabelCaption(metadata);
                        this.setShowCounts(metadata);
                        this.setSurveyLayout(metadata);
                        this.generateSurveySections(metadata);
                    }
                    else
                        this.onFailure(resp);
                },
                failure : this.onFailure,
                scope   : this
            });
        }
        else
            this.hidden = true;
    },

    setSubmittedByInfo : function(responseConfig) {
        this.submitted = responseConfig.submitted ? responseConfig.submitted : null;
        this.submittedBy = responseConfig.submittedBy ? responseConfig.submittedBy : null;
    },

    setLabelCaption : function(surveyConfig) {
        this.labelCaption = 'Survey Label';
        if (surveyConfig.survey && surveyConfig.survey.labelCaption)
            this.labelCaption = surveyConfig.survey.labelCaption;
    },

    setShowCounts : function(surveyConfig) {
        this.showCounts = false;
        if (surveyConfig.survey && surveyConfig.survey.showCounts)
            this.showCounts = surveyConfig.survey.showCounts;
    },

    setSurveyLayout : function(surveyConfig) {
        this.surveyLayout = 'auto';
        if (surveyConfig.survey && surveyConfig.survey.layout)
            this.surveyLayout = surveyConfig.survey.layout;
    },

    questionChangeHandler : function(cmp, newValue, oldValue) {

        // if there is a handler mapped to the component
        var changeHandlers = this.changeHandlers[cmp.getName()];
        if (Ext4.isArray(changeHandlers))
        {
            var values = this.getForm().getValues();

            for (var i=0; i < changeHandlers.length; i++)
            {
                var info = changeHandlers[i];
                var me = this.down('#item-' + info.name);
                if (me)
                {
                    var changeFn = info.fn;

                    changeFn().call(this, me, cmp, newValue, oldValue, values);

                    this.clearHiddenFieldValues(me);
                }
            }
        }

        if (this.showCounts)
            this.udpateSectionCount(cmp, newValue, oldValue);
    },

    udpateSectionCount : function(cmp, newValue, oldValue) {

        var sectionPanel = cmp.up('.panel[sectionPanel=true]');
        if (sectionPanel)
        {
            var changed = false;

            // special case for checkbox fields since the value is never null (i.e. true or false)
            if (cmp.getXType() == "checkboxfield")
            {
                sectionPanel.completedQuestions = sectionPanel.completedQuestions + (newValue ? 1 : -1);
                changed = true;
            }
            // special case for any custom question types
            else if (cmp.getXType() == "surveygridquestion")
            {
                if (newValue > 0 && oldValue == 0)
                {
                    sectionPanel.completedQuestions++;
                    changed = true;
                }
                else if (newValue == 0 && oldValue > 0)
                {
                    sectionPanel.completedQuestions--;
                    changed = true;
                }
            }
            else if ((oldValue == null || oldValue.toString().length == 0) && (newValue != null && newValue.toString().length > 0))
            {
                sectionPanel.completedQuestions++;
                changed = true;
            }
            else if ((newValue == null || newValue.toString().length == 0) && (oldValue != null && oldValue.toString().length > 0))
            {
                sectionPanel.completedQuestions--;
                changed = true;
            }

            if (changed)
            {
                sectionPanel.setTitle(sectionPanel.origTitle + (sectionPanel.completedQuestions > 0 ? " (" + sectionPanel.completedQuestions + ")" : ""));

                // if we are in card layout, update the side bar titles
                if (this.sideBar)
                    this.sideBar.update({steps: this.getStepsDataArr()});
            }
        }
    },

    clearHiddenFieldValues : function(cmp) {
        if (cmp.isHidden())
        {
            // the component can either be a form field itself or a container that has multiple fields
            if (cmp.isFormField)
                this.clearFieldValue(cmp);
            else
                Ext4.each(cmp.query('field'), this.clearFieldValue, this);
        }
    },

    clearFieldValue : function(field) {
        // only "reset" form fields that are not displayfields
        if (field && field.isFormField && field.getXType() != 'displayfield')
        {
            if (field.clearValue != undefined)
                field.clearValue();
            else
                field.setValue(null);
        }
    },

    generateSurveySections : function(surveyConfig) {

        this.addSurveyStartPanel();

        // add each of the survey sections as a panel to the sections array
        if (surveyConfig.survey)
        {
            Ext4.each(surveyConfig.survey.sections, function(section){
                var sectionPanel = Ext4.create('Ext.panel.Panel', {
                    border: false,
                    header: this.surveyLayout == 'card' ? false : (section.header != undefined ? section.header : true),
                    sectionPanel: true, // marker for looking for a components parent section
                    completedQuestions: 0, // counter for the section header to show progress when a panel is collapsed
                    origTitle: section.title || '',
                    title: section.title || '',
                    defaults: {
                        labelWidth: section.defaultLabelWidth || 350,
                        labelSeparator: '',
                        padding: 10
                    },
                    items: []
                });

                // for card layout, add the section title as a displayfield instead of a panel header
                if (this.surveyLayout == 'card' && section.title)
                {
                    sectionPanel.add(this.getCardSectionHeader(section.title));
                }
                else
                {
                    sectionPanel.collapsed = section.collapsed != undefined ? section.collapsed : false;
                    sectionPanel.collapsible = section.collapsible != undefined ? section.collapsible : true;
                    sectionPanel.titleCollapse = true;
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

                        // we don't want to apply the hidden state until after the config is created
                        var hidden = question.hidden;
                        question.hidden = false;

                        var config;
                        if (question.extConfig)
                        {
                            // if the question is defined as an ext component, pass it through
                            config = question.extConfig;
                        }
                        else
                        {
                            // the labkey formhelper doesn't support file upload fields, so we'll step in here and
                            // add one manually
                            if (question.inputType == 'file')
                            {
                                var attachment = undefined;
                                var entry = this.rowMap[question.name];

                                if (entry && entry.value)
                                {
                                    attachment = {};
                                    attachment.icon = LABKEY.Utils.getFileIconUrl(entry.value);
                                    attachment.name = entry.value;
                                    attachment.downloadURL = entry.url;
                                }

                                config = {
                                    fieldLabel : question.caption,
                                    attachment : attachment,
                                    name : question.name,
                                    fieldWidth : 445,
                                    xtype : 'attachmentfield'
                                };
                            }
                            else
                            {
                                // if the question is not defined as ext, use the column metadata form helper
                                config = LABKEY.ext.Ext4Helper.getFormEditorConfig(question);
                            }
                        }

                        // survey specific question configurations (required field display, etc.)
                        config = this.customizeQuestionConfig(question, config, hidden);

                        // add a component id for retrieval
                        if (config.name && !config.itemId)
                            config.itemId = 'item-' + config.name;

                        // register any configured listeners
                        // Note: we allow for an array of listeners OR an array of question names to apply a single listener to
                        var listeners = question.listeners || {};
                        if (listeners.change)
                        {
                            if (!(listeners.change instanceof Array))
                                listeners.change = [listeners.change];

                            for (var listenerIndex = 0; listenerIndex < listeners.change.length; listenerIndex++)
                            {
                                var listenerFn = listeners.change[listenerIndex].fn;
                                var listenerNames;
                                if (listeners.change[listenerIndex].question instanceof Array)
                                    listenerNames = listeners.change[listenerIndex].question;
                                else
                                    listenerNames = [listeners.change[listenerIndex].question];

                                for (var qnameIndex = 0; qnameIndex < listenerNames.length; qnameIndex++)
                                {
                                    var handlers = this.changeHandlers[listenerNames[qnameIndex]] || [];
                                    var changeFn = new Function('', "return " + listenerFn);

                                    handlers.push({name : config.name, fn : changeFn});
                                    this.changeHandlers[listenerNames[qnameIndex]] = handlers;
                                }
                            }
                        }

                        sectionPanel.add(config);
                    }
                }

                this.sections.push(sectionPanel);
            }, this);
        }

        this.addSurveyEndPanel();

        this.configureSurveyLayout(surveyConfig);

        this.configureFieldListeners();

        // if we have an existing survey record, initialize the fields based on the surveyResults
        if (this.initialResponses != null)
            this.getForm().setValues(this.initialResponses);

        this.clearLoadingMask();
    },

    configureFieldListeners : function() {

        Ext4.each(this.getForm().getFields().items, function(field){

            // add a global change listener for all questions so we can map them to any change handlers specified in the survey
            field.addListener('change', this.questionChangeHandler, this);

            // if the user can not edit this survey (i.e. submitted and non-admin), make the field readOnly
            if (!this.canEdit)
            {
                field.setReadOnly(true);
            }
            else
            {
                // add a validity listener to each question
                if (field.submitValue)
                {
                    this.validStatus[field.getName()] = field.isValid();
                    field.clearInvalid();
                    field.addListener('validitychange', this.fieldValidityChanged, this);
                }
            }

        }, this);
    },

    customizeQuestionConfig : function(question, config, hidden) {
        // make the field label for required questions bold and end with an *
        if (question.required != undefined && question.required)
        {
            config.allowBlank = false;
            config.labelStyle = "font-weight: bold;";
            config.fieldLabel = config.fieldLabel + "*";
        }

        // customize the checkbox config to make sure unchecked values get submitted
        if (question.inputType != undefined && question.inputType == 'checkbox')
        {
            config.inputValue = 'true';
            config.uncheckedValue = 'false';
        }

        // set the date field format
        if (config.xtype == 'datefield')
        {
            config.format = question.format ? question.format : "Y-m-d";
        }

        // if the question has a description, append it to the field label
        if (question.description)
            config.fieldLabel += "<br/>" + question.description;

        // if hidden, apply that to the config
        if (hidden != undefined && hidden)
            config.hidden = true;

        // if the question has a short caption (used in the submit button disabled info), make sure it is applied
        if (question.shortCaption)
            config.shortCaption = question.shortCaption;

        // if the user can not edit this survey (i.e. submitted and non-admin), make the field readOnly
        if (!this.canEdit)
            config.readOnly = true;

        return config;
    },

    getCardSectionHeader : function(title) {
        var txt = Ext.DomHelper.markup({tag:'div', cls:'labkey-nav-page-header', html: title}) +
                Ext.DomHelper.markup({tag:'div', html:'&nbsp;'});
        return {xtype:'displayfield', value: txt};
    },

    addSurveyStartPanel : function() {

        // add an initial panel that has the survey label text field
        var title = 'Begin Survey';
        var items = [];

        if (this.surveyLayout == 'card')
            items.push(this.getCardSectionHeader(title));

        items.push({
            xtype: 'textfield',
            name: '_surveyLabel_', // for selenium testing
            itemId: 'surveyLabel',
            value: this.surveyLabel,
            submitValue: false, // this field applies to the surveys table not the responses
            fieldLabel: this.labelCaption + '*',
            labelStyle: 'font-weight: bold;',
            labelWidth: 350,
            labelSeparator: '',
            padding: 10,
            allowBlank: false,
            readOnly: !this.canEdit,
            width: 800,
            listeners: {
                scope: this,
                change: function(cmp, newValue) {
                    this.surveyLabel = newValue == null || newValue.length == 0 ? null : newValue;
                },
                validitychange: function(cmp, isValid) {
                    // this is the only form field that is required before the survey can be saved
                    this.toggleSaveBtn(isValid, true);
                }
            }
        });

        this.sections.push(Ext4.create('Ext.panel.Panel', {
            title: title,
            header: false,
            border: false,
            minHeight: 60,
            items: items
        }));
    },

    addSurveyEndPanel : function() {

        // add a final panel that has the Save/Submit buttons and required field checks
        this.updateSubmittedInfo = Ext4.create('Ext.form.DisplayField', {
            hideLabel: true,
            width: 250,
            style: "text-align: center;",
            hidden: !this.isSubmitted,
            value: "<span style='font-style: italic; font-size: 90%'>"
                    + (this.submitted && this.submittedBy ? "This survey was submitted by " + this.submittedBy + " on " + this.submitted + ".<br/><br/>" : "")
                    + (this.canEdit ? "You are allowed to make changes to this form because you are a project/site administrator.<br/><br/>" : "")
                    + "</span>"
        });

        this.saveBtn = Ext4.create('Ext.button.Button', {
            text: 'Save',
            disabled: true,
            width: 150,
            height: 30,
            scope: this,
            handler: function(btn, evt) {
                this.saveSurvey(btn, evt, false);
            }
        });

        this.saveDisabledInfo = Ext4.create('Ext.form.DisplayField', {
            hideLabel: true,
            width: 250,
            style: "text-align: center;",
            hidden: this.surveyLabel != null,
            value: "<span style='font-style: italic; font-size: 90%'>Note: The '" + this.labelCaption + "' field at the"
                + " beginning of the form must be filled in before you can save the form*.</span>"
        });

        this.autosaveInfo = Ext4.create('Ext.container.Container', {
            hideLabel: true,
            width: 150,
            style: "text-align: center;"
        });

        this.submitBtn = Ext4.create('Ext.button.Button', {
            text: 'Submit completed form',
            formBind: true,
            width: 250,
            height: 30,
            scope: this,
            handler: this.submitSurvey
        });

        this.submitInfo = Ext4.create('Ext.container.Container', {
            hideLabel: true,
            width: 250,
            style: "text-align: center;"
        });

        // the set of buttons and text on the end survey page changes based on whether or not the survey was submitted
        // and whether or not the user can edit a submitted survey (i.e. is project/site admin)
        var items = [{
            xtype: 'panel',
            layout: {type: 'vbox', align: 'center'},
            items: this.canEdit
                    ? [this.updateSubmittedInfo, this.saveBtn, this.saveDisabledInfo, this.autosaveInfo]
                    : [this.updateSubmittedInfo]
        }];
        if (this.canEdit && !this.isSubmitted)
        {
            items.push({xtype: 'label', width: 100, value: null});
            items.push({
                xtype: 'panel',
                layout: {type: 'vbox', align: 'center'},
                items: [this.submitBtn, this.submitInfo]
            });
        }

        this.sections.push(Ext4.create('Ext.panel.Panel', {
            title: 'End Survey',
            layout: {
                type: 'hbox',
                align: 'top',
                pack: 'center'
            },
            header: false,
            border: false,
            bodyStyle: 'padding-top: 25px;',
            defaults: { border: false, width: 250 },
            items: items
        }));
    },

    configureSurveyLayout : function(surveyConfig) {

        this.currentStep = 0;

        // in card layout, we add a side bar with the section titles and next/previous buttons
        var bbar = [];
        if (this.surveyLayout == 'card')
        {
            this.width += 250;

            // define function to be called on click of a sidebar section title
            window.surveySidebarSectionClick = function(step){
                // need to use component query to get back at the surveyFormPanel object
                var panels = Ext4.ComponentQuery.query('#SurveyFormPanel');
                if (panels.length == 1)
                {
                    panels[0].currentStep = step;
                    panels[0].updateStep();
                }
            };

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
                        '<li onclick="surveySidebarSectionClick({step});" class="labkey-side-bar-title">{value}</li>',
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

        this.updateSubmitInfo();
    },

    getStepsDataArr : function() {
        var steps = [];
        for (var i = 0; i < this.sections.length; i++)
        {
            steps.push({
                value: this.sections[i].title,
                step: i,
                currentStep: i == this.currentStep
            });
        }
        return steps;
    },

    updateStep : function() {
        this.sideBar.update({steps: this.getStepsDataArr()});
        this.centerPanel.getLayout().setActiveItem(this.currentStep);

        this.prevBtn.setDisabled(this.currentStep == 0);
        this.nextBtn.setDisabled(this.currentStep == this.sections.length-1);
    },

    saveSurvey : function(btn, evt, toSubmit) {
        //console.log('Attempting save at ' + new Date().format('g:i:s A'));

        // check to see if there is anything to be saved (or submitted)
        if (!this.isSurveyDirty() && !toSubmit)
            return;

        this.toggleSaveBtn(false, false);

        // get the dirty form values which are also valid and to be submitted
        this.submitValues = {};
        Ext4.each(this.getForm().getFields().items, function(field){
            if (field.submitValue && field.isDirty() && field.isValid())
                this.submitValues[field.getName()] = field.getSubmitValue();
        }, this);

        // check to make sure the survey label is not null, it is required
        if (!this.surveyLabel)
        {
            //Ext4.MessageBox.alert('Error', 'The ' + this.labelCaption + ' is requried.');
            return;
        }

        // send the survey rowId, surveyDesignId, and responsesPk as params to the API call
        Ext4.Ajax.request({
            url     : LABKEY.ActionURL.buildURL('survey', 'updateSurveyResponse.api'),
            method  : 'POST',
            jsonData: {
                surveyDesignId : this.surveyDesignId,
                rowId          : this.rowId ? this.rowId : undefined,
                responsesPk    : this.responsesPk ? this.responsesPk : undefined,
                label          : this.surveyLabel,
                responses      : this.submitValues,
                submit         : toSubmit
            },
            success : function(resp){
                var o = Ext4.decode(resp.responseText);
                if (o.success)
                {
                    // store the survey rowId and responsesPk, for new entries
                    if (o.survey["rowId"])
                        this.rowId = o.survey["rowId"];
                    if (o.survey["responsesPk"])
                        this.responsesPk = o.survey["responsesPk"];

                    // save any attachments added to this survey
                    this.saveSurveyAttachments();

                    // reset the values so that the form's dirty state is cleared, with one special case for the survey label field
                    this.submitValues[this.down('.textfield[itemId=surveyLabel]').getName()] = this.down('.textfield[itemId=surveyLabel]').getValue();
                    this.getForm().setValues(this.submitValues);

                    var msgBox = Ext4.create('Ext.window.Window', {
                        title    : 'Success',
                        html     : toSubmit ? '<span class="labkey-message">This form has been successfully submitted.</span>'
                                        : '<span class="labkey-message">Changes saved successfully.</span>',
                        modal    : true,
                        closable : false,
                        bodyStyle: 'padding: 20px;'
                    });

                    if (toSubmit)
                    {
                        // since the user clicked the submit button, navigate back to the srcUrl
                        msgBox.show();
                        this.closeMsgBox = new Ext4.util.DelayedTask(function(){
                            msgBox.hide();

                            if (this.returnURL)
                                window.location = this.returnURL;
                            else
                                window.history.back();
                        }, this);
                        this.closeMsgBox.delay(2500);

                        this.autosaveInfo.update("");
                    }
                    else if (btn != null)
                    {
                        // since the user clicked the save button, give them an indication that the save was successful
                        msgBox.show();
                        this.closeMsgBox = new Ext4.util.DelayedTask(function(){ msgBox.hide(); }, this);
                        this.closeMsgBox.delay(2000);

                        this.autosaveInfo.update("");
                    }
                    else
                    {
                        // if no btn param, the save was done via the auto save task
                        this.autosaveInfo.update("<span style='font-style: italic; font-size: 90%'>Responses automatically saved at " + new Date().format('g:i:s A') + "</span>");
                    }
                }
                else
                    this.onFailure(resp);
            },
            failure : this.onFailure,
            scope   : this
        });
    },

    submitSurvey : function() {
        // call the save function with the toSubmit parameter as true
        this.saveSurvey(null, null, true);
    },

    toggleSaveBtn : function(enable, toggleMsg) {

        this.saveBtn.setDisabled(!enable);

        if (toggleMsg)
            enable ? this.saveDisabledInfo.hide() : this.saveDisabledInfo.show();
    },

    toggleSubmitBtn : function(form, isValid) {
        this.submitBtn.setDisabled(!isValid);
        this.updateSubmitInfo();
    },

    fieldValidityChanged : function(cmp, isValid) {
        this.validStatus[cmp.getName()] = isValid;
        this.updateSubmitInfo();
    },

    updateSubmitInfo : function() {
        var msg = "";
        for (var name in this.validStatus)
        {
            if (!this.validStatus[name])
            {
                var cmp = this.down('[name=' + name + ']');
                if (cmp)
                {
                    msg += "-" + (cmp.shortCaption ? cmp.shortCaption : name) + "<br/>";
                }
            }
        }

        if (msg.length > 0)
        {
            msg = "<span style='font-style: italic; font-size: 90%'>"
                + "Note: The following fields must be valid before you can submit the form*:<br/>"
                + msg + "</span>";
        }

        this.submitInfo.update(msg);
    },

    isSurveyDirty : function() {
        return this.getForm().isDirty();
    },

    onFailure : function(resp, message, hidePanel) {
        var error = Ext4.decode(resp.responseText);
        if (error.exception)
            message = error.exception;
        else if (error.errorInfo)
            message = error.errorInfo;

        if (hidePanel)
            this.update("<span class='labkey-error'>" + message + "</span>");
        else
            Ext4.MessageBox.alert('Error', message != null ? message : 'An unknown error has ocurred.');

        this.clearLoadingMask();

        if (this.isSurveyDirty() && this.down('.textfield[itemId=surveyLabel]').isValid())
            this.toggleSaveBtn(true, false);
    },

    saveSurveyAttachments : function() {

        // component query by xtype
        var fields = Ext4.ComponentQuery.query('attachmentfield', this);

        if (Ext4.isArray(fields) && fields.length > 0)
        {
            for (var i=0; i < fields.length; i++)
            {
                var cmp = fields[i];
                var form = cmp.getFormPanel().getForm();

                if (form.hasUpload() && form.isDirty())
                {
                    var options = {
                        method  :'POST',
                        form    : form,
                        params  : {rowId : this.rowId, questionName : cmp.name},
                        url     : LABKEY.ActionURL.buildURL('survey', 'updateSurveyResponseAttachments.api'),
                        success : function() {

                            // need to refresh the component
                        },
                        failure : LABKEY.Utils.displayAjaxErrorResponse,
                        scope : this
                    };
                    form.doAction(new Ext4.form.action.Submit(options));
                }
            }
        }
    },

    clearLoadingMask: function() {
        this.setLoading(false);
    }
});