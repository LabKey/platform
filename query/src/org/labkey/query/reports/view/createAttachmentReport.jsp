<%
/*
 * Copyright (c) 2007-2012 LabKey Corporation
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
<%@ page import="org.labkey.query.reports.ReportsController.AttachmentReportForm" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<script type="text/javascript">
    LABKEY.requiresExt4Sandbox(true);
    LABKEY.requiresScript("study/DataViewPropertiesPanel.js");
</script>
<%
    JspView<ReportsController.AttachmentReportForm> me = (JspView<ReportsController.AttachmentReportForm>) HttpView.currentView();
    ReportsController.AttachmentReportForm bean = me.getModelBean();

    boolean canUseDiskFile = HttpView.currentContext().getUser().isAdministrator() && bean.getReportId() == null;
%>

<table>
    <%=formatMissedErrorsInTable("form", 1)%>
</table>

<div id="attachmentReportForm">
</div>

<script type="text/javascript">

    Ext4.onReady(function(){

        var fileUploadField = Ext4.create('Ext.form.field.File', {
            name: 'uploadFile',
            id: 'uploadFile',
            fieldLabel: "Choose a file",
            allowBlank: false
        });

        var extraItems;

        <% if (canUseDiskFile) { %>
        var serverFileTextField = Ext4.create('Ext.form.field.Text', {
            name: "filePath",
            hidden: true,
            disabled: true,
            fieldLabel: "Path on server",
            allowBlank: false
        });

        var fileUploadRadioGroup = {
            xtype: 'radiogroup',
            fieldLabel: 'Attachment Type',
            columns: 1,
            items: [{
                boxLabel: 'Upload file to server',
                name: 'attachmentType',
                inputValue: '<%=AttachmentReportForm.AttachmentReportType.local.toString()%>',
                checked: true,
                inputField: fileUploadField
            },{
                boxLabel: 'Full file path on server',
                name: 'attachmentType',
                inputValue: '<%=AttachmentReportForm.AttachmentReportType.server.toString()%>',
                inputField: serverFileTextField
            }],
            listeners: {
                scope: this,
                change: function (field, newVal, oldVal, opts) {
                    var value = newVal['attachmentType'];
                    field.items.each(function (item) {
                        if (item.inputValue === value) {
                            item.inputField.setVisible(true);
                            item.inputField.setDisabled(false);
                        } else {
                            item.inputField.setVisible(false);
                            item.inputField.setDisabled(true);
                        }
                    });
                }
            }
        };

        extraItems = [ fileUploadRadioGroup, fileUploadField, serverFileTextField ];
        <% } else { %>

        var attachmentTypeField = {
            xtype:'hidden',
            name:'attachmentType',
            value:'<%=AttachmentReportForm.AttachmentReportType.local.toString()%>'
        };
        extraItems = [ attachmentTypeField, fileUploadField ];
        <% } %>

        var form = Ext4.create('LABKEY.study.DataViewPropertiesPanel', {
            url : LABKEY.ActionURL.buildURL('reports', 'createAttachmentReport', null, {returnUrl: LABKEY.ActionURL.getParameter('returnUrl')}),
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
                datacutdate : true,
                category    : true,
                description : true,
                shared      : true
            },
            extraItems : extraItems,
            renderTo    : 'attachmentReportForm',
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
