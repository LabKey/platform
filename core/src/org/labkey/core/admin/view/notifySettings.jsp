<%
/*
 * Copyright (c) 2014-2017 LabKey Corporation
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
<%@ page import="org.labkey.api.message.settings.MessageConfigService" %>
<%@ page import="org.labkey.api.util.UniqueID" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("Ext4");
    }
%>
<%
    final String ID_PREFIX = "labkey_";
    String panelDiv = ID_PREFIX + UniqueID.getRequestScopedUID(HttpView.currentRequest());

    MessageConfigService.EmailConfigForm bean = ((JspView <MessageConfigService.EmailConfigForm>)HttpView.currentView()).getModelBean();
%>
<div id="<%=h(panelDiv)%>"></div>
<script type="text/javascript">

    Ext4.onReady(function() {
        var emailDefaultCombo = Ext4.create('Ext.form.field.ComboBox', {
            hiddenName:'<%=h(bean.getType())%>_defaultEmailOption',
            fieldLabel:'Default setting for <%=h(bean.getType().toLowerCase())%>',
            store: Ext4.create('Ext.data.JsonStore', {
                fields: ['id', 'label'],
                autoLoad: true,
                proxy: ({
                    type: 'ajax',
                    url: LABKEY.ActionURL.buildURL('announcements', 'getEmailOptions'),
                    extraParams: {
                        type: '<%=h(bean.getType())%>',
                        isDefault: true
                    },
                    reader: {
                        type: 'json',
                        root: 'options',
                        idProperty: 'id'
                    }
                }),
                listeners: {
                    load: function() { emailDefaultCombo.setValue(<%=bean.getDefaultEmailOption()%>); }
                }
            }),
            forceSelection:true,
            allowBlank: false,
            triggerAction:'all',
            valueField:'id',
            displayField:'label',
            width: 550,
            labelWidth: 250,
            labelAlign: 'right',
            emptyText:'Choose a folder default...'
        });

        Ext4.create('Ext.form.FormPanel', {
            renderTo: '<%=h(panelDiv)%>',
            width: 800,
            labelWidth: 350,
            labelAlign: 'top',
            labelPad: 15,
            border: false,
            items: [{
                xtype: 'fieldcontainer',
                layout: {
                    type: 'hbox',
                    defaultMargins: '2 25 2 0'
                },
                items: [emailDefaultCombo, {
                    xtype: 'button',
                    text: 'Update',
                    tooltip: 'Sets the default notification option for this folder',
                    handler: function(btn) {
                        Ext4.Ajax.request({
                            url: '<%=h(bean.getSetDefaultPrefURL())%>',
                            method: 'POST',
                            timeout: 30000,
                            params: {defaultEmailOption: emailDefaultCombo.getValue()},
                            success: function(response) {
                                var o = eval('var $=' + response.responseText + ';$;');
                                if (o && o.success) {
                                    Ext4.MessageBox.show({
                                        title: 'Update complete',
                                        animEl: btn.getEl(),
                                        msg: o.message
                                    });
                                    setTimeout(function(){
                                        Ext4.MessageBox.hide();
                                        LABKEY.Utils.signalWebDriverTest('notificationSettingUpdate');
                                    }, 1500);
                                }
                            },
                            failure: function(response, opts) {
                                LABKEY.Utils.displayAjaxErrorResponse(response, opts);
                            }
                        });
                    }
                }]
            }]
        });
    });

    var userSettings_<%=h(bean.getType())%> = function(selectionCount)
    {
        var idBulkEditBtn = Ext4.id();

        var emailCombo = Ext4.create('Ext.form.field.ComboBox',{
            hiddenName: '<%=h(bean.getType())%>EmailOption',
            fieldLabel: 'New setting',
            store: Ext4.create('Ext.data.JsonStore', {
                fields: ['id', 'label'],
                proxy: ({
                    type: 'ajax',
                    reader: {
                        type: 'json',
                        root: 'options',
                        idProperty: 'id'
                    },
                    url: LABKEY.ActionURL.buildURL('announcements', 'getEmailOptions'),
                    extraParams: {type: '<%=h(bean.getType())%>'}
                })
            }),
            forceSelection: true,
            allowBlank: false,
            triggerAction: 'all',
            valueField: 'id',
            displayField: 'label',
            width: 350,
            labelAlign: 'left',
            emptyText: 'Choose a setting...',
            listeners: {
                select: function(combo) {
                    Ext4.getCmp(idBulkEditBtn).setDisabled(!combo.isValid(true));
                }
            }
        });

        // buttons
        var bulkEditHandler = function() {
            if (!emailCombo.validate()) {
                Ext4.MessageBox.alert('Error', 'Please select a notification setting from the drop down list.');
                return;
            }

            userSettingsPopup.close();

            var confirmMsg = 'Are you sure you want to update the setting for ';
            if (selectionCount > 1)
                confirmMsg += 'these ' + selectionCount + ' selected users?';
            else
                confirmMsg += 'the selected user?';

            Ext4.MessageBox.confirm('Update selected users', confirmMsg, function (btn) {
                if (btn === 'yes') {
                    Ext4.Ajax.request({
                        url: LABKEY.ActionURL.buildURL('announcements', 'setBulkEmailOptions'),
                        method: 'POST',
                        timeout: 30000,
                        params: {
                            type: '<%=h(bean.getType())%>',
                            individualEmailOption: emailCombo.getValue(),
                            dataRegionSelectionKey: '<%=h(bean.getDataRegionSelectionKey())%>',
                            returnUrl: <%=q(bean.getReturnUrl())%>
                        },
                        success: function (response, opts) {
                            var o = eval('var $=' + response.responseText + ';$;');
                            if (o && o.success)
                                window.location = <%=q(bean.getReturnUrl())%>;
                            else
                                Ext4.MessageBox.alert('Error', o.message);
                        },
                        failure: function (response, opts) {
                            LABKEY.Utils.displayAjaxErrorResponse(response, opts);
                        }
                    });
                }
            });
        };

        var userSettingsPopup = Ext4.create('Ext.window.Window', {
            title: 'Update user settings for <%=h(bean.getType().toLowerCase())%>',
            modal: true,
            border : false,
            scope: this,
            layout: 'hbox',
            height: 150,
            width: 400,
            bodyStyle : 'padding: 15px;',
            labelPad: 10,
            items: [emailCombo],
            autoShow: true,
            buttons: [{
                disabled: true,
                id: idBulkEditBtn,
                itemId: idBulkEditBtn,
                text: 'Update settings for ' + selectionCount + ' user' + (selectionCount > 1 ? 's' : ''),
                tooltip: 'Sets the notification option for all selected users',
                handler: bulkEditHandler
            },{
                text: 'Cancel',
                scope: this,
                handler: function() { userSettingsPopup.close(); }
            }]
        });
    };
</script>
