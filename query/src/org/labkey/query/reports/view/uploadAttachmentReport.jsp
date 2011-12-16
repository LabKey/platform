<%
/*
 * Copyright (c) 2007-2011 LabKey Corporation
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

        Ext4.define('Dataset.Browser.Category', {
            extend : 'Ext.data.Model',
            fields : [
                {name : 'created',      type : 'date'},
                {name : 'createdBy'                  },
                {name : 'displayOrder', type : 'int' },
                {name : 'label'                      },
                {name : 'modfied',      type : 'date'},
                {name : 'modifiedBy'                 },
                {name : 'rowid',        type : 'int' }
            ]
        });

        function initializeCategoriesStore  () {
            var config = {
                pageSize: 100,
                model   : 'Dataset.Browser.Category',
                autoLoad: true,
                autoSync: false,
                proxy   : {
                    type   : 'ajax',
                    url    : LABKEY.ActionURL.buildURL('study', 'getCategories.api'),
                    reader : {
                        type : 'json',
                        root : 'categories'
                    },
                    listeners : {
                        exception : function(p, response, operations, eOpts)
                        {
                        }
                    }
                },
                listeners: {
                    scope: this,
                    load : function(s, recs, success, operation, ops) {
                        s.sort('displayOrder', 'ASC');
                    }
                }
            };

            return Ext4.create('Ext.data.Store', config);
        }

        var label = {
            xtype: 'textfield',
            name: "label",
            fieldLabel: "Report Name"
        };
        var reportDateString = {
            xtype: 'datefield',
            name: "reportDateString",
            fieldLabel: "Report Date"
        };
        var description = {
            xtype: 'textareafield',
            grow: true,
            name: "description",
            fieldLabel: "Description"
        };

        var store = initializeCategoriesStore();

        this.category = Ext4.create('Ext.form.field.ComboBox', {
            xtype: 'combobox',
            name: 'category',
            store: store,
            editable: false,
            displayField: 'label',
            valueField: 'rowid',
            fieldLabel: 'Category'
        });

        var shared = {
            xtype: 'checkbox',
            value: false,
            inputValue: false,
            boxLabel: 'Share this report with all users?',
            name: "shared",
            fieldLabel: "Shared",
            listeners: {
                change: function(cmp, newVal, oldVal){
                    cmp.inputValue = newVal;
                }
            }
        };

        var hiddenShared = {
            xtype: 'hidden',
            name: "@shared"
        };

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
//            width: 350,
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
            items: [
                localFileUploadRadio,
                serverFileRadio
            ]
        };

        var fileUploadField = Ext4.create('Ext.form.field.File', {
            name: 'uploadFile',
            id: 'uploadFile',
            fieldLabel: "Choose a file"
        });

        var serverFileTextField = Ext4.create('Ext.form.field.Text', {
            name: "filePath",
            hidden: true,
            fieldLabel: "Full path on server"
        });

        var form = Ext4.create('Ext.form.Panel', {
            renderTo: "attachmentReportForm",
            url: LABKEY.ActionURL.buildURL('reports', 'uploadReport', null, {returnUrl: LABKEY.ActionURL.getParameter('returnUrl')}),
            standardSubmit: true,
            bodyStyle:'background-color: transparent;',
            border: false,
            buttonAlign: "left",
            width: 500,
            fieldDefaults: {
                width: 500,
                labelSeparator: '',
                labelWidth: 125
            },
            items: [
                label,
                reportDateString,
                description,
                this.category,
                shared,
                hiddenShared,
                fileUploadRadioGroup,
                fileUploadField,
                serverFileTextField
            ],
            buttons: [
                {
                    text: 'Submit',
                    scope: this,
                    handler: function() {
                        form.submit();
                    }
                },
                {
                    text: 'Cancel',
                    handler: function(){
                        window.location = LABKEY.ActionURL.getParameter('returnUrl');
                    }
                }
            ]
        });
    });
</script>
