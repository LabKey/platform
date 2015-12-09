<%
/*
 * Copyright (c) 2007-2014 LabKey Corporation
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
<%@ page import="org.apache.commons.lang3.StringUtils"%>
<%@ page import="org.labkey.api.exp.property.DomainProperty" %>
<%@ page import="org.labkey.api.study.actions.AssayRunUploadForm" %>
<%@ page import="org.labkey.api.study.actions.TransformResultsAction" %>
<%@ page import="org.labkey.api.study.assay.AssayProvider" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.springframework.validation.ObjectError" %>
<%@ page import="java.io.File" %>
<%@ page import="java.util.Map" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<script type="text/javascript">LABKEY.requiresScript('completion.js');</script>

<%
    JspView<AssayRunUploadForm> me = (JspView<AssayRunUploadForm>) HttpView.currentView();
    AssayRunUploadForm<? extends AssayProvider> bean = me.getModelBean();
%>
<table>
<%
    if (bean.getTransformResult().getWarnings() != null)
    {
%>
        <tr>
            <td class="labkey-error" colspan="2"><%= bean.getTransformResult().getWarnings() %></td>
        </tr>
        <tr>
            <td><div id="overrideBtn"></div></td>
        </tr>
        <tr>
            <td>&nbsp;</td>
        </tr>
        <tr class="labkey-wp-header">
            <td colspan="2">Output Files</td>
        </tr>
        <% for(File file : bean.getTransformResult().getFiles())
        { %>
            <tr>
                <td colspan="2"><a class="labkey-text-link" href='<%= new ActionURL(TransformResultsAction.class,getContainer())
                    .addParameter("name",file.getName()).addParameter("uploadAttemptId", bean.getUploadAttemptID())%>'><%= h(file.getName())%></a></td>
            </tr>
        <% } %>
        <tr>
            <td>&nbsp;</td>
        </tr>
<%
    }
    else if (bean.isSuccessfulUploadComplete())
    {
%>
        <tr>
            <td class="labkey-header-large" colspan="2">Upload successful.  Upload another run below, or click Cancel to view previously uploaded runs.</td>
        </tr>
        <tr>
            <td>&nbsp;</td>
        </tr>
<%
    }
    else
    {
%>
        <tr>
            <td class="labkey-error" id="importErrors" colspan="2">
                <% for(ObjectError err : bean.getErrors().getAllErrors())
                {
                %>
                    <%= h(err.getDefaultMessage()) %>
                    <br>
                <%}%>
            </td>
        </tr>
        <tr>
            <td>&nbsp;</td>
        </tr>
<%  } %>
    <tr class="labkey-wp-header">
        <td colspan="2">Assay Properties</td>
    </tr>
    <tr>
        <td class="labkey-form-label" nowrap="true">Name</td>
        <td width="100%"><%= h(bean.getProtocol().getName()) %></td>
    </tr>
    <% if (!StringUtils.isEmpty(bean.getProtocol().getDescription())) { %>
        <tr>
            <td class="labkey-form-label" nowrap="true">Description</td>
            <td><%= h(bean.getProtocol().getProtocolDescription()) %></td>
        </tr>
    <% } %>
    <% if (!bean.getBatchProperties().isEmpty())
    { %>
        <tr><td>&nbsp;</td></tr>
        <tr class="labkey-wp-header">
            <td colspan="2">Batch Properties</td>
        </tr>
        <%
            for (Map.Entry<DomainProperty, String> entry : bean.getBatchProperties().entrySet())
            {
                %>
                <tr>
                    <td class="labkey-form-label" nowrap="true"><%= h(entry.getKey().getPropertyDescriptor().getNonBlankCaption()) %></td>
                    <td>
                        <%= h(bean.getBatchPropertyValue(entry.getKey().getPropertyDescriptor(), entry.getValue())) %>
                    </td>
                </tr>
                <%
            }
        }
    %>
    <tr><td>&nbsp;</td></tr>
</table>

<script type="text/javascript">

    Ext4.onReady(function(){
        Ext4.create('Ext.Button', {
            text: 'Proceed',
            renderTo: Ext4.get("overrideBtn"),
            handler: function() {
                this.form = document.getElementById('ExperimentRun');
                if (isTrueOrUndefined(function(){
                            document.forms['ExperimentRun'].multiRunUpload.value = '<%= bean.isMultiRunUpload()%>';
                            this.className += " labkey-disabled-button";
                        }.call(this))) {
                    submitForm(document.getElementById('ExperimentRun'));
                    return false;
                }
            }
        });
        Ext4.create('Ext.Button', {
            text: 'Cancel',
            renderTo: Ext4.get("overrideBtn"),
            margin: '0 0 0 10',
            handler: function() {
                var row = document.getElementsByName('rowId')[0].value;
                location = LABKEY.ActionURL.buildURL('assay', 'assayruns', null, {rowId: row});
            }
        });

        <%
        if (bean.getTransformResult().getWarnings() != null)
        {
        %>
        var form = document.getElementById("ExperimentRun");
        var elements = form.elements;
        for (var i = 0, len = elements.length; i < len; ++i) {
            if(elements[i].type != "hidden") {
                if (elements[i].type == "radio") {
                    if(elements[i].id == "Previouslyuploadedfiles")
                        elements[i].checked = true;
                    else
                        elements[i].disabled = true;
                }
                else
                    elements[i].readOnly = true;
            }
        }


        var buttons = document.querySelectorAll('.labkey-button');
        len = buttons.length;
        for (i = 0; i < len; i++) {
            buttons[i].setAttribute("class", "labkey-disabled-button");
        }

        // populate name field
        document.getElementsByName('name')[0].value = '<%= bean.getName()%>';
        if(<%= bean.getComments()%> != null)
            document.getElementsByName('comments')[0].value = '<%= bean.getComments()%>';

        hideAllCollectors();
        showCollector('Previously uploaded files');
        <%
        }
        %>
    });

</script>
