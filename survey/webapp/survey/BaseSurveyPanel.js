/*
 * Copyright (c) 2013-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.define('LABKEY.ext4.BaseSurveyPanel', {

    extend : 'Ext.form.Panel',

    constructor : function(config){
        Ext4.applyIf(config, {
            forceLowerCaseNames  : true
        });
        this.callParent([config]);
    },

    initComponent : function() {
        this.callParent();
    },

    generateSurveySections : function(surveyConfig, panelType) {

        this.requiredFieldNames = [];

        // default to a normal ext panel
        if (!panelType)
            panelType = 'Ext.panel.Panel';

        // add each of the sections as a panel to the sections array
        if (surveyConfig.survey)
        {
            Ext4.each(surveyConfig.survey.sections, function(section) {
                var sectionPanel = this.generateSectionPanel(section, panelType, false, section.layoutHorizontal ? section.layoutHorizontal : false);

                this.sections.push(sectionPanel);
            }, this);
        }
    },

    generateSectionPanel: function(section, panelType, isSubSection, layoutHorizontal) {
        var sectionPanel = Ext4.create(panelType, {
            flex: 1,
            autoScroll: true,
            sectionPanel: true, // marker for looking for a components parent section
            completedQuestions: 0, // counter for the section header to show progress when a panel is collapsed
            origTitle: section.title || '',
            title: section.title || '',
            copyFromPrevious: section.copyFromPrevious || false,
            isDisabled: section.initDisabled || false,
            dockedItems: (!section.toolbarButton) ? null : [{
                xtype: 'toolbar',
                dock: 'bottom',
                items: [{
                    text: section.toolbarButton,
                    scope: this,
                    style: {
                        'font-weight': 'bold'
                    },
                    name: section.toolbarButtonName,
                    tooltip: section.toolbarButtonTooltip
                }]
            }],
            margin: '1 0 2 0',
            items: [],
            listeners: {
                scope: this,
                activate : function(cmp) {
                    // ensure the window is scrolled into view
                    window.scrollTo(0,0);
                }
            }
        });

        if (layoutHorizontal)
        {
            sectionPanel.layout = {
                type: 'table',
                align: 'top',
                flex: 1,
                manageOverflow: 1,
                reserveScrollbar: true,
                itemCls: 'survey-table-cell',
                columns: (section.numColumns ? section.numColumns : section.questions.length) + (section.allowDelete ? 1 : 0),
                tableattrs: {
                    style: {}
                }
            };
            sectionPanel.defaults = {
                labelAlign: 'top',
                labelSeparator: '',
                padding: section.padding || 1
            };
            sectionPanel.border = section.border != undefined ? section.border : 1;
            sectionPanel.hasHeadingRow = section.hasHeadingRow;
        }
        else
        {
            sectionPanel.defaults = {
                labelWidth: section.defaultLabelWidth || 350,
                labelSeparator: '',
                padding: section.padding || 10
            };
            sectionPanel.border = section.border != undefined ? section.border : 0;
        }

        if (section.toolbarButtonHandlerKey)
        {
            var buttonConfig = sectionPanel.dockedItems.get(0).items.get(0);
            buttonConfig.handler = function(btn, evt) {
                if (this.buttonHandler) this.buttonHandler(section, btn);
            };
        }

        // for card layout, add the section title as a displayfield instead of a panel header
        if (this.surveyLayout == 'card' && !isSubSection)
        {
            sectionPanel.header = false;
            if (section.title)
                sectionPanel.add(this.getCardSectionHeader(section.title, section.subTitle));
        }
        else if (isSubSection)
        {
            sectionPanel.header = section.header != undefined ? section.header : false;
        }
        else
        {
            sectionPanel.header = section.header != undefined ? section.header : true;
        }

        if (this.surveyLayout != 'card' || !section.title || isSubSection)
        {
            sectionPanel.collapsed = section.collapsed != undefined ? section.collapsed : false;
            if (section.collapsible == undefined)
                sectionPanel.collapsible = true;
            else
                sectionPanel.collapsible = section.collapsible;
            sectionPanel.titleCollapse = true;
        }

        if (section.hidden)
            sectionPanel.hidden = true;
        if (section.dontClearDirtyFieldWhenHiding)
            sectionPanel.dontClearDirtyFieldWhenHiding = true;

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
                if (section.allowDelete && section.numColumns)
                {
                    // If there are columns and allowDelete is true, add a check box on the left of each row
                    if (0 == i % section.numColumns)
                    {
                        sectionPanel.add(this.createDeleteCheckbox(section, i / section.numColumns));
                    }
                }
                var question = section.questions[i];

                var config;
                if (question.subSection)
                {
                    config = this.generateSectionPanel(question, 'Ext.panel.Panel', true, question.layoutHorizontal ? question.layoutHorizontal : false);
                    config.name = question.name;
                    this.doSharedQuestion(question, config);        // SubSections may have listeners
                }
                else
                {
                    config = this.generateQuestion(question, section.title);
                }
                sectionPanel.add(config);
            }
        }
        // sections can be defined as an extAlias
        else if (section.extAlias)
        {
            sectionPanel.add({
                xtype: section.extAlias,
                isSubmitted: this.isSubmitted,
                canEdit: this.canEdit,
                listeners: {
                    // allow custom section panels to fire save events back to the parent
                    saveSurvey: function(successUrl, idParamName) {
                        this.saveSurvey(null, null, false, successUrl, idParamName);
                    },
                    scope: this
                }
            });
        }
        return sectionPanel;
    },

    generateQuestion: function(question, title) {
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
                config = LABKEY.ext4.Util.getFormEditorConfig(question);
            }
        }

        // survey specific question configurations (required field display, etc.)
        config = this.customizeQuestionConfig(question, config, hidden);

        // add the section title to each config (to test for disabled sections)
        config.sectionTitle = title;
        this.doSharedQuestion(question, config);

        // allow section panels to fire save events back to the parent
        config.listeners = {
            scope : this,
            saveSurvey : function(successUrl, idParamName) {
                this.saveSurvey(null, null, false, successUrl, idParamName);
            }
        };
        return config;
    },

    customizeQuestionConfig : function(question, config, hidden) {
        // make the field label for required questions bold and end with an *
        if (question.required != undefined && question.required)
        {
            config.allowBlank = false;
            config.origFieldLabel = config.fieldLabel;
            // only append * if there is some text in the field label
            config.reqFieldLabel = (config.fieldLabel && Ext4.util.Format.trim(config.fieldLabel).length > 0) ? "<span style='font-weight: bold;'>" + config.fieldLabel + "*</span>" : config.fieldLabel;
            config.fieldLabel = config.reqFieldLabel;

            if (config.name)
                this.requiredFieldNames.push(config.name);
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

        // make the name lowercase for consistency
        if (this.forceLowerCaseNames)
            this.convertNamesToLowerCase(config);

        // apply lookup filter for non-admins in edit mode (currently only supports "ISBLANK" filter type)
        if (!LABKEY.user.isAdmin && this.canEdit && question.lookup && question.lookup.filterColumn)
            config.store.filterArray = [LABKEY.Filter.create(question.lookup.filterColumn, null, LABKEY.Filter.Types.ISBLANK)];

        return config;
    },

    convertNamesToLowerCase : function(config) {
        if (config.items)
        {
            Ext4.each(config.items, function(item){
                this.convertNamesToLowerCase(item);
            }, this);
        }

        if (config.name)
            config.name = config.name.toLowerCase();
    },

    // Anything shared between actual questions and subSections
    doSharedQuestion : function(question, config) {
        // add a component id for retrieval
        if (config.name && !config.itemId) {
            config.itemId = this.makeItemId(config.name);
        }

        // register any configured listeners
        // Note: we allow for an array of listeners OR an array of question names to apply a single listener to
        var listeners = question.listeners || {}, changeListen, names, i, n, handlers;
        if (listeners.change) {
            if (!Ext4.isArray(listeners.change)) {
                listeners.change = [listeners.change];
            }

            for (i = 0; i < listeners.change.length; i++) {
                changeListen = listeners.change[i];
                if (Ext4.isArray(changeListen.question)) {
                    names = changeListen.question;
                }
                else {
                    names = [changeListen.question];
                }

                for (n = 0; n < names.length; n++) {
                    handlers = this.changeHandlers[names[n]] || [];
                    handlers.push({name: config.name, fn: new Function('', "return " + changeListen.fn)});
                    this.changeHandlers[names[n]] = handlers;
                }
            }
        }
    },

    getCardSectionHeader : function(title, subTitle) {
        var txt;
        txt = Ext4.DomHelper.markup({tag:'div', cls:'labkey-nav-page-header', html: title}) +
                (subTitle ? Ext4.DomHelper.markup({tag:'div', style:'margin-top: 10px', html: subTitle}) : "") +
                Ext4.DomHelper.markup({tag:'div', html:'&nbsp;'});
        return {xtype:'displayfield', value: txt};
    },

    createDeleteCheckbox: function(section, index) {
        if (0 == index && section.hasHeadingRow)
        {
            return {
                xtype: 'displayfield',
                name: section.name + '-delete-heading' + index,
                submitValue: false,
                value: 'Del',
                tooltip: "Check samples to delete and click button at the bottom of the panel",
                style: {
                    'background-color': '#E0E0E0'
                },
                width: 24,
                minWidth: 16
            };
        }
        else
        {
            return {
                xtype: 'checkbox',
                name: section.name + '-delete-checkbox' + index,
                submitValue: false,
                width: 16,
                minWidth: 16,
                margin: '0 0 0 2'
            };
        }
    },

    addSurveyStartPanel : function() {

        // add an initial panel that has the survey label text field
        var title = this.startSectionTitle;
        var items = [];

        if (this.surveyLayout == 'card')
            items.push(this.getCardSectionHeader(title, null));

        if (this.useDefaultLabel) {
            this.surveyLabel = new Date();
        }

        items.push({
            xtype: 'textfield',
            name: '_surveyLabel_', // for selenium testing
            itemId: 'surveyLabel',
            value: this.surveyLabel,
            submitValue: false, // this field applies to the surveys table not the responses
            fieldLabel: this.labelCaption + '*',
            labelStyle: 'font-weight: bold;',
            labelWidth: this.labelWidth || 350,
            labelSeparator: '',
            maxLength: 200,
            allowBlank: false,
            readOnly: !this.canEdit,
            hidden: this.useDefaultLabel,
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

        if (this.startSectionDescription) {
            items.push({
                xtype   : 'label',
                hideLabel: true,
                data    : {},
                tpl     : [this.startSectionDescription]
            });
        }

        this.sections.push(Ext4.create('Ext.panel.Panel', {
            title: title,
            isDisabled: false,
            header: false,
            border: false,
            minHeight: 60,
            padding: 10,
            items: items
        }));
    },

    addSurveyEndPanel : function(saveDisabledInfo) {

        var simpleSaveCancel = this.saveSubmitMode && 'save/cancel' == this.saveSubmitMode;

        // add a final panel that has the Save/Submit buttons and required field checks
        this.updateSubmittedInfo = Ext4.create('Ext.form.DisplayField', {
            hideLabel: true,
            width: 250,
            style: "text-align: center;",
            hidden: !this.isSubmitted,
            value: "<span style='font-style: italic; font-size: 90%'>"
                    + (this.submitted && this.submittedBy ? "Submitted by " + this.submittedBy + "<br/>on " + this.submitted + ".<br/><br/>" : "")
                    + (this.canEdit ? "You are allowed to make changes to this form because you are a project/site administrator.<br/><br/>" : "")
                    + "</span>"
        });

        this.saveBtn = Ext4.create('Ext.button.Button', {
            text: 'Save',
            disabled: !simpleSaveCancel,
            width: 150,
            height: 30,
            scope: this,
            handler: function(btn, evt) {
                this.saveSurvey(btn, evt, false, null, null);
            }
        });

        if (saveDisabledInfo)
        {
            this.saveDisabledInfo = saveDisabledInfo;
        }
        else
        {
            // Create if caller doesn't pass one in
            this.saveDisabledInfo = Ext4.create('Ext.form.DisplayField', {
                hideLabel: true,
                width: 250,
                style: "text-align: center;",
                hidden: this.surveyLabel != null,
                value: "<span style='font-style: italic; font-size: 90%'>Note: The '" + this.labelCaption + "' field at the"
                    + " beginning of the form must be filled in before you can save the form*.</span>"
            });
        }

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

        this.doneBtn = Ext4.create('Ext.button.Button', {
            text: 'Done',
            width: 175,
            height: 30,
            handler: function() { this.leavePage(); },
            scope: this
        });

        this.cancelBtn = Ext4.create('Ext.button.Button', {
            text: 'Cancel',
            disabled: !simpleSaveCancel,
            width: 150,
            height: 30,
            scope: this,
            handler: function(btn, evt) {
                this.leavePage();
            }
        });


        // the set of buttons and text on the end survey page changes based on whether or not the survey was submitted
        // and whether or not the user can edit a submitted survey (i.e. is project/site admin)
        var items = [{
            xtype: 'panel',
            layout: {type: 'vbox', align: 'center'},
            items:  simpleSaveCancel
                    ? [this.saveBtn, this.saveDisabledInfo]
                    : this.canEdit
                      ? [this.updateSubmittedInfo, this.saveBtn, this.saveDisabledInfo, this.autosaveInfo]
                      : [this.updateSubmittedInfo, this.doneBtn]
        }];
        if (!simpleSaveCancel)
        {
            if (this.canEdit && !this.isSubmitted)
            {
                items.push({xtype: 'label', width: 30, value: null});
                items.push({
                    xtype: 'panel',
                    layout: {type: 'vbox', align: 'center'},
                    items: [this.submitBtn, this.submitInfo]
                });
            }
        }
        else
        {
            items.push({xtype: 'label', width: 30, value: null});
            items.push({
                xtype: 'panel',
                layout: {type: 'vbox', align: 'center'},
                items: [this.cancelBtn]
            });
        }

        this.sections.push(Ext4.create('Ext.panel.Panel', {
            title: simpleSaveCancel ? 'Save / Cancel' : (this.canEdit ? 'Save / Submit' : 'Done'),
            isDisabled: false,
            layout: {
                type: 'hbox',
                align: 'top',
                pack: 'center'
            },
            header: false,
            border: false,
            bodyStyle: 'padding-top: 25px;',
            defaults: { border: false, width: 250 },
            items: items,
            listeners: {
                scope: this,
                activate : function(cmp) {
                    // ensure the window is scrolled into view
                    window.scrollTo(0,0);
                }
            }
        }));
    },

    toggleSaveBtn : function(enable, toggleMsg) {

        this.saveBtn.setDisabled(!enable);

        if (toggleMsg)
            enable ? this.saveDisabledInfo.hide() : this.saveDisabledInfo.show();
    },

    configureSurveyLayout : function(surveyConfig, initSectionTitle) {
        this.currentStep = this.getInitSection(initSectionTitle || LABKEY.ActionURL.getParameter("sectionTitle"));

        // in card layout, we add a side bar with the section titles and next/previous buttons
        var bbar = [];
        if (this.surveyLayout == 'card')
        {
            var sidebarWidth = surveyConfig.survey.sidebarWidth ? surveyConfig.survey.sidebarWidth : 250;
            var mainPanelWidth = surveyConfig.survey.mainPanelWidth ? surveyConfig.survey.mainPanelWidth : this.width;
            this.width = mainPanelWidth + sidebarWidth;

            // define function to be called on click of a sidebar section title
            window.surveySidebarSectionClick = function(step, itemId){
                // need to use component query to get back at the FormPanel object
                var panels = Ext4.ComponentQuery.query('#' + itemId);
                if (panels.length == 1)
                {
                    panels[0].updateStep(step);
                }
            };

            this.sideBar = Ext4.create('Ext.panel.Panel', {
                name: 'sidebar',
                width: sidebarWidth,
                border: false,
                tpl: [
                    '<div class="labkey-ancillary-wizard-background">',
                    '<ol class="labkey-ancillary-wizard-steps">',
                    '<tpl for="steps">',
                        '<tpl if="values.currentStep == true">',
                            '<li class="labkey-ancillary-wizard-active-step">{value}</li>',
                        '<tpl elseif="values.isDisabled == false">',
                            '<li onclick="surveySidebarSectionClick({step}, \'{panelId}\');" class="labkey-side-bar-title">{value}</li>',
                        '<tpl else>',
                            '<li class="labkey-side-bar-title-disabled">{value}</li>',
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
                    this.updateStep(this.previousEnabledStepIndex());
            }});

            this.nextBtn = Ext4.create('Ext.button.Button', {
                text: 'Next',
                scope: this,
                handler: function(cmp){
                    this.updateStep(this.nextEnabledStepIndex());
            }});

            this.progressBar = Ext4.create('Ext.ProgressBar', {
                value: (this.currentStep+1)/this.sections.length,
                text: "Page " + (this.currentStep+1) + " of " + this.sections.length,
                hidden: this.showProgressBar != undefined ? !this.showProgressBar : true,
                width: this.progressBarWidth || 300
            });

            bbar = [this.progressBar, '->', this.prevBtn, this.nextBtn];
        }

        this.centerPanel = Ext4.create('Ext.panel.Panel', {
            layout: this.surveyLayout,
            border: false,
            minHeight: this.surveyLayout == 'card' ? 500 : undefined,
            height: this.fixedHeight ? this.fixedHeight : undefined,
            bodyStyle : 'padding: 10px;',
            activeItem: this.currentStep,
            flex: 1,
            items: this.sections,
            bbar: bbar.length > 0 ? bbar : undefined
        });
        this.add(this.centerPanel);
    },

    getInitSection : function(goToSection) {
        if (goToSection)
        {
            for (var i = 0; i < this.sections.length; i++)
            {
                if (this.sections[i].title == goToSection)
                    return i;
            }
        }
        return 0;
    },

    previousEnabledStepIndex : function() {
        var step = this.currentStep;
        var steps = this.getStepsDataArr();
        for (var i = (step - 1); i > -1; i--)
        {
            if (!steps[i].isDisabled)
            {
                step = i;
                break;
            }
        }

        return step;
    },

    nextEnabledStepIndex : function() {
        var step = this.currentStep;
        var steps = this.getStepsDataArr();
        for (var i = (step + 1); i < steps.length; i++)
        {
            if (!steps[i].isDisabled)
            {
                step = i;
                break;
            }
        }

        return step;
    },

    getStepsDataArr : function() {
        var steps = [];
        for (var i = 0; i < this.sections.length; i++)
        {
            steps.push({
                value: this.sections[i].title,
                step: i,
                currentStep: i == this.currentStep,
                isDisabled: this.sections[i].isDisabled,
                panelId: this.itemId
            });
        }

        return steps;
    },

    updateStep : function(step) {

        this.currentStep = step;
        this.sideBar.update({steps: this.getStepsDataArr()});
        this.centerPanel.getLayout().setActiveItem(this.currentStep);

        this.prevBtn.setDisabled(this.currentStep == 0);
        this.nextBtn.setDisabled(this.currentStep == this.sections.length-1);

        if (this.progressBar)
        {
            this.updateProgressBar(
                    (this.currentStep+1)/this.sections.length,
                    "Page " + (this.currentStep+1) + " of " + this.sections.length,
                    true // animate
            );
        }
    },

    setSubmittedByInfo : function(responseConfig) {
        this.submitted = responseConfig.submitted ? responseConfig.submitted : null;
        this.submittedBy = responseConfig.submittedBy ? responseConfig.submittedBy : null;
    },

    setLabelCaption : function(surveyConfig) {
        this.labelCaption = 'Survey Label';
        if (surveyConfig.survey && surveyConfig.survey.labelCaption)
            this.labelCaption = surveyConfig.survey.labelCaption;

        if (surveyConfig.survey && surveyConfig.survey.labelWidth)
            this.labelWidth = surveyConfig.survey.labelWidth;
    },

    setStartOptions : function(surveyConfig) {
        this.startSectionTitle = "Start";
        this.startSectionDescription = null;
        this.useDefaultLabel = false;

        if (surveyConfig.survey && surveyConfig.survey.start) {
            // call the setLabelCaption function for backwards compatibility with the labelCaption and labelWidth options
            this.setLabelCaption({survey: surveyConfig.survey.start});

            this.startSectionTitle = surveyConfig.survey.start.sectionTitle || this.startSectionTitle;
            this.startSectionDescription = surveyConfig.survey.start.description || this.startSectionDescription;
            this.useDefaultLabel = surveyConfig.survey.start.useDefaultLabel || this.useDefaultLabel;
        }
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

    setNavigateOnSave : function(surveyConfig) {
        this.navigateOnSave = false;
        if (surveyConfig.survey && surveyConfig.survey.navigateOnSave)
            this.navigateOnSave = surveyConfig.survey.navigateOnSave;
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

    fieldValidityChanged : function(cmp, isValid) {
        var name = cmp.getName();

        // special case for radiogroups to set that valid status for the group instead of the radio field
        if (cmp.getXType() == "radiofield" && cmp.findParentByType("radiogroup"))
            name = cmp.findParentByType("radiogroup").getName();

        this.validStatus[name] = isValid;

        this.updateSubmitInfo();
    },

    updateSubmitInfo : function() {
        // do nothing
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
                var me = this.findSurveyElement(info.name);
                if (me)
                {
                    var changeFn = info.fn;

                    changeFn().call(this, me, cmp, newValue, oldValue, values);

                    this.clearHiddenFieldValues(me);
                }
            }
        }

        if (this.showCounts)
            this.updateSectionCount(cmp, newValue, oldValue);

        this.updateSubmitInfo();
    },

    clearHiddenFieldValues : function(cmp) {
        if (cmp.isHidden())
        {
            // the component can either be a form field itself or a container that has multiple fields
            if (cmp.isFormField)
                this.clearFieldValue(cmp);
            else if (cmp.query)
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

    updateSectionCount : function(cmp, newValue, oldValue) {

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

    setValues : function(form, values) {

        // the form.setValues doesn't play nicely with radio groups that have boolean false values, so set them via the radiogroup
        var rbGrps = this.query('radiogroup');
        Ext4.each(rbGrps, function(rbCmp){
            Ext4.each(rbCmp.initialConfig.items, function(rb){
                var objVal = {};
                objVal[rb.name] = values[rb.name];
                rbCmp.setValue(objVal);
            }, this);
        }, this);

        form.setValues(values);
    },

    getFormDirtyValues : function() {
        return this.getDirtyValues(this.getForm());
    },

    getDirtyValues : function(form) {
        var values = {};
        Ext4.each(form.getFields().items, function(field){
            if (field.submitValue && field.isDirty() && (field.isHidden() || field.isValid()))
            {
                // special casing for radiogroups and radiofields, i.e. skip the group field and use the individual radio fields
                if (field.getXType() == 'radiogroup') {
                    // skip the radiogroup itself in favor of the radiofields
                }
                else if (field.getXType() == 'checkboxgroup') {
                    if (field.getName()) {
                        values[field.getName()] = field.getValue();
                    }
                }
                else if (field.getXType() == "radiofield") {
                    if (field.getValue()) { // this will be true for only the selected radio field in the group
                        this.addFieldValue(values, field.getName(), field.getGroupValue());
                    }
                }
                else {
                    this.addFieldValue(values, field.getName(), field.getSubmitValue());
                }
            }
        }, this);

        return values;
    },

    addFieldValue : function(values, name, value) {

        // multi select values are encoded as an array
        if (values[name]) {
            if (Ext4.isArray(values[name])){
                values[name].push(value);
            }
            else {
                var valueArr = [];
                valueArr.push(values[name]);
                valueArr.push(value);

                values[name] = valueArr;
            }
        }
        else {
            values[name] = value;
        }
    },

    /*
     * return false if the call to saveSurvey should return without calling updateSurveyResponse
     */
    beforeSaveSurvey : function(btn, evt, toSubmit, successUrl, idParamName) {
        return true;
    },

    saveSurvey : function(btn, evt, toSubmit, successUrl, idParamName) {

        if (!this.beforeSaveSurvey(btn, evt, toSubmit, successUrl, idParamName))
            return;

        // get the dirty form values which are also valid and to be submitted
        this.submitValues = this.getFormDirtyValues();

        this.updateSurveyResponse(btn, evt, toSubmit, successUrl, idParamName, false);
    },

    updateSurveyResponse : function(btn, evt, toSubmit, successUrl, idParamName, navigateOnSave) {
        // default, do nothing
    },

    submitSurvey : function(btn, evt) {
        // call the save function with the toSubmit parameter as true
        this.saveSurvey(btn, evt, true, null, null);
    },

    isSurveyDirty : function() {
        return this.getForm().isDirty();
    },

    leavePage : function(pageId) {
        if (this.returnURL)
            window.location = this.returnURL;
        else if (pageId)
            window.location = LABKEY.ActionURL.buildURL('project', 'begin', null, { pageId: pageId });
        else
            window.location = LABKEY.ActionURL.buildURL('project', 'begin');
    },

    onFailure : function(resp, message, hidePanel) {
        var error = {};
        if (resp && resp.responseText)
            error = Ext4.decode(resp.responseText);
        else if (resp)
            error = resp;

        if (error.exception)
            message = error.exception;
        else if (error.errorInfo)
            message = error.errorInfo;

        // explicitly check for equals true because hidePanel could be an object
        if (hidePanel == true)
            this.update("<span class='labkey-error'>" + message + "</span>");
        else
            Ext4.MessageBox.alert('Error', message != null ? message : 'An unknown error has occurred.');

        this.clearLoadingMask();
    },

    clearLoadingMask: function() {
        this.setLoading(false);
    },

    updateSaveInfo : function(saveInfo) {
        var msg = "";

        // add to message for each required field name that is visible
        Ext4.each(this.requiredFieldNames, function(name){
            var cmp = this.down('[name=' + name + ']');
            if (cmp)
            {
                // get the field value to determine if it is not null (special case for radiogroups)
                var value = cmp.getValue();
                if (cmp.getXType() == "radiogroup")
                    value = cmp.getChecked().length > 0 ? cmp.getValue() : null;

                if (!cmp.isHidden() && !value)
                    msg += "-" + (cmp.shortCaption ? cmp.shortCaption : name) + "<br/>";
            }
        }, this);

        if (msg.length > 0)
        {
            msg = "<span style='font-style: italic; font-size: 90%'>"
                    + "Note: The following fields must be valid before you can save the form:<br/>"
                    + msg + "</span>";
        }

        saveInfo.update(msg);
        this.saveBtn.setDisabled(msg.length > 0);
    },

    // Helper to parse date values coming from the server
    getValueIfDate: function(value) {
        return Ext4.Date.parse(value, "Y/m/d H:i:s", true);     // Returns null if parse fails
    },

    makeKeyWithSectionName: function(sectionName, key) {
        return sectionName + '-' + key;
    },

    findSurveyElement: function(name) {
        return this.down('[itemId="' + this.makeItemId(name) + '"]');
    },

    makeItemId: function(name) {
        return 'item-' + name;
    },

    makeSectionQueryKey: function(section) {
        var name = section.queryName;
        if (name && section.queryNameUniquifier)
            name += '-zz-' + section.queryNameUniquifier;
        return name;
    },

    updateProgressBar: function(value, text, animate) {
        if (this.progressBar)
            this.progressBar.updateProgress(value, text, animate);
    }
});