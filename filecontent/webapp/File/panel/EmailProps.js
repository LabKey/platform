/*
 * Copyright (c) 2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('File.panel.EmailProps', {

    extend : 'Ext.util.Observable',
    fileFields : [],    // array of extra field information to collect/display for each file uploaded
    files : [],         // array of file information for each file being transferred
    fileIndex : 0,
    emailPrefDefault : 0,

    constructor : function(config)
    {
        Ext4.apply(this, config);

        this.addEvents(
                /**
                 * @event emailPrefsChanged
                 * Fires after the user's email preferences have been updated.
                 */
                'emailPrefsChanged'
        );

        this.callParent([config]);
    },

    show : function()
    {
        Ext4.Ajax.request({
            url: LABKEY.ActionURL.buildURL("filecontent", "getEmailPref", this.containerPath),
            method: 'GET',
            disableCaching: false,
            success : this.getEmailPref,
            failure: LABKEY.Utils.displayAjaxErrorResponse,
            updateSelection: true,
            scope: this
        });
    },

    getEmailPref : function(response)
    {
        var o = Ext4.decode(response.responseText);

        if (o.success)
        {
            var emailPref = o.emailPref;
            this.emailPrefDefault = o.emailPrefDefault;

            var formPanel = Ext4.create('Ext.form.Panel', {
                bodyStyle : 'padding:10px;',
                labelWidth: 5,
                flex: 1,
                border: false,
                items: [{
                    xtype: 'radiogroup',
                    columns: 1,
                    width : 630,
                    labelSeparator: '',
                    items: [{
                        checked: emailPref == -1,
                        boxLabel: "<span class='labkey-strong'>Folder Default</span> - use the defaults configured for this folder by an administrator.",
                        name: 'emailPref', inputValue: -1,
                        handler: this.onFolderDefault,
                        scope: this
                    },{
                        checked: emailPref == 513,
                        boxLabel: '<span class="labkey-strong">15 Minute Digest</span> - send a email for file changes within a fifteen minute span.',
                        name: 'emailPref', inputValue: 513
                    },{
                        checked: emailPref == 514,
                        boxLabel: '<span class="labkey-strong">Daily Digest</span> - send one email each day that summarizes file changes in this folder.',
                        name: 'emailPref', inputValue: 514
                    },{
                        checked: emailPref == 512,
                        boxLabel: "<span class='labkey-strong'>None</span> - don't send any email for file changes in this folder.",
                        name: 'emailPref', inputValue: 512
                    }]
                }]
            });

            var items = [formPanel,{
                xtype: 'panel',
                id: 'email-pref-msg',
                border: false,
                height: 40,
                listeners: emailPref == -1 ? { afterrender: function() { this.onFolderDefault(null, true); }, scope: this } : undefined,
                scope: this
            }];

            if (this.renderTo)
            {
                Ext4.create('Ext.panel.Panel', {
                    renderTo: this.renderTo,
                    border: false,
                    height: 175,
                    items: items,
                    buttonAlign: 'left',
                    buttons: [{
                        text: 'Submit',
                        handler: function() {
                            formPanel.getForm().doAction('submit', {
                                url: LABKEY.ActionURL.buildURL("filecontent", "setEmailPref", this.containerPath),
                                waitMsg:'Saving Settings...',
                                method: 'POST',
                                success: function(){ window.location = LABKEY.ActionURL.buildURL('filecontent', 'begin', this.containerPath); },
                                failure: LABKEY.Utils.displayAjaxErrorResponse,
                                scope: this,
                                clientValidation: false
                            });
                        },
                        scope: this
                    },{
                        text:'Cancel',
                        handler: function() { window.location = LABKEY.ActionURL.buildURL('filecontent', 'begin', this.containerPath); }
                    }]
                })
            }
            else
            {
                var win = Ext4.create('Ext.Window', {
                    title: 'Email Notification Settings',
                    width: 650,
                    height: 250,
                    autoScroll: true,
                    closeAction: 'close',
                    modal: true,
                    layout: 'fit',
                    autoShow: true,
                    items: [{
                        xtype: 'panel',
                        layout: 'vbox',
                        layoutConfig: {
                            align: 'stretch',
                            pack: 'start'
                        },
                        bodyStyle : 'padding:10px;',
                        items: items
                    }],
                    buttons: [{
                        text: 'Submit',
                        handler: function(btn) {
                            formPanel.getForm().doAction('submit', {
                                url: LABKEY.ActionURL.buildURL("filecontent", "setEmailPref", this.containerPath),
                                method: 'POST',
                                waitMsg: 'Saving Settings...',
                                clientValidation: false,
                                success: function(){ btn.up('window').close(); },
                                failure: LABKEY.Utils.displayAjaxErrorResponse,
                                scope: this
                            });
                        },
                        scope: this
                    },{
                        text: 'Cancel',
                        handler: function(btn) { btn.up('window').close(); }
                    }]
                });
            }
        }
        else
            Ext4.Msg.alert('Error', 'An error occurred getting the user email settings.');
    },

    onFolderDefault : function(cb, checked)
    {
        if (checked)
        {
            var msg = 'The default setting for this folder is: <span class="labkey-strong">None</span>';
            if (this.emailPrefDefault == 513)
                msg = 'The default setting for this folder is: <span class="labkey-strong">15 Minute Digest</span>';
            else if (this.emailPrefDefault == 514)
                msg = 'The default setting for this folder is: <span class="labkey-strong">Daily Digest</span>';
        }
        else
            msg = '';

        var el = Ext4.getCmp('email-pref-msg');
        if (el)
            el.update(msg);
    }
});

