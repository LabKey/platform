<%
/*
 * Copyright (c) 2013 LabKey Corporation
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
%>
<%@ page import="com.fasterxml.jackson.databind.ObjectMapper" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="org.labkey.freezerpro.FreezerProController" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page import="org.labkey.freezerpro.FreezerProConfig" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%!

    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
        resources.add(ClientDependency.fromFilePath("Ext4"));
        return resources;
    }
%>
<%
    JspView<FreezerProConfig> me = (JspView<FreezerProConfig>)HttpView.currentView();
    FreezerProConfig bean = me.getModelBean();

    ObjectMapper jsonMapper = new ObjectMapper();
%>

<labkey:errors/>
<script type="text/javascript">

    Ext4.onReady(function() {

        var bean = <%=text(jsonMapper.writeValueAsString(bean))%>;
        var formPanel = Ext4.create('Ext.form.Panel', {
            title   : 'Connection',
            trackResetOnLoad : true,
            fieldDefaults  : {
                labelWidth : 200,
                width : 450,
                height : 22,
                labelSeparator : ''
            },
            items : [{
                xtype       : 'displayfield',
                value       : '<span>This configuration enables the automatic reloading of specimen data directly from a FreezerPro server. The credentials specified ' +
                        'will be used to connect to the remote FreezerPro server.</span><p>'
            },{
                xtype       : 'textfield',
                fieldLabel  : 'FreezerPro Server Base URL',
                allowBlank  : false,
                name        : 'baseServerUrl',
                emptyText   : 'http://10.10.10.54/api',
                value       : bean.baseServerUrl
            },{
                xtype       : 'textfield',
                fieldLabel  : 'User Name',
                allowBlank  : false,
                name        : 'username',
                value       : bean.username
            },{
                xtype       : 'textfield',
                fieldLabel  : 'Password',
                allowBlank  : false,
                name        : 'password',
                inputType   : 'password',
                value       : bean.password
            },{
                xtype       : 'displayfield',
                value       : '<p><span>Automatic reloading can be configured to run at a specific frequency and start date. The specific ' +
                                'time that the reload is run can be configured from the <a href="' + LABKEY.ActionURL.buildURL('admin', 'configureSystemMaintenance.view', '/') + '">system maintenance page</a>.</span><p>'
            },{
                xtype       : 'checkbox',
                fieldLabel  : 'Enable Reloading',
                name        : 'enableReload',
                checked     : bean.enableReload,
                listeners : {
                    scope: this,
                    'change': function(cb, value) {
                        formPanel.down('numberfield[name=reloadInterval]').setDisabled(!value);
                        formPanel.down('datefield[name=reloadDate]').setDisabled(!value);
                    }
                }
            },{
                xtype       : 'datefield',
                fieldLabel  : 'Load on',
                disabled    : !bean.enableReload,
                name        : 'reloadDate',
                format      : 'Y-m-d',
                altFormats  : '',
                value       : bean.reloadDate
            },{
                xtype       : 'numberfield',
                fieldLabel  : 'Repeat (days)',
                disabled    : !bean.enableReload,
                name        : 'reloadInterval',
                value       : bean.reloadInterval,
                minValue    : 1
            }]
        });

        var metadata = Ext4.create('Ext.form.Panel', {
            title   : 'Advanced',
            trackResetOnLoad : true,
            fieldDefaults  : {
                labelWidth : 200,
                width : 550,
                height : 22,
                labelSeparator : ''
            },
            items : [{
                xtype       : 'displayfield',
                value       : '<span>Add additional XML metadata to control filtering of sample exports and mapping of FreezerPro ' +
                        'field names to LabKey field names. Click on this <a href="">link</a> for documentation on XML schema.</span><p>'
            },{
                xtype       : 'textarea',
                name        : 'metadata',
                height      : 200,
                value       : bean.metadata
            }]
        });

        var panel = Ext4.create('Ext.tab.Panel', {
            renderTo    : 'fp-config-div',
            bodyPadding : 20,
            buttonAlign : 'left',
            items       : [formPanel, metadata],
            bodyStyle   :'background-color: transparent;',
            defaults  : {
                height : 300,
                border  : false,
                frame   : false,
                cls         : 'iScroll', // webkit custom scroll bars
                bodyStyle   :'background-color: transparent;'
            },

            buttons     : [{
                text    : 'Save',
                handler : function(btn) {
                    var form = formPanel.getForm();
                    var formAdvanced = metadata.getForm();
                    if (form.isValid() && formAdvanced.isValid())
                    {
                        panel.getEl().mask("Saving...");
                        var params = form.getValues();

                        Ext4.apply(params, formAdvanced.getValues());
                        Ext4.Ajax.request({
                            url    : LABKEY.ActionURL.buildURL('freezerpro', 'saveFreezerProConfig.api'),
                            method  : 'POST',
                            params  : params,
                            submitEmptyText : false,
                            success : function(response){
                                var o = Ext4.decode(response.responseText);
                                panel.getEl().unmask();

                                if (o.success) {
                                    var msgbox = Ext4.create('Ext.window.Window', {
                                        title    : 'Save Complete',
                                        modal    : false,
                                        closable : false,
                                        border   : false,
                                        html     : '<div style="padding: 15px;"><span class="labkey-message">' + 'FreezerPro configuration saved successfully' + '</span></div>'
                                    });
                                    msgbox.show();
                                    msgbox.getEl().fadeOut({duration : 3000, callback : function(){ msgbox.close(); }});

                                    form.setValues(form.getValues());
                                    form.reset();

                                    formAdvanced.setValues(formAdvanced.getValues());
                                    formAdvanced.reset();
                                }
                            },
                            failure : function(response){
                                panel.getEl().unmask();
                                Ext4.Msg.alert('Failure', Ext4.decode(response.responseText).exception);
                            },
                            scope : this
                        });
                    }
                },
                scope   : this
            },{
                text    : 'Test Connection',
                handler : function(btn) {
                    var form = formPanel.getForm();
                    var formAdvanced = metadata.getForm();
                    if (form.isValid() && formAdvanced.isValid())
                    {
                        panel.getEl().mask("Testing FreezerPro Connection...");
                        var params = form.getValues();

                        Ext4.apply(params, formAdvanced.getValues());
                        Ext4.Ajax.request({
                            url    : LABKEY.ActionURL.buildURL('freezerpro', 'testFreezerProConfig.api'),
                            method  : 'POST',
                            params  : params,
                            submitEmptyText : false,
                            success : function(response){
                                var o = Ext4.decode(response.responseText);
                                panel.getEl().unmask();

                                if (o.success)
                                    Ext4.Msg.alert('Success', o.message);
                                else
                                    Ext4.Msg.alert('Failure', o.message);
                            },
                            failure : function(response){
                                panel.getEl().unmask();
                                Ext4.Msg.alert('Failure', Ext4.decode(response.responseText).exception);
                            },
                            scope : this
                        });
                    }
                },
                scope   : this
            },{
                text    : 'Reload Now',
                handler : function(btn) {
                    var form = formPanel.getForm();
                    var formAdvanced = metadata.getForm();
                    if (!form.isDirty() && !formAdvanced.isDirty())
                    {
                        panel.getEl().mask("Testing FreezerPro Connection...");
                        var params = form.getValues();

                        Ext4.apply(params, formAdvanced.getValues());
                        Ext4.Ajax.request({
                            url    : LABKEY.ActionURL.buildURL('freezerpro', 'reloadFreezerPro.api'),
                            method  : 'POST',
                            params  : params,
                            submitEmptyText : false,
                            success : function(response){
                                var o = Ext4.decode(response.responseText);
                                panel.getEl().unmask();

                                if (o.success)
                                    window.location = o.returnUrl;
                            },
                            failure : function(response){
                                panel.getEl().unmask();
                                Ext4.Msg.alert('Failure', Ext4.decode(response.responseText).exception);
                            },
                            scope : this
                        });
                    }
                    else
                        Ext4.Msg.alert('Failure', 'There are unsaved changes in your settings. Please save before starting the reload.');
                },
                scope   : this
            }]
        });
    });

</script>

<div>
    <div id='fp-config-div'></div>
</div>
