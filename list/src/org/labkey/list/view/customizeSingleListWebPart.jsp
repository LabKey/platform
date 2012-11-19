<%
/*
 * Copyright (c) 2006-2012 LabKey Corporation
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
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="java.util.Comparator" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.TreeMap" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%!

    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> resources = new LinkedHashSet<ClientDependency>();
        //Need to include the Helper for a use of the form panel configuration.
        resources.add(ClientDependency.fromFilePath("Ext4"));
        resources.add(ClientDependency.fromFilePath("SQVSelector.js"));
        return resources;
    }
%>
<%
    HttpView<Portal.WebPart> me = (HttpView<Portal.WebPart>) HttpView.currentView();
    Portal.WebPart part = me.getModelBean();
    ViewContext ctx = me.getViewContext();
    Map<String, String> props = part.getPropertyMap();

    final TreeMap<String, String> listOptions = new TreeMap<String, String>();

    Map<String, ListDefinition> lists = ListService.get().getLists(ctx.getContainer());
    for (String name : lists.keySet())
    {
        listOptions.put(String.valueOf(lists.get(name).getListId()), name);
    }

    //to sort on values
    TreeMap<String, String> sortedListOptions = new TreeMap<String, String>(new Comparator<String>() {
        public int compare(String s1, String s2) {
            return listOptions.get(s1).compareTo(listOptions.get(s2));
        }
    });
    sortedListOptions.putAll(listOptions);

%>

This webpart displays data from a single list.<br><br>

If you want to let users change the list that's displayed or customize the view themselves then use the query webpart.<br><br>

<div id="SQVPicker"></div>
<script type="text/javascript">
    Ext4.onReady(function(){
        var sqvModel = Ext4.create('LABKEY.SQVModel', {});
        var title = Ext4.create('Ext.form.field.Text', {
            name : 'title',
            value :  <%=PageFlowUtil.jsString(h(props.get("title")))%>,
            fieldLabel : 'Title'
        });
        var queryCombo = ('Ext.form.field.ComboBox', sqvModel.makeQueryComboConfig({
            defaultSchema : 'lists',
            name : 'listId',
            valueField : 'listId',
            initialValue : <%=PageFlowUtil.jsString(props.get("listId"))%>
        }));

        var viewCombo = Ext4.create('Ext.form.field.ComboBox', sqvModel.makeViewComboConfig({
            name : 'viewName',
            initialValue : <%=PageFlowUtil.jsString(h(props.get("viewName")))%>
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
            items : [title, queryCombo, viewCombo, submitButton]
        });

    });
</script>
