/*
 * Copyright (c) 2012-2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

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
            autosaveInterval: this.autosaveInterval
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
                jsonData: {rowId : this.surveyDesignId, stringifyMetadata : true},
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

    extend : 'LABKEY.ext4.BaseSurveyPanel',

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
        this.addListener('validitychange', this.updateSubmitInfo, this);

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
                this.saveSurvey(null, null, false, null, null);
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

                            this.initialResponses[key.toLowerCase()] = val;
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
                jsonData: {rowId : this.surveyDesignId, stringifyMetadata : true},
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

        if (surveyConfig.survey && surveyConfig.survey.labelWidth)
            this.labelWidth = surveyConfig.survey.labelWidth;
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

    generateSurveySections : function(surveyConfig) {

        this.addSurveyStartPanel();

        this.callParent([surveyConfig]);

        this.addSurveyEndPanel();

        this.configureSurveyLayout(surveyConfig);

        this.configureFieldListeners();

        // if we have an existing survey record, initialize the fields based on the surveyResults
        if (this.initialResponses != null)
            this.setValues(this.getForm(), this.initialResponses);

        this.clearLoadingMask();
    },

    addSurveyStartPanel : function() {

        // add an initial panel that has the survey label text field
        var title = 'Start';
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
            labelWidth: this.labelWidth || 350,
            labelSeparator: '',
            maxLength: 200,
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
            isDisabled: false,
            header: false,
            border: false,
            minHeight: 60,
            padding: 10,
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
                    + (this.submitted && this.submittedBy ? "Submitted by " + this.submittedBy + "<br/>on " + this.submitted + ".<br/><br/>" : "")
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
                this.saveSurvey(btn, evt, false, null, null);
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

        this.doneBtn = Ext4.create('Ext.button.Button', {
            text: 'Done',
            width: 175,
            height: 30,
            handler: function() { this.leavePage(); },
            scope: this
        });

        // the set of buttons and text on the end survey page changes based on whether or not the survey was submitted
        // and whether or not the user can edit a submitted survey (i.e. is project/site admin)
        var items = [{
            xtype: 'panel',
            layout: {type: 'vbox', align: 'center'},
            items: this.canEdit
                    ? [this.updateSubmittedInfo, this.saveBtn, this.saveDisabledInfo, this.autosaveInfo]
                    : [this.updateSubmittedInfo, this.doneBtn]
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
            title: this.canEdit ? 'Save / Submit' : 'Done',
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
            items: items
        }));
    },

    configureSurveyLayout : function(surveyConfig) {
        this.callParent([surveyConfig]);

        this.updateSubmitInfo();
    },

    saveSurvey : function(btn, evt, toSubmit, successUrl, idParamName) {

        // check to make sure the survey label is not null, it is required
        if (!this.surveyLabel)
        {
            if (successUrl)
                Ext4.MessageBox.alert('Error', 'The ' + this.labelCaption + ' is required.');

            return;
        }

        // check to see if there is anything to be saved (or submitted)
        if (!this.isSurveyDirty() && !toSubmit)
        {
            // if there is a url to go to, navigate now
            if (successUrl && idParamName)
            {
                var params = {};
                params[idParamName] = this.rowId;
                window.location = successUrl + "&" + LABKEY.ActionURL.queryString(params);
            }

            return;
        }

        this.toggleSaveBtn(false, false);

        // get the dirty form values which are also valid and to be submitted
        this.submitValues = this.getFormDirtyValues();

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
                    this.setValues(this.getForm(), this.submitValues);

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
                            this.leavePage();
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

                    if (successUrl && idParamName)
                    {
                        var params = {};
                        params[idParamName] = this.rowId;
                        window.location = successUrl + "&" + LABKEY.ActionURL.queryString(params);
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
        this.saveSurvey(null, null, true, null, null);
    },

    toggleSaveBtn : function(enable, toggleMsg) {

        this.saveBtn.setDisabled(!enable);

        if (toggleMsg)
            enable ? this.saveDisabledInfo.hide() : this.saveDisabledInfo.show();
    },

    updateSubmitInfo : function() {
        var msg = this.surveyLabel == null ? "-" + this.labelCaption : "";
        for (var name in this.validStatus)
        {
            if (!this.validStatus[name])
            {
                var cmp = this.down('[name=' + name + ']');
                // conditional validStatus for hidden fields
                if (cmp && !cmp.isHidden())
                    msg += "-" + (cmp.shortCaption ? cmp.shortCaption : name) + "<br/>";
            }
        }

        if (msg.length > 0)
        {
            msg = "<span style='font-style: italic; font-size: 90%'>"
                + "Note: The following fields must be valid before you can submit the form*:<br/>"
                + msg + "</span>";
        }

        this.submitInfo.update(msg);
        this.submitBtn.setDisabled(msg.length != 0);
    },

    isSurveyDirty : function() {
        //console.log(this.getFormDirtyValues());
        return this.getForm().isDirty();
    },

    onFailure : function(resp, message, hidePanel) {
        this.callParent([resp, message, hidePanel]);

        if (this.isSurveyDirty() && this.down('.textfield[itemId=surveyLabel]').isValid())
            this.toggleSaveBtn(true, false);
    },

    leavePage : function() {
        if (this.returnURL)
            window.location = this.returnURL;
        else
            window.location = LABKEY.ActionURL.buildURL('project', 'begin');
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
    }
});