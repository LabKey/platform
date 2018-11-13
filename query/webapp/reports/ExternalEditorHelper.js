/*
 * Copyright (c) 2018 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.define('LABKEY.ext4.ExternalEditorHelper', {

    showMaskOnFormLoad: function(cmp) {
        if (this.externalEditSettings) {
            if (this.externalEditSettings.isEditing === true) {
                LABKEY.Utils.signalWebDriverTest("external-edit-url", this.externalEditSettings.externalUrl);
                this.showExternalEditingDialog((this.externalEditSettings));
            }
        }
    },

    getExternalEditBtn: function() {
        return {
            xtype : 'button',
            text : 'Edit in ' + this.externalEditSettings.name,
            hidden  : this.readOnly,
            style: 'margin-left: 5px;',
            scope : this,
            handler : function() {
                if (this.reportConfig.reportId)
                    this.save(this.externalEditSettings.url, null, false, true);
                else {
                    this.showSaveReportPrompt(this.externalEditSettings.url, 'Create New Report', false, true);
                }
            }
        };
    },

    initExternalWindow: function() {
        // browsers will block window.open not from direct user action.
        // as a work-around, open a blank window prior to ajax actions
        if (!this.externalEditWindow || !this.externalEditWindow.window || this.externalEditWindow.window.closed) {
            var winName = this.externalEditSettings && this.externalEditSettings.externalWindowTitle ? this.externalEditSettings.externalWindowTitle : "";
            this.externalEditWindow = window.open('', winName);
            this.formPanel.getEl().mask();
        }
    },

    closeExternalEditor: function() {
        if (this.externalEditWindow && this.externalEditWindow.window && !this.externalEditWindow.window.closed) {
            if (this.externalEditWindow.opener) {
                this.externalEditWindow.opener.focus();
            }
            this.externalEditWindow.close();
        }
        this.formPanel.getEl().unmask();
    },

    openExternalEditor : function (o) {
        this.readOnly = true;
        this.codeMirror.readOnly = true;
        var externalEditWindow = this.openWindowOnce(o.externalUrl, o.externalWindowTitle, this.externalEditSettings.finishUrl, o.entityId, o.redirectUrl);
        if (!externalEditWindow)
            return;

        this.externalEditWindow = externalEditWindow;
        this.showExternalEditingDialog(o);
    },

    openWindowOnce: function(url, windowName, finishUrl, entityId, returnUrl) {
        // open a blank windowName window
        // or get the reference to the existing windowName window
        if (this.externalEditWindow && this.externalEditWindow.window && !this.externalEditWindow.window.closed) {
            this.externalEditWindow.name = windowName; // rstudio window for newly created report was opened with blank name
        }
        else {
            // if user closed new window before action complete, open again...
            this.externalEditWindow = window.open('', windowName);
        }

        if (this.externalEditWindow) {
            // if the windowName window was just opened, change its url to actual needed window
            if(this.externalEditWindow.location.href.indexOf(url) === -1){
                this.externalEditWindow.location.href = url;
            }
            this.externalEditWindow.focus(); // this seems to only work in some versions of IE
        }
        else {
            Ext4.Msg.show({
                title:'Error',
                msg: 'Unable to open window, please check your browser settings for allowing pop-up windows.',
                buttons: Ext4.Msg.OK,
                icon: Ext4.Msg.ERROR
            });
        }
        LABKEY.Utils.signalWebDriverTest("external-edit-url", url);
        if (finishUrl && entityId) {
            var me = this;
            window.finishEditing = function()
            {
                me._finishEditing(finishUrl, entityId, returnUrl);
            };
        }

        return this.externalEditWindow;
    },

    _finishEditing: function(finishUrl, entityId, returnUrl) {
        Ext4.Ajax.request(
                {
                    method: "POST",
                    url: finishUrl,
                    params: {
                        returnUrl: returnUrl,
                        entityId: entityId
                    },
                    success : function(resp, opt) {
                        var o = Ext4.decode(resp.responseText);

                        if (o.success) {
                            window.location = o.redirectUrl;
                        }
                        else {
                            LABKEY.Utils.displayAjaxErrorResponse(resp, opt);
                        }
                    },
                    failure : LABKEY.Utils.displayAjaxErrorResponse
                }
        );
    },

    showExternalEditingDialog: function(config)
    {
        var externalName = this.externalEditSettings.name;
        var me = this;
        var popup = new Ext4.Window({
            autoShow: true,
            modal: false,
            width: 380,
            height: 150,
            cls: 'external-editor-popup',
            border: false,
            closable: false,
            resizable: false,
            title: 'Editing report in ' + externalName,
            draggable: true,
            items:[{
                xtype: 'box',
                html: '<div style="margin: 10px;">Report is being edited in ' + externalName +
                '<br>' + externalName + ' may be in hidden window or tab. <br>' +
                'When finished in ' + externalName + ' click "Edit in LabKey" below.' +
                '<br>NOTE: Save your changes in  ' + externalName +' first!</div>'
            }],
            buttonAlign: 'center',
            buttons: [{
                text: 'Edit in LabKey',
                onClick : function () {
                    popup.close();
                    new Ext4.Window({
                        autoShow: true,
                        modal: false,
                        width: 150,
                        height: 80,
                        border: false,
                        header: false,
                        closable: false,
                        resizable: false,
                        items:[{
                            xtype: 'box',
                            html: '<div style="padding: 20px;"><i class="fa fa-spinner fa-pulse"></i> loading...</div>'
                        }]
                    });
                    var selfWindowName = 'lk' + (new Date()).getTime(); //a unique window name for current window
                    window.name = selfWindowName;
                    // close external edit window to avoid simultaneous editing
                    if (me.externalEditWindow && me.externalEditWindow.window) {
                        if (!me.externalEditWindow.window.closed && me.externalEditWindow.location.href.indexOf(config.externalUrl) !== -1) {
                            if (me.externalEditWindow.saveAndClose && typeof me.externalEditWindow.saveAndClose === "function")
                                me.externalEditWindow.saveAndClose();
                            else
                                me.externalEditWindow.close();
                        }
                    }
                    else
                    {
                        // open a temp window with desired window title, but without explicit href.
                        // This will open a new window only if config.externalWindowTitle isn't already open.
                        var tmpWin = window.open('', config.externalWindowTitle);
                        if (tmpWin) {
                            var href = tmpWin.location.href;
                            // if temp window href is desired externalUrl, temp window is the target external window.
                            if (href.indexOf(config.externalUrl) !== -1) // if RStudio report
                            {
                                if (tmpWin.saveAndClose && typeof tmpWin.saveAndClose === "function")
                                    tmpWin.saveAndClose();
                                else
                                    tmpWin.close();
                            }
                            // if temp window href is about:blank, temp window is newly opened and should be closed.
                            else if (href === 'about:blank') // if newly opened blank window
                                tmpWin.close();
                            // otherwise, the window used to be target external report window, but has been navigated away, no action
                            else
                            {
                                // switch focus back to LabKey report window
                                tmpWin.open('', selfWindowName).focus();
                            }
                        }
                    }

                    new Ext4.util.DelayedTask(function() {
                        this._finishEditing(me.externalEditSettings.finishUrl, config.entityId, config.redirectUrl);
                    }, me).delay(5000); // wait to stop RStudio docker container to allow buffer for saveAndClose

                }
            },{
                text: 'Go to ' + externalName,
                cls: 'external-editor-popup-btn',
                onClick : function () {
                    me.externalEditWindow = me.openWindowOnce(config.externalUrl, config.externalWindowTitle);
                }
            }]
        });

        this.formPanel.getEl().mask();
    }
});