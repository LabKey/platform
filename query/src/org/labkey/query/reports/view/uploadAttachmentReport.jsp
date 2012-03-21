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
<%@ page import="org.labkey.query.reports.ReportsController.UploadForm" %>
<%@ page import="org.springframework.validation.ObjectError" %>
<%@ page import="java.util.List" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<script type="text/javascript">
    LABKEY.requiresExt4Sandbox(true);
    LABKEY.requiresScript("study/DataViewPropertiesPanel.js");
</script>
<%
    JspView<UploadForm> me = (JspView<UploadForm>) HttpView.currentView();
    ReportsController.UploadForm bean = me.getModelBean();

    boolean canUseDiskFile = HttpView.currentContext().getUser().isAdministrator() && bean.getReportId() == null;
%>

<table>
<%
    for (ObjectError e : (List<ObjectError>) bean.getErrors().getAllErrors())
    {
%>      <tr><td colspan=3><font class="labkey-error"><%=h(HttpView.currentContext().getMessage(e))%></font></td></tr><%
    }
%>
</table>

<div id="attachmentReportForm">
</div>

<script type="text/javascript">

    Ext4.onReady(function(){

        var localFileUploadRadio = {
            boxLabel: 'Upload File',
            name: 'fileUploadRadio',
            inputValue: 'local',
            checked: true,
            listeners: {
                scope: this,
                change: function(cmp, newVal, oldVal){
                    if(newVal){
                        serverFileTextField.setVisible(false);
                        serverFileTextField.disable();
                        fileUploadField.setVisible(true);
                        fileUploadField.enable();
                    }
                }
            }
        };

        var serverFileRadio = {
            boxLabel: 'Use a file on server localhost',
            name: 'fileUploadRadio',
            inputValue: 'server',
            listeners: {
                scope: this,
                change: function(cmp, newVal, oldVal){
                    if(newVal){
                        serverFileTextField.setVisible(true);
                        serverFileTextField.enable();
                        fileUploadField.setVisible(false);
                        fileUploadField.disable();
                    }
                }
            }
        };

        var fileUploadRadioGroup = {
            xtype: 'radiogroup',
            fieldLabel: 'Upload Type',
            columns: [125, 100],
            items: [
                localFileUploadRadio,
                serverFileRadio
            ]
        };

        var fileUploadField = Ext4.create('Ext.form.field.File', {
            name: 'uploadFile',
            id: 'uploadFile',
            fieldLabel: "Choose a file",
            allowBlank: false
        });

        var serverFileTextField = Ext4.create('Ext.form.field.Text', {
            name: "filePath",
            hidden: true,
            disabled: true,
            fieldLabel: "Full path on server",
            allowBlank: false
        });

        var form = Ext4.create('LABKEY.study.DataViewPropertiesPanel', {
            url : LABKEY.ActionURL.buildURL('reports', 'uploadReport', null, {returnUrl: LABKEY.ActionURL.getParameter('returnUrl')}),
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
                fileUploadRadioGroup,
                fileUploadField,
                serverFileTextField
            ],
            renderTo    : 'attachmentReportForm',
            buttons     : [{
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
        });
    });
</script>
