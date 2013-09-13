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
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.module.FolderType" %>
<%@ page import="org.labkey.api.view.FolderTab" %>
<%@ page import="java.util.List" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.core.admin.AdminController" %>
<%@ page import="org.springframework.validation.Errors" %>
<%@ page import="org.springframework.validation.ObjectError" %>
<%@ page import="org.labkey.api.view.Portal" %>
<%@ page import="java.util.Map" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%
    ViewContext context = HttpView.currentContext();
    JspView<AdminController.AddTabForm> me = (JspView<AdminController.AddTabForm>) HttpView.currentView();
    AdminController.AddTabForm form = me.getModelBean();
    Container c = context.getContainer();
    FolderType folderType = c.getFolderType();
    List<FolderTab> folderTabs = c.getDefaultTabs();        // Get from the container, so it can filter out deleted container tabs
    Errors errors = me.getErrors();
    Map<String, Portal.PortalPage> pages = Portal.getPages(c);
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
<div id="addTabUI"></div>

<script type="text/javascript">
    LABKEY.requiresExt4Sandbox(true);
</script>

<script type="text/javascript">
    var folderTabs = [];
    var folder = {
        name: '<%=h(folderType.getName())%>',
        label: '<%=h(folderType.getLabel())%>'
    };
    <%
        for (FolderTab folderTab : folderTabs)
        {
    %>
            folderTabs.push({
                name: '<%=h(folderTab.getName())%>',
                caption: '<%=h(folderTab.getCaption(context))%>',
                hidden: !<%=pages.containsKey(folderTab.getName())%>
            });
    <%
        }
    %>
    folder.tabs = folderTabs;
</script>

<script type="text/javascript">
    Ext4.onReady(function(){
        var panelItems = [],
            nameField = Ext4.create('Ext.form.field.Text', {
                fieldLabel: 'Name',
                name: 'tabName',
                allowBlank: true,
                width: '100%'
            }),
            standardTabsDisplayField = Ext4.create('Ext.Panel', {
                border: 0,
                frame: false,
                html: '<h3>Standard Tabs:</h3>',
                bodyStyle:'background-color: transparent;'
            }),
            folderTabsDisplayField = Ext4.create('Ext.Panel', {
                border: 0,
                frame: false,
                html: '<h3>' + folder.label +' Folder Tabs:</h3>',
                bodyStyle:'background-color: transparent;'
            }),
            portalTabRadio = Ext4.create('Ext.form.field.Radio', {
                boxLabel: 'Portal',
                inputValue: 'portal',
                name: 'tabType',
                checked: true,
                margin: '0 0 0 10px'
            });

        panelItems.push(nameField);
		panelItems.push(standardTabsDisplayField);
		panelItems.push(portalTabRadio);
		panelItems.push(folderTabsDisplayField);
        panelItems.push({xtype:'hidden', name:'returnUrl', value: "<%=form.getReturnActionURL() != null ? form.getReturnActionURL() : ""%>"});

        for(var i = 0; i < folder.tabs.length; i++){
			panelItems.push(Ext4.create('Ext.form.field.Radio', {
				boxLabel: folder.tabs[i].caption,
				inputValue: folder.tabs[i].name,
				disabled: !folder.tabs[i].hidden, // Need to figure this out.
				name: 'tabType',
                margin: '0 0  10px'
			}));
		}

        var newTabPanel = Ext4.create('Ext.form.Panel', {
			width: 400,
			items: panelItems,
			border: 0,
			frame: false,
			renderTo: 'addTabUI',
            url: 'addTab.view',
            standardSubmit: true,
            bodyStyle:'background-color: transparent;',
            padding: 10,
            dockedItems: [{
                xtype: 'toolbar',
                dock: 'bottom',
                ui: 'footer',
                style: 'background-color: transparent;',
                margin: '20 0 0 0 0',
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
