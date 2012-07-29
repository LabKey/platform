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
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView"%>
<%@ page import="org.labkey.query.reports.ReportsController" %>
<%@ page import="org.labkey.query.reports.ReportsController.LinkReportForm" %>
<%@ page import="org.springframework.validation.ObjectError" %>
<%@ page import="java.util.List" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<script type="text/javascript">
    LABKEY.requiresExt4Sandbox(true);
    LABKEY.requiresScript("study/DataViewPropertiesPanel.js");
</script>
<%
    JspView<LinkReportForm> me = (JspView<LinkReportForm>) HttpView.currentView();
    LinkReportForm bean = me.getModelBean();
%>

<table>
    <%
        for (ObjectError e : (List<ObjectError>) me.getErrors().getAllErrors())
        {
    %>      <tr><td colspan=3><font class="labkey-error"><%=h(HttpView.currentContext().getMessage(e))%></font></td></tr><%
    }
%>
</table>

<div id="linkReportForm">
</div>

<script type="text/javascript">

    Ext4.onReady(function(){

        var targetNewWindowField = {
            id: "targetNewWindow",
            xtype: "checkbox",
            name: "targetNewWindow",
            fieldLabel: "Target",
            boxLabel: "Open link report in new window?",
            checked: true
        };

        var hiddenTargetNewWindowField = {
            xtype: "hidden",
            name: "@targetNewWindow"
        };

        var urlTextField = Ext4.create('Ext.form.field.Text', {
            name: "linkUrl",
            fieldLabel: "Link URL",
            allowBlank: false,
            validator: function (value) {
                if (!(value.charAt(0) == '/' || value.indexOf("http://") == 0 || value.indexOf("https://") == 0))
                    return "URL must be absolute (starting with http or https) or relative to this server (start with '/')";

                return true;
            }
        });

        var form = Ext4.create('LABKEY.study.DataViewPropertiesPanel', {
            url : LABKEY.ActionURL.buildURL('reports', 'createLinkReport', null, {returnUrl: LABKEY.ActionURL.getParameter('returnUrl')}),
            standardSubmit  : true,
            bodyStyle       :'background-color: transparent;',
            bodyPadding     : 10,
            border          : false,
            buttonAlign     : "left",
            width           : 575,
            fieldDefaults: {
                width : 500,
                labelWidth : 125,
                msgTarget : 'side'
            },
            visibleFields   : {
                author  : true,
                status  : true,
                modifieddate: true,
                datacutdate : true,
                category    : true,
                description : true,
                shared      : true
            },
            extraItems : [
                targetNewWindowField,
                hiddenTargetNewWindowField,
                urlTextField
            ],
            renderTo    : 'linkReportForm',
            dockedItems: [{
                xtype: 'toolbar',
                dock: 'bottom',
                ui: 'footer',
                style: 'background-color: transparent;',
                items: [{
                    text : 'Save',
                    handler : function(btn) {
                        var form = btn.up('form').getForm();
                        if (form.isValid())
                            form.submit();
                    },
                    scope   : this
                },{
                    text: 'Cancel',
                    handler: function(){
                        if(LABKEY.ActionURL.getParameter('returnUrl')){
                            window.location = LABKEY.ActionURL.getParameter('returnUrl');
                        } else {
                            window.location = LABKEY.ActionURL.buildURL('reports', 'manageViews');
                        }
                    }
                }]
            }]
        });
    });
</script>
