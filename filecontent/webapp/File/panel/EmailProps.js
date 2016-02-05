/*
 * Copyright (c) 2013-2016 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('File.panel.EmailProps', {

    extend : 'Ext.panel.Panel',

    bodyStyle: 'background-color: transparent;',
    border: false,
    buttonAlign: 'left',
    fileIndex: 0,
    padding: 10,

    statics: {
        preferences: {
            FOLDERDEFAULT: {
                value: -1,
                label: 'Folder Default',
                description: 'use the defaults configured for this folder by an administrator.'
            },
            MINUTEDIGEST: {
                value: 513,
                label: '15 Minute Digest',
                description: 'send a email for file changes within a fifteen minute span.'
            },
            DAILYDIGEST: {
                value: 514,
                label: 'Daily Digest',
                description: 'send one email each day that summarizes file changes in this folder.'
            },
            NONE: {
                value: 512,
                label: 'None',
                description: 'don\'t send any email for file changes in this folder.'
            }
        }
    },

    initComponent : function()
    {
        Ext4.applyIf(this, {
            defaults: {
                bodyStyle: 'background-color: transparent;'
            },
            fileFields: [],   // Extra field information to collect/display for each file uploaded
            files: []         // File information for each file being transferred
        });

        this.items = [];

        this.displayInWindow = !this.renderTo;

        // add the buttons directly to this panel if not in a window
        if (!this.displayInWindow) {
            this.dockedItems = [{
                xtype: 'toolbar',
                dock: 'bottom',
                ui: 'footer',
                style: 'background-color: transparent;',
                items: [{
                    text: 'Submit',
                    handler: this.onFormSubmit,
                    scope: this
                },{
                    text: 'Cancel',
                    handler: this.onFormCancel,
                    scope: this
                }]
            }];
        }

        this.callParent();

        Ext4.Ajax.request({
            url: LABKEY.ActionURL.buildURL('filecontent', 'getEmailPref.api', this.containerPath),
            method: 'GET',
            success: this.getEmailPref,
            failure: LABKEY.Utils.displayAjaxErrorResponse,
            scope: this
        });
    },

    getEmailPref : function(response)
    {
        var json = Ext4.decode(response.responseText),
            preferences = File.panel.EmailProps.preferences,
            emailPrefValue = json.emailPref,
            emailPrefDefault = parseInt(json.emailPrefDefault),
            defaultLabel = '';

        function generateOption(preference, currentPreference) {
            return {
                checked: preference.value == currentPreference,
                inputValue: preference.value,
                boxLabel: '<span class="labkey-strong">' + preference.label + '</span> - ' + preference.description
            };
        }

        // Determine the default label
        Ext4.iterate(preferences, function(p, preference) {
            if (emailPrefDefault == preference.value) {
                defaultLabel = preference.label;
                return false;
            }
        });

        this.add([{
            xtype: 'form',
            itemId: 'emailPrefForm',
            labelWidth: 5,
            flex: 1,
            border: false,
            items: [{
                xtype: 'radiogroup',
                itemId: 'emailPref',
                columns: 1,
                width : 630,
                labelSeparator: '',
                defaults: {width: 600, name: 'emailPref'},
                items: [
                    generateOption(preferences.FOLDERDEFAULT, emailPrefValue),
                    generateOption(preferences.MINUTEDIGEST, emailPrefValue),
                    generateOption(preferences.DAILYDIGEST, emailPrefValue),
                    generateOption(preferences.NONE, emailPrefValue)
                ]
            }]
        },{
            xtype: 'box',
            style: 'padding: 0 0 10px 5px',
            tpl: new Ext4.XTemplate(
                '<tpl if="emailPref == File.panel.EmailProps.preferences.FOLDERDEFAULT.value">',
                    'The default setting for this folder is: <span class="labkey-strong">{defaultLabel}</span>',
                '<tpl else>',
                    '&nbsp;',
                '</tpl>'
            ),
            data: {
                emailPref: emailPrefValue,
                defaultLabel: defaultLabel
            },
            listeners: {
                afterrender: {
                    fn: function(msg) {
                        this.getComponent('emailPrefForm').getComponent('emailPref').on('change', function(rg, newValue) {
                            msg.update({
                                emailPref: newValue.emailPref,
                                defaultLabel: defaultLabel
                            });
                        });
                    },
                    scope: this,
                    single: true
                }
            }
        }]);

        if (this.displayInWindow) {
            Ext4.create('Ext.Window', {
                title: 'Email Notification Settings',
                cls: 'data-window',
                autoScroll: true,
                closeAction: 'close',
                modal: true,
                layout: 'fit',
                autoShow: true,
                items: [this],
                buttons: [{
                    text: 'Submit',
                    handler: this.onFormSubmit,
                    scope: this
                },{
                    text: 'Cancel',
                    handler: this.onFormCancel,
                    scope: this
                }]
            });
        }
    },

    onFormSubmit : function(btn) {
        this.getComponent('emailPrefForm').getForm().doAction('submit', {
            url: LABKEY.ActionURL.buildURL('filecontent', 'setEmailPref.api', this.containerPath),
            method: 'POST',
            waitMsg: 'Saving Settings...',
            clientValidation: false,
            success: function() {
                if (this.displayInWindow) {
                    btn.up('window').close();
                }
                else {
                    window.location = LABKEY.ActionURL.buildURL('filecontent', 'begin', this.containerPath);
                }
            },
            failure: LABKEY.Utils.displayAjaxErrorResponse,
            scope: this
        });
    },

    onFormCancel : function(btn) {
        if (this.displayInWindow) {
            btn.up('window').close();
        }
        else {
            window.location = LABKEY.ActionURL.buildURL('filecontent', 'begin', this.containerPath);
        }
    }
});

