<%
/*
 * Copyright (c) 2013 LabKey Corporation
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
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.geomicroarray.GEOMicroarrayController" %>
<%@ page import="org.springframework.validation.Errors" %>
<%@ page import="org.springframework.validation.ObjectError" %>
<%@ page import="java.util.List" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> resources = new LinkedHashSet<ClientDependency>();
        resources.add(ClientDependency.fromFilePath("Ext4"));
        return resources;
    }
%>

<%
    JspView<GEOMicroarrayController.FeatureAnnotationSetForm> me = (JspView<GEOMicroarrayController.FeatureAnnotationSetForm>) HttpView.currentView();
    String vendor = me.getModelBean().getVendor();
    String name = me.getModelBean().getName();
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
                    <p class="labkey-error"><%=h(HttpView.currentContext().getMessage(error))%></p>
                </li>
                <%
                    }
                %>
            </ul>
        </div>
<%
    }
%>

<div id="featureAnnotationSetForm"></div>

<script type="text/javascript">
    function renderPanel(){
        var onCancel = function(){
            window.location = LABKEY.ActionURL.buildURL('geomicroarray', 'manageFeatureAnnotationSets');
        };

        var onUpload = function(){
            var form = panel.getForm();
            if(form.isValid()){
                form.standardSubmit = true;
                form.submit();
            }
        };

        var panel = Ext4.create('Ext.form.Panel', {
            renderTo: 'featureAnnotationSetForm',
            border: false,
            bodyStyle: 'background-color: transparent;',
            bodyPadding: 10,
            width: 600,
            defaults: {
                labelWidth: 125
            },
            items: [{
                xtype: 'textfield',
                width : 580,
                labelWidth: 125,
                name: 'name',
                value: "<%=name != null ? name : ""%>",
                fieldLabel: 'Name',
                allowBlank: false
            }, {
                xtype: 'textfield',
                width : 580,
                labelWidth: 125,
                name: 'vendor',
                value: "<%=vendor != null ? vendor : ""%>",
                fieldLabel: 'Vendor',
                allowBlank: false
            }, {
                xtype: 'filefield',
                name: 'annotationFile',
                fieldLabel: 'Annotation File',
                labelWidth: 125,
                width: 580,
                allowBlank: false
            }],
            dockedItems: [{
                xtype: 'toolbar',
                style: 'background-color: transparent;',
                dock: 'bottom',
                ui: 'footer',
                items: [
                    '->',
                    {text: 'upload', handler: onUpload, scope: this},
                    {text: 'cancel', handler: onCancel, scope: this}
                ]
            }]
        });
    }

    Ext4.onReady(function(){
        renderPanel();
    });

</script>