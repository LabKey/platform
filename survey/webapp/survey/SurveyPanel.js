/*
 * Copyright (c) 2012-2016 LabKey Corporation
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

        Ext4.applyIf(config, {
            border      : false,
            bodyStyle   : 'background-color: transparent;',
            autoHeight  : true,
            items       : []
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
            autosaveInterval: this.autosaveInterval,
            disableAutoSave : this.disableAutoSave
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
                jsonData: {designId : this.surveyDesignId, stringifyMetadata : true},
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

        Ext4.applyIf(config, {
            updateUrl   : LABKEY.ActionURL.buildURL('survey', 'updateSurveyResponse.api'),
            getResponsesUrl : LABKEY.ActionURL.buildURL('survey', 'getSurveyResponse.api')
        });

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
            var surveyLabelCmp = this.down('.textfield[itemId=surveyLabel]');
            if (surveyLabelCmp && surveyLabelCmp.isValid())
                this.toggleSaveBtn(isDirty, false);
        }, this);

        // add a delayed task for automatically saving the survey responses
        if (this.canEdit)
        {
            if (!this.disableAutoSave)
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
            }

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
                url     : this.getResponsesUrl,
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
                jsonData: {designId : this.surveyDesignId, stringifyMetadata : true},
                success : function(resp){
                    var o = Ext4.decode(resp.responseText);

                    if (o.success)
                    {
                        var metadata = Ext4.JSON.decode(o.survey.metadata);
                        var successFn = function(){
                            this.setLabelCaption(metadata);
                            this.setStartOptions(metadata);
                            this.setShowCounts(metadata);
                            this.setSurveyLayout(metadata);
                            this.setNavigateOnSave(metadata);
                            this.generateSurveySections(metadata);
                        };

                        if (metadata.survey.beforeLoad && metadata.survey.beforeLoad.fn){
                            var beforeLoad = new Function('', 'return ' + metadata.survey.beforeLoad.fn);

                            beforeLoad().call(this, successFn, this);
                        }
                        else {
                            successFn.call(this);
                        }
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

    configureSurveyLayout : function(surveyConfig) {
        this.callParent([surveyConfig]);

        this.updateSubmitInfo();
    },

    beforeSaveSurvey : function(btn, evt, toSubmit, successUrl, idParamName) {
        // check to make sure the survey label is not null, it is required
        if (!this.surveyLabel)
        {
            if (successUrl || idParamName)
                Ext4.MessageBox.alert('Error', 'The ' + this.labelCaption + ' is required.');

            return false;
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

            return false;
        }

        this.toggleSaveBtn(false, false);
        this.submitBtn.disable();

        return true;
    },

    updateSurveyResponse : function(btn, evt, toSubmit, successUrl, idParamName, navigateOnSave)
    {
        // send the survey rowId, surveyDesignId, and responsesPk as params to the API call
        Ext4.Ajax.request({
            url     : this.updateUrl,
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
                    if (o.survey['successUrl'])
                        successUrl = o.survey.successUrl;

                    // save any attachments added to this survey
                    this.saveSurveyAttachments();

                    // reset the values so that the form's dirty state is cleared, with one special case for the survey label field
                    var surveyLabelCmp = this.down('.textfield[itemId=surveyLabel]');
                    if (surveyLabelCmp)
                        this.submitValues[surveyLabelCmp.getName()] = surveyLabelCmp.getValue();
                    this.setValues(this.getForm(), this.submitValues);

                    var msgBox = Ext4.create('Ext.window.Window', {
                        title    : 'Success',
                        html     : toSubmit ? '<span class="labkey-message">This form has been successfully submitted.</span>'
                                        : '<span class="labkey-message">Changes saved successfully.</span>',
                        modal    : true,
                        closable : false,
                        bodyStyle: 'padding: 20px;'
                    });

                    if (btn != null)
                    {
                        this.autosaveInfo.update("");

                        // since the user clicked the save/submit button, give them an indication that the save was successful
                        msgBox.show();
                        this.closeMsgBox = new Ext4.util.DelayedTask(function(){ msgBox.hide(); }, this);
                        this.closeMsgBox.delay(2500);

                        // if this is a submit or the navigateOnSave is set (Issue 32539), leave the page after the delay timer as well
                        if (toSubmit || navigateOnSave || this.navigateOnSave)
                        {
                            this.updateSubmitInfo();

                            var navigateTask = new Ext4.util.DelayedTask(function(){ this.leavePage(); }, this);
                            navigateTask.delay(2500);
                        }
                    }
                    else
                    {
                        // if no btn param, the save was done via the auto save task
                        this.autosaveInfo.update("<span style='font-style: italic; font-size: 90%'>Responses automatically saved at " + Ext4.util.Format.date(new Date(), 'g:i:s A') + "</span>");
                        this.updateSubmitInfo();
                    }

                    if (successUrl && idParamName) {
                        var params = {};
                        params[idParamName] = this.rowId;
                        window.location = successUrl + "&" + LABKEY.ActionURL.queryString(params);
                    }
                    else if (successUrl) {
                        this.getForm().reset();
                        window.location = successUrl;
                    }
                }
                else
                    this.onFailure(resp);
            },
            failure : this.onFailure,
            scope   : this
        });
    },

    updateSubmitInfo : function() {
        var msg = this.surveyLabel == null ? "-" + this.labelCaption + "<br/>" : "";
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

    onFailure : function(resp, message, hidePanel) {
        this.callParent([resp, message, hidePanel]);

        var surveyLabelCmp = this.down('.textfield[itemId=surveyLabel]');
        if (this.isSurveyDirty() && surveyLabelCmp && surveyLabelCmp.isValid())
        {
            this.toggleSaveBtn(true, false);
            this.updateSubmitInfo();
        }
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
                    var attachmentFiles = cmp.getFormPanel().items;
                    if (attachmentFiles && attachmentFiles.length === 1) {
                        var uploadedFile = attachmentFiles.get(0).fileInputEl.dom.files[0];

                        if (uploadedFile) {
                            var formData = new FormData();
                            formData.append('file', uploadedFile);
                            formData.append('rowId', this.rowId);
                            formData.append('questionName', cmp.name);

                            LABKEY.Ajax.request({
                                method  : 'POST',
                                url     : LABKEY.ActionURL.buildURL('survey', 'updateSurveyResponseAttachments.api'),
                                form    : formData,
                                success : function() {

                                    // need to refresh the component
                                },
                                failure : LABKEY.Utils.displayAjaxErrorResponse,
                                scope : this
                            });
                        }
                    }
                }
            }
        }
    }
});