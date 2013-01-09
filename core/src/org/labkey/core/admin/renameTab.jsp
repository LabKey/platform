<%
    /*
    * Copyright (c) 2012 LabKey Corporation
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
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="java.util.List" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.core.admin.AdminController" %>
<%@ page import="org.springframework.validation.Errors" %>
<%@ page import="org.springframework.validation.ObjectError" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%
    ViewContext context = HttpView.currentContext();
    JspView<AdminController.RenameTabForm> me = (JspView<AdminController.RenameTabForm>) HttpView.currentView();
    AdminController.RenameTabForm form = me.getModelBean();
    Errors errors = me.getErrors();
%>

<%
    if(errors.hasErrors())
    {
%>
<div id="errors">
    <ul>
        <%
            for (ObjectError error : (List<ObjectError>) errors.getAllErrors())
            {
        %>
        <li>
            <p style="color:red;"><%=h(HttpView.currentContext().getMessage(error))%></p>
        </li>
        <%
            }
        %>
    </ul>
</div>
<%
    }
%>
<div id="renameTabUI"></div>

<script type="text/javascript">
    LABKEY.requiresExt4Sandbox(true);
</script>


<script type="text/javascript">
    Ext4.onReady(function(){
        var panelItems = [],
                nameField = Ext4.create('Ext.form.field.Text', {
                    fieldLabel: 'Name',
                    labelAlign: 'left',
                    name: 'tabName',
                    allowBlank: true,
                    width: '100%'
                });

        panelItems.push(nameField);
        panelItems.push({xtype:'hidden', name:'pageId', value: "<%=h(form.getPageId())%>"})
        panelItems.push({xtype:'hidden', name:'returnUrl', value: "<%=form.getReturnActionURL() != null ? form.getReturnActionURL() : ""%>"});

        var newTabPanel = Ext4.create('Ext.form.Panel', {
            width: 300,
            items: panelItems,
            border: 0,
            frame: false,
            renderTo: 'renameTabUI',
            url: 'renameTab.view',
            standardSubmit: true,
            bodyStyle:'background-color: transparent;',
            padding: 0,
            dockedItems: [{
                xtype: 'toolbar',
                dock: 'bottom',
                ui: 'footer',
                style: 'background-color: transparent;',
                margin: '0 0 0 0 0',
                items: [{
                    text: 'save',
                    handler: function(){
                        var form = newTabPanel.getForm();
                        console.log(form.getFieldValues());
                        console.log(form.getFields());
                        form.submit();
                    },
                    scope: this
                }, {
                    text: 'cancel',
                    handler: function(){
                        window.location = "<%=form.getReturnActionURL()%>";
                    }
                }]
            }]
        });

    });
</script>
