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
            border  : false,
            frame   : false,
            bodyStyle   :'background-color: transparent;',
            cls         : 'iScroll', // webkit custom scroll bars
            buttonAlign : 'left',
            renderTo    : 'fp-config-div',
            fieldDefaults  : {
                labelWidth : 200,
                width : 450,
                height : 22,
                //style      : 'margin: 0px 0px 10px 0px',
                labelSeparator : ''
            },
            items : [{
                xtype       : 'displayfield',
                value       : '<span><b>Settings</b><br><br>Specify the configuration values used to connect to the FreezerPro server including ' +
                        'the account settings used to connect to the FreezerPro server.</span><p>'
            },{
                xtype       : 'textfield',
                fieldLabel  : 'FreezerPro Server Base URL',
                allowBlank  : false,
                name        : 'baseServerUrl',
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
                minValue    : 0
            },{
                xtype   : 'button',
                text    : 'Save',
                formBind: true,
                handler : function(btn) {
                    var form = formPanel.getForm();
                    if (form.isValid())
                    {
                        formPanel.getEl().mask("Saving...");
                        var params = form.getValues();

                        Ext4.Ajax.request({
                            url    : LABKEY.ActionURL.buildURL('freezerpro', 'saveFreezerProConfig.api'),
                            method  : 'POST',
                            params  : params,
                            success : function(response){
                                var o = Ext4.decode(response.responseText);
                                formPanel.getEl().unmask();

                                if (o.success)
                                    window.location = o.returnUrl;
                            },
                            failure : function(response){
                                formPanel.getEl().unmask();
                                Ext4.Msg.alert('Failure', Ext4.decode(response.responseText).exception);
                            },
                            scope : this
                        });
                    }
                },
                scope   : this
            }]
        });
    });

</script>

<div>
    <div id='fp-config-div'></div>
</div>
