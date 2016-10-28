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
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView"%>
<%@ page import="org.labkey.query.reports.ReportsController" %>
<%@ page import="org.springframework.validation.ObjectError" %>
<%@ page import="java.util.List" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("Ext4");
        dependencies.add("dataviews");
        dependencies.add("sqv");
    }
%>
<%
    JspView<ReportsController.QueryReportForm> me = (JspView<ReportsController.QueryReportForm>) HttpView.currentView();
%>

<table>
    <%
        for (ObjectError e : me.getErrors().getAllErrors())
        {
    %>      <tr><td colspan=3><font class="labkey-error"><%=h(getViewContext().getMessage(e))%></font></td></tr><%
    }
%>
</table>

<div id="queryReportForm">
</div>

<script type="text/javascript">

    var getReturnUrl = function() {
        var returnUrl = LABKEY.ActionURL.getParameter('returnUrl');
        return (undefined == returnUrl ? "" : returnUrl);
    };

    Ext4.onReady(function() {

        var sqvModel = Ext4.create('LABKEY.sqv.Model', {});
        var items = [];

        items.push(sqvModel.makeSchemaComboConfig({
            name        : 'selectedSchemaName',
            editable    : false,
            emptyText   : 'None',
            msgTarget   : 'qtip'
        }));

        items.push(sqvModel.makeQueryComboConfig({
            name        : 'selectedQueryName',
            editable    : false,
            typeAhead   : 'true',
            emptyText   : 'None',
            msgTarget   : 'qtip'
        }));

        items.push(sqvModel.makeViewComboConfig({
            name        : 'selectedViewName',
            editable    : false,
            typeAhead   : 'true',
            emptyText   : 'None'
        }));

        var querySchemaPanel = Ext4.create('Ext.panel.Panel', {
            frame       : false,
            border      : false,
            bodyStyle   : {
                background: 'transparent'
            },
            items       : items
        });

        Ext4.create('LABKEY.study.DataViewPropertiesPanel', {
            renderTo    : 'queryReportForm',
            url : LABKEY.ActionURL.buildURL('reports', 'createQueryReport', null, {returnUrl: getReturnUrl()}),
            standardSubmit  : true,
            bodyStyle       :'background-color: transparent;',
            bodyPadding     : 10,
            border          : false,
            buttonAlign     : "left",
            width           : 575,
            fieldDefaults: {
                width : 500,
                labelWidth : 120,
                msgTarget : 'qtip'
            },
            visibleFields   : {
                viewName: true,
                author  : true,
                status  : true,
                modifieddate: true,
                datacutdate : true,
                category    : true,
                description : true,
                shared      : true
            },
            extraItems : [querySchemaPanel, {xtype: 'hiddenfield', name: 'srcURL'}],
            dockedItems: [{
                xtype: 'toolbar',
                dock: 'bottom',
                ui: 'footer',
                style: 'background-color: transparent;',
                items: [{
                    text : 'Save',
                    handler : function(btn) {
                        var form = btn.up('form').getForm();
                        if (form.isValid()) {
                            form.submit({submitEmptyText : false});
                        }
                    },
                    scope   : this
                },{
                    text: 'Cancel',
                    handler: function() {
                        if (LABKEY.ActionURL.getParameter('returnUrl')) {
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
