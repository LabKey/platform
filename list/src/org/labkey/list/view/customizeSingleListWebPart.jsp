<%
/*
 * Copyright (c) 2006-2016 LabKey Corporation
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
<%@ page import="org.labkey.api.exp.list.ListDefinition"%>
<%@ page import="org.labkey.api.exp.list.ListService" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.Portal" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="java.util.Map" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        //Need to include the Helper for a use of the form panel configuration.
        dependencies.add("Ext4");
        dependencies.add("sqv");
    }
%>
<%
    HttpView<Portal.WebPart> me = (HttpView<Portal.WebPart>) HttpView.currentView();
    Portal.WebPart part = me.getModelBean();
    ViewContext ctx = getViewContext();
    Map<String, String> props = part.getPropertyMap();

    String listName = props.get("listName");
    if (listName == null)
    {
        String listIdStr = props.get("listId");
        try
        {
            if (listIdStr != null)
            {
                int listId = Integer.parseInt(listIdStr);
                ListDefinition list = ListService.get().getList(getContainer(), listId);
                listName = list.getName();
            }
        }
        catch (NumberFormatException ex) { }
    }
%>

This webpart displays data from a single list.<br><br>

If you want to let users change the list that's displayed or customize the view themselves then use the query webpart.<br><br>

<div id="SQVPicker"></div>
<script type="text/javascript">
    Ext4.onReady(function(){
        var sqvModel = Ext4.create('LABKEY.sqv.Model', {});
        var title = Ext4.create('Ext.form.field.Text', {
            name : 'title',
            value :  <%=PageFlowUtil.jsString(props.get("title"))%>,
            fieldLabel : 'Title',
            width: 400
        });
        var queryCombo = ('Ext.form.field.ComboBox', sqvModel.makeQueryComboConfig({
            defaultSchema : 'lists',
            // Only include actual lists -- no custom queries
            includeUserQueries: false,
            fieldLabel : 'List',
            name: 'listName',
            initialValue : <%=PageFlowUtil.jsString(listName)%>,
            width: 400
        }));

        var viewCombo = Ext4.create('Ext.form.field.ComboBox', sqvModel.makeViewComboConfig({
            name : 'viewName',
            initialValue : <%=PageFlowUtil.jsString(props.get("viewName"))%>,
            width: 400
        }));

        var submitButton = Ext4.create('Ext.button.Button', {
            text : 'Submit',
            handler : function() {
                if(myPanel){
                    if(myPanel.getForm().isValid()){

                        myPanel.getForm().submit({
                            url : <%=PageFlowUtil.jsString(h(part.getCustomizePostURL(ctx)))%>,
                            success : function(){},
                            failure : function(){}
                        });
                    }
                    else
                    {
                        Ext4.MessageBox.alert("Error Saving", "There are errors in the form.");
                    }
                }
            }
        });

        var myPanel = Ext4.create('Ext.form.Panel', {
            border : false,
            renderTo : 'SQVPicker',
            bodyStyle : 'background-color: transparent;',
            standardSubmit: true,
            items : [title, queryCombo, viewCombo, submitButton, { xtype: 'hidden', name: 'X-LABKEY-CSRF', value: LABKEY.CSRF }]
        });

    });
</script>
