<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="org.labkey.api.message.settings.MessageConfigService" %>
<%@ page import="org.labkey.api.util.UniqueID" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="java.util.LinkedHashSet" %>
<%
    /*
     * Copyright (c) 2014 LabKey Corporation
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
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    addClientDependency("clientapi/ext4");
%>

<%final String ID_PREFIX = "labkey_";
    String panelDiv = ID_PREFIX + UniqueID.getRequestScopedUID(HttpView.currentRequest());

    MessageConfigService.EmailConfigForm bean = ((JspView <MessageConfigService.EmailConfigForm>)HttpView.currentView()).getModelBean();

%>

<div id="<%=h(panelDiv)%>"></div>

<script type="text/javascript">

    Ext4.onReady(function(){
        var emailDefaultCombo;

        var defaultOptionsStore = Ext4.create('Ext.data.JsonStore', {
            fields: ['id', 'label'],
            autoLoad: true,
            proxy: ({
                type: 'ajax',
                reader: {
                    type: 'json',
                    root: 'options',
                    idProperty: 'id'
                },
                url: LABKEY.ActionURL.buildURL('announcements', 'getEmailOptions'),
                extraParams: {type: '<%=h(bean.getType())%>', isDefault: true}})
        });
        defaultOptionsStore.on('load', function(){emailDefaultCombo.setValue(<%=bean.getDefaultEmailOption()%>);});

        emailDefaultCombo = Ext4.create('Ext.form.field.ComboBox', {
            hiddenName:'<%=h(bean.getType())%>_defaultEmailOption',
            fieldLabel:'Default Setting For <%=h(StringUtils.capitalize(bean.getType()))%>',
            store: defaultOptionsStore,
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

        // buttons
        var folderDefaultBtn = Ext4.create('Ext.button.Button', {text:'Update',
            tooltip: 'Sets the default notification option for this folder',
            handler: function(b,e){
                Ext4.Ajax.request({
                    url: '<%=h(bean.getSetDefaultPrefURL())%>',
                    method: 'POST',
                    timeout: 30000,
                    params: {defaultEmailOption: emailDefaultCombo.getValue()},
                    success: function(response, opts){
                        var o = eval('var $=' + response.responseText + ';$;');
                        if (o && o.success)
                        {
                            Ext4.MessageBox.show({
                                title: 'Update complete',
                                animEl: folderDefaultBtn.getEl(),
                                msg: o.message
                            });
                            setTimeout(function(){Ext4.MessageBox.hide()}, 1500);
                        }
                    },
                    failure: function(response, opts) {
                        LABKEY.Utils.displayAjaxErrorResponse(response, opts);
                    }
                });}
        });
        var formPanel = Ext4.create('Ext.form.FormPanel', {
            renderTo: '<%=h(panelDiv)%>',
            width: 800,
            labelWidth: 350,
            labelAlign: 'top',
            //buttonAlign: 'right',
            labelPad: 15,
            border: false,
            //bodyStyle : 'padding: 0px 10px;',
            items: [{
            xtype : 'fieldcontainer',
            layout : {
                type : 'hbox',
                defaultMargins : '2 25 2 0'
            }, items: [emailDefaultCombo, folderDefaultBtn]}]
        });

    });

    var userSettings_<%=h(bean.getType())%> = function(selectionCount)
    {
        var optionsStore = Ext4.create('Ext.data.JsonStore', {
            fields: ['id', 'label'],
            proxy: ({
                type: 'ajax',
                reader: {
                    type: 'json',
                    root: 'options',
                    idProperty: 'id'
                },
                url: LABKEY.ActionURL.buildURL('announcements', 'getEmailOptions'),
                extraParams: {type: '<%=h(bean.getType())%>'}})
        });

        var idBulkEditBtn = Ext.id();

        var emailCombo = Ext4.create('Ext.form.field.ComboBox',{
            hiddenName: '<%=h(bean.getType())%>EmailOption',
            fieldLabel: 'New Setting',
            store: optionsStore,
            forceSelection: true,
            allowBlank: false,
            triggerAction: 'all',
            valueField: 'id',
            displayField: 'label',
            width: 350,
            labelAlign: 'left',
            emptyText: 'Choose a setting...'
        });
        emailCombo.on('select', function (cmp, record, idx)
        {
            Ext4.getCmp(idBulkEditBtn).setDisabled(!cmp.isValid(true));
        });


        // buttons
        var bulkEditHandler = function (btn)
        {
            if (!emailCombo.validate())
            {
                Ext4.MessageBox.alert('Error', 'Please select a notification setting from the drop down list.');
                return;
            }

            userSettingsPopup.close();

            var confirmMsg = 'Are you sure you want to update the setting for ';
            if (selectionCount > 1)
                confirmMsg += 'these ' + selectionCount + ' selected users?';
            else
                confirmMsg += 'the selected user?';

            Ext4.MessageBox.confirm('Update Selected Users',
                    confirmMsg,
                    function (btn)
                    {
                        if (btn == 'yes')
                        {
                            Ext4.Ajax.request({
                                url: LABKEY.ActionURL.buildURL('announcements', 'setBulkEmailOptions'),
                                method: 'POST',
                                timeout: 30000,
                                params: {
                                    type: '<%=h(bean.getType())%>',
                                    individualEmailOption: emailCombo.getValue(),
                                    dataRegionSelectionKey: '<%=h(bean.getDataRegionSelectionKey())%>',
                                    returnUrl: '<%=bean.getReturnUrl()%>'
                                },
                                success: function (response, opts)
                                {
                                    var o = eval('var $=' + response.responseText + ';$;');
                                    if (o && o.success)
                                        window.location = '<%=bean.getReturnUrl()%>';
                                    else
                                        Ext.MessageBox.alert('Error', o.message);
                                },
                                failure: function (response, opts)
                                {
                                    LABKEY.Utils.displayAjaxErrorResponse(response, opts);
                                }
                            });
                        }
                    });
        };

        var btnText = 'Update Settings For ' + selectionCount + ' User';
        if (selectionCount > 1)
            btnText += 's';

        var bulkEditBtn = Ext4.create('Ext.button.Button',{
            disabled: true,
            id: idBulkEditBtn,
            itemId: idBulkEditBtn,
            text: btnText,
            tooltip: 'Sets the notification option for all selected users',
            handler: bulkEditHandler
        });

        var userSettingsPopup = Ext4.create('Ext.window.Window', {
            title: 'Update User Settings For <%=h(StringUtils.capitalize(bean.getType()))%>',
            modal: true,
            border : false,
            scope: this,
            layout: 'hbox',
            height: 150,
            width: 400,
            bodyStyle : 'padding: 15px;',
            labelPad: 10,
            items: [emailCombo],
            buttons: [bulkEditBtn, {
                text: 'Cancel',
                scope: this,
                handler: function() { userSettingsPopup.close(); }
            }]
        });

        userSettingsPopup.show();
    };

</script>
