/*
 * Copyright (c) 2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */


 Ext4.define('LABKEY.EmailFolderPreferences', {

    extend : 'Ext.util.Observable',
    fileFields : [],    // array of extra field information to collect/display for each file uploaded
    files : [],         // array of file information for each file being transferred
    fileIndex : 0,
    emailPrefDefault : 0,

    constructor : function(config)
    {
        LABKEY.EmailFolderPreferences.superclass.constructor.call(this, config);

        Ext4.apply(this, config);

        this.addEvents(
                /**
                 * @event emailPrefsChanged
                 * Fires after the user's email preferences have been updated.
                 */
                'emailPrefsChanged'
        );
    },

    show : function(btn)
    {
        Ext4.Ajax.request({
            url: LABKEY.ActionURL.buildURL("filecontent", "getEmailPref", this.containerPath),
            method:'GET',
            disableCaching:false,
            success : this.getEmailPref,
            failure: LABKEY.Utils.displayAjaxErrorResponse,
            updateSelection: true,
            scope: this
        });
    },

    getEmailPref : function(response)
    {
        var o = eval('var $=' + response.responseText + ';$;');

        if (o.success)
        {
            var emailPref = o.emailPref;
            this.emailPrefDefault = o.emailPrefDefault;
            var radioItems = [];

            radioItems.push({xtype: 'radio',
                checked: emailPref == -1,
                handler: this.onFolderDefault,
                scope: this,
                boxLabel: "<span class='labkey-strong'>Folder Default</span> - use the defaults configured for this folder by an administrator.",
                name: 'emailPref', inputValue: -1});
            radioItems.push({xtype: 'radio',
                checked: emailPref == 513,
                boxLabel: '<span class="labkey-strong">15 Minute Digest</span> - send a email for file changes within a fifteen minute span.',
                name: 'emailPref', inputValue: 513});
            radioItems.push({xtype: 'radio',
                checked: emailPref == 514,
                boxLabel: '<span class="labkey-strong">Daily Digest</span> - send one email each day that summarizes file changes in this folder.',
                name: 'emailPref', inputValue: 514});
            radioItems.push({xtype: 'radio',
                checked: emailPref == 512,
                boxLabel: "<span class='labkey-strong'>None</span> - don't send any email for file changes in this folder.",
                name: 'emailPref', inputValue: 512});

            var radioGroup = Ext4.create('Ext.form.RadioGroup', {
                //fieldLabel: 'Email Notification Settings',
                columns: 1,
                width : 630,
                labelSeparator: '',
                items: radioItems
            });

            var formPanel = Ext4.create('Ext.form.Panel', {
                bodyStyle : 'padding:10px;',
                labelWidth: 5,
                flex: 1,
                border: false,
                defaultType: 'radio',
                items: radioGroup
            });

            var msgPanel = Ext4.create('Ext.Panel', {
                id: 'email-pref-msg',
                border: false,
                height: 40
            });

            var items = [formPanel, msgPanel];

            if (emailPref == -1)
            {
                msgPanel.on('afterrender', function(){this.onFolderDefault(null, true);}, this);
            }

            var panel = Ext4.create('Ext.Panel', {
                layout: 'vbox',
                layoutConfig: {
                    align: 'stretch',
                    pack: 'start'
                },
                bodyStyle : 'padding:10px;',
                items: items
            });

            if (this.renderTo)
            {
                var p = Ext4.create('Ext.Panel', {
                    renderTo: this.renderTo,
                    border: false,
                    height: 175,
                    items: items,
                    buttonAlign: 'left',
                    buttons: [
                        {text:'Submit', scope: this, handler:function(){
                            formPanel.getForm().doAction('submit', {
                                url: LABKEY.ActionURL.buildURL("filecontent", "setEmailPref", this.containerPath),
                                waitMsg:'Saving Settings...',
                                method: 'POST',
                                success: function(){window.location = LABKEY.ActionURL.buildURL('filecontent', 'begin', this.containerPath);},
                                failure: LABKEY.Utils.displayAjaxErrorResponse,
                                scope: this,
                                clientValidation: false
                            });}
                        },
                        {text:'Cancel', scope: this, handler:function(){
                            window.location = LABKEY.ActionURL.buildURL('filecontent', 'begin', this.containerPath);
                        }}
                    ]
                })
            }
            else
            {
                var win = Ext4.create('Ext.Window',{
                    title: 'Email Notification Settings',
                    width: 650,
                    height: 250,
                    cls: 'extContainer',
                    autoScroll: true,
                    closeAction:'close',
                    modal: true,
                    layout: 'fit',
                    items: panel,
                    buttons: [
                        {text:'Submit', scope: this, handler:function(){
                            formPanel.getForm().doAction('submit', {
                                url: LABKEY.ActionURL.buildURL("filecontent", "setEmailPref", this.containerPath),
                                waitMsg:'Saving Settings...',
                                method: 'POST',
                                success: function(){win.close();},
                                failure: LABKEY.Utils.displayAjaxErrorResponse,
                                scope: this,
                                clientValidation: false
                            });}
                        },
                        {text:'Cancel', handler:function(){win.close();}}
                    ]
                });
                win.show();
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

