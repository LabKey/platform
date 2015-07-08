/*
 * Copyright (c) 2015 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

Ext4.define('LABKEY.ext4.RedcapSettings', {

    extend: 'Ext.tab.Panel',

    constructor: function (config)
    {
        Ext4.applyIf(config, {
            bodyPadding : 20,
            buttonAlign : 'left',
            bodyStyle   : 'background-color: transparent;',
            defaults : {
                height  : 400,
                border  : false,
                frame   : false,
                cls     : 'iScroll', // webkit custom scroll bars
                bodyStyle: 'background-color: transparent;'
            }
        });

        this.currentRow = 0;
        this.callParent([config]);
    },

    initComponent: function ()
    {
        var items = [{
            xtype       : 'displayfield',
            value       : '<strong>project name</strong>'
        },{
            xtype       : 'displayfield',
            value       : '<strong>token</strong>'
        },{
            xtype       : 'button',
            text        : '+',
            margin      : '0 0 5 8',
            handler     : this.addRow,
            scope       : this
        }];

        if (this.bean && this.bean.projectname) {

            for (var i=0; i < this.bean.projectname.length; i++) {
                items = items.concat(this.getRowFields(true));
                this.currentRow++;
            }
        }
        else {
            items = items.concat(this.getRowFields());
            this.currentRow++;
        }

        this.items = [
            {
                xtype   : 'form',
                itemId  : 'auth-form',
                title   : 'Authentication',
                trackResetOnLoad : true,
                autoScroll : true,
                fieldDefaults  : {
                    height      : 22,
                    labelSeparator : ''
                },
                items : [{
                    xtype       : 'displayfield',
                    value       : '<span>Add API authentication information required to communicate with a remote REDCap server. REDCap requires an API token for each ' +
                        'project that is to be accessed through the API, the tokens are configured through the REDCap web interface. For each REDCap project ' +
                        'that you wish to import data into LabKey, there must be a separate row of connection information consisting of:</span>' +
                        '<ul><li><strong>project</strong> - the name of the REDCap project the token is associated with. This should match the project name in the configuration XML specified in the other tab.</li>' +
                        '<li><strong>token</strong> - the hexadecimal REDCap token for the project (can be located through the REDCap API settings page of the project you are exporting from)</li></ul>'
                    },{
                    xtype       : 'fieldset',
                    layout      : {
                        type    : 'table',
                        columns : 3
                    },
                    border      : false,
                    labelWidth  : 75,
                    items : items
                }]
            },{
                xtype   : 'form',
                itemId  : 'reload-form',
                title   : 'Reloading',
                trackResetOnLoad : true,
                fieldDefaults  : {
                    labelWidth : 200,
                    width : 550,
                    height : 22,
                    labelSeparator : ''
                },
                items : [
                    {
                        xtype       : 'displayfield',
                        value       : '<p><span>Automatic reloading can be configured to run at a specific frequency and start date. The specific ' +
                        'time that the reload is run can be configured from the <a href="' + LABKEY.ActionURL.buildURL('admin', 'configureSystemMaintenance.view', '/') + '">system maintenance page</a>.</span><p>'
                    },{
                        xtype       : 'checkbox',
                        fieldLabel  : 'Enable Reloading',
                        name        : 'enableReload',
                        inputId     : 'enableReload',
                        checked     : this.bean.enableReload,
                        uncheckedValue : 'false',
                        listeners : {
                            scope: this,
                            'change': function (cb, value)
                            {
                                cb.up('panel').down('numberfield[name=reloadInterval]').setDisabled(!value);
                                cb.up('panel').down('datefield[name=reloadDate]').setDisabled(!value);
                            }
                        }
                    },{
                        xtype       : 'datefield',
                        fieldLabel  : 'Load on',
                        disabled    : !this.bean.enableReload,
                        allowBlank  : false,
                        name        : 'reloadDate',
                        format      : 'Y-m-d',
                        altFormats  : '',
                        value       : this.bean.reloadDate
                    },{
                        xtype       : 'numberfield',
                        fieldLabel  : 'Repeat (days)',
                        disabled    : !this.bean.enableReload,
                        name        : 'reloadInterval',
                        value       : this.bean.reloadInterval,
                        minValue    : 1
                    }
                ]
            },{
                xtype   : 'form',
                itemId  : 'config-form',
                title   : 'Configuration Setting',
                trackResetOnLoad : true,
                fieldDefaults  : {
                    labelWidth : 200,
                    width : 550,
                    height : 22,
                    labelSeparator : ''
                },
                items : [{
                    xtype       : 'displayfield',
                    value       : '<span>Add XML metadata to control how REDCap projects are mapped to LabKey studies. ' +
                        'Click on this ' + this.helpLink + ' for documentation on XML schema.</span><p>'
                },{
                    xtype       : 'textarea',
                    name        : 'metadata',
                    height      : 300,
                    allowBlank  : false,
                    value       : this.bean.metadata,
                    listeners   : {
                        change : {fn : function(cmp){
                            this.markDirty(true);
                        }, scope : this}
                    }
                }]
            }
        ];

        //this.items = [formPanel, metadata];

        this.buttons = [{
            text    : 'Save',
            handler : function(btn) {
                var form = this.getComponent('auth-form').getForm();
                var formReload = this.getComponent('reload-form').getForm();
                var formAdvanced = this.getComponent('config-form').getForm();
                if (form.isValid() && formReload && formAdvanced.isValid()) {
                    this.getEl().mask("Saving...");
                    var params = form.getValues();

                    Ext4.apply(params, formReload.getValues());
                    Ext4.apply(params, formAdvanced.getValues());
                    Ext4.Ajax.request({
                        url    : LABKEY.ActionURL.buildURL('redcap', 'saveRedcapConfig.api'),
                        method  : 'POST',
                        params  : params,
                        submitEmptyText : false,
                        success : function(response){
                            var o = Ext4.decode(response.responseText);
                            this.getEl().unmask();

                            if (o.success) {
                                var msgbox = Ext4.create('Ext.window.Window', {
                                    title    : 'Save Complete',
                                    modal    : false,
                                    closable : false,
                                    border   : false,
                                    html     : '<div style="padding: 15px;"><span class="labkey-message">' + 'REDCap configuration saved successfully' + '</span></div>'
                                });
                                msgbox.show();
                                msgbox.getEl().fadeOut({duration : 3000, callback : function(){ msgbox.close(); }});

                                this.resetDirty();
                            }
                        },
                        failure : function(response){
                            this.getEl().unmask();
                            Ext4.Msg.alert('Failure', Ext4.decode(response.responseText).exception);
                        },
                        scope : this
                    });
                }
                else
                    Ext4.Msg.alert('Failure', 'Unable to save, the form is invalid. Please fill out all required fields before saving.');
            },
            scope   : this
        },{
            text    : 'Reload Now',
            handler : function(btn) {
                var form = this.getComponent('auth-form').getForm();
                var formReload = this.getComponent('reload-form').getForm();
                var formAdvanced = this.getComponent('config-form').getForm();

                if (!this.isDirty()) {
                    this.getEl().mask("Reloading REDCap...");
                    var params = form.getValues();

                    Ext4.apply(params, formReload.getValues());
                    Ext4.apply(params, formAdvanced.getValues());
                    Ext4.Ajax.request({
                        url    : LABKEY.ActionURL.buildURL('redcap', 'reloadRedcap.api'),
                        method  : 'POST',
                        params  : params,
                        submitEmptyText : false,
                        success : function(response){
                            var o = Ext4.decode(response.responseText);
                            this.getEl().unmask();

                            if (o.success)
                                window.location = o.returnUrl;
                        },
                        failure : function(response){
                            this.getEl().unmask();
                            Ext4.Msg.alert('Failure', Ext4.decode(response.responseText).exception);
                        },
                        scope : this
                    });
                }
                else
                    Ext4.Msg.alert('Failure', 'There are unsaved changes in your settings. Please save before starting the reload.');
            },
            scope   : this
        }];

        this.callParent();
        window.onbeforeunload = LABKEY.beforeunload(this.beforeUnload, this);
    },

    deleteRow : function(cmp){
        var parent = cmp.findParentByType('fieldset');
        if (parent){
            var row = [];
            var deleteButtons = [];

            Ext4.each(parent.items.items, function(item){
                if (item.row == cmp.row){
                    row.push(item);
                }
                else if (item.xtype == 'button' && item.text == 'delete'){
                    deleteButtons.push(item);
                }
            }, this);

            Ext4.each(row, function(item){
                parent.remove(item);
            }, this);

            if (deleteButtons.length == 1)
                deleteButtons[0].disable();

            this.markDirty(true);
        }
    },

    addRow : function(cmp){
        var parent = cmp.findParentByType('fieldset');
        if (parent){
            parent.add(this.getRowFields());

            Ext4.each(parent.items.items, function(item){
                if (item.xtype == 'button' && item.text == 'delete'){
                    item.enable();
                }
            }, this);

            this.currentRow++;
        }
    },

    getRowFields : function(initValues) {

        return[{
            xtype       : 'textfield',
            allowBlank  : false,
            row         : this.currentRow,
            name        : 'projectname',
            emptyText   : 'CaseReportForms',
            width       : 250,
            value       : initValues ? this.bean.projectname[this.currentRow] : null,
            listeners   : {
                change : {fn : function(cmp){
                    this.markDirty(true);
                }, scope : this}
            }
        },{
            xtype       : 'textfield',
            allowBlank  : false,
            row         : this.currentRow,
            width       : 250,
            name        : 'token',
            inputType   : 'password',
            value       : initValues ? this.bean.token[this.currentRow] : null,
            listeners   : {
                change : {fn : function(cmp){
                    this.markDirty(true);
                }, scope : this}
            }
        },{
            xtype       : 'button',
            text        : 'delete',
            row         : this.currentRow,
            margin      : '0 0 5 8',
            handler     : this.deleteRow,
            disabled    : this.bean.projectname ? this.bean.projectname.length === 1 : true,
            scope       : this
        }];
    },

    markDirty : function(dirty) {
        this.dirty = dirty;
    },

    resetDirty : function() {
        this.dirty = false;
    },

    isDirty : function() {
        return this.dirty;
    },

    beforeUnload : function() {
        if (this.isDirty()) {
            return 'please save your changes';
        }
    }
});
