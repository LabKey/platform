<%
/*
 * Copyright (c) 2012-2016 LabKey Corporation
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
<%@ page import="org.labkey.api.util.DateUtil"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.query.reports.ReportsController.LinkReportForm" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("dataviews");
    }
%>
<%
    JspView<LinkReportForm> me = (JspView<LinkReportForm>) HttpView.currentView();
    LinkReportForm form = me.getModelBean();
    String action = (form.isUpdate() ? "update" : "create") + "linkReport";
%>

<table>
    <%=formatMissedErrorsInTable("form", 1)%>
</table>

<div id="linkReportForm">
</div>

<script type="text/javascript">

    function getReturnUrl()
    {
        var returnUrl = LABKEY.ActionURL.getParameter('returnUrl');
        return (undefined == returnUrl ? "" : returnUrl);
    }

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

        var extraItems = [
            targetNewWindowField,
            hiddenTargetNewWindowField,
            urlTextField
        ];

        <%

        // Set additional field values if updating an existing report
        if (form.isUpdate())
        { %>

        targetNewWindowField.checked = <%=form.isTargetNewWindow()%>;
        urlTextField.setValue(<%=q(form.getLinkUrl())%>);

        extraItems.push({
            xtype: "hidden",
            name: "reportId",
            value: <%=q(form.getReportId().toString())%>
        });
        <%

        }
        %>

        var form = Ext4.create('LABKEY.study.DataViewPropertiesPanel', {
            url : LABKEY.ActionURL.buildURL('reports', <%=q(action)%>, null, {returnUrl: getReturnUrl()}),
            standardSubmit  : true,
            bodyStyle       :'background-color: transparent;',
            bodyPadding     : 10,
            border          : false,
            buttonAlign     : "left",
            width           : 575,
            fieldDefaults: {
                width : 400,
                labelWidth : 120,
                msgTarget : 'side'
            },
            disableShared   : <%=(!form.getCanChangeSharing())%>,
            visibleFields   : {
                viewName: true,
                author  : true,
                status  : true,
                datacutdate : true,
                category    : true,
                description : true,
                shared      : true
            },
 <%
        if (form.isUpdate())
        {
            %>

            record : {
                data : {
                    name: <%=q(form.getViewName())%>,
                    authorUserId: <%=form.getAuthor()%>,
                    status: <%=q(form.getStatus().name())%>,
                    refreshDate: <%=q(DateUtil.formatDate(getContainer(), form.getRefreshDate()))%>,
                    category: {rowid : <%=form.getCategory()%>},
                    description: <%=q(form.getDescription())%>,
                    shared: <%=form.getShared()%>
                }
            },

          <%
        }
%>
            extraItems : extraItems,
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
