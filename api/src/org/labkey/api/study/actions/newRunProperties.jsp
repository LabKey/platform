<%
/*
 * Copyright (c) 2007-2016 LabKey Corporation
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
<%@ page import="org.labkey.api.study.actions.AssayRunsAction" %>
<%@ page import="org.labkey.api.study.actions.TransformResultsAction" %>
<%@ page import="org.labkey.api.study.assay.AssayProvider" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.springframework.validation.FieldError" %>
<%@ page import="org.springframework.validation.ObjectError" %>
<%@ page import="java.io.File" %>
<%@ page import="java.util.Map" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("internal/jQuery");
        dependencies.add("Ext4"); // required for completion.js
        dependencies.add("completion.js");
    }
%>
<%
    JspView<AssayRunUploadForm> me = (JspView<AssayRunUploadForm>) HttpView.currentView();
    AssayRunUploadForm<? extends AssayProvider> bean = me.getModelBean();

    ActionURL returnURL = new ActionURL(AssayRunsAction.class, getContainer()).addParameter("rowId", bean.getRowId());
%>
<table>
<%
    if (bean.getTransformResult().getWarnings() != null)
    {
%>
        <tr>
            <td class="labkey-error" colspan="2"><%= text(bean.getTransformResult().getWarnings()) %></td>
        </tr>
        <tr>
            <td>
                <%= button("Proceed").onClick("submitForm(getRegionForm()); return false;") %>
                <%= button("Cancel").href(returnURL).attributes("style: margin: 0 0 0 10px;") %>
            </td>
        </tr>
        <tr>
            <td>&nbsp;</td>
        </tr>
        <tr class="labkey-wp-header">
            <td colspan="2">Output Files</td>
        </tr>
        <% for (File file : bean.getTransformResult().getFiles())
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

                <%
                if (bean.getErrors().hasFieldErrors("transform"))
                {
                    for (FieldError fe : bean.getErrors().getFieldErrors())
                    {
                        if (fe.getField().equals("transform"))
                        {
                %>
                            <%= text(fe.getDefaultMessage())%>
                            <br>
                <%
                        }
                    }
                %>
                <%
                }
                else
                {
                    for (ObjectError err : bean.getErrors().getAllErrors())
                    {
                %>
                    <%= h(err.getDefaultMessage()) %>
                    <br>
                <%  }
                }
                %>
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
        <td class="labkey-form-label" nowrap>Name</td>
        <td width="100%"><%= h(bean.getProtocol().getName()) %></td>
    </tr>
    <% if (!StringUtils.isEmpty(bean.getProtocol().getDescription())) { %>
        <tr>
            <td class="labkey-form-label" nowrap>Description</td>
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
                    <td class="labkey-form-label" nowrap><%= h(entry.getKey().getPropertyDescriptor().getNonBlankCaption()) %></td>
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

    (function($) {

        <%-- Used by button handlers --%>
        window.getRegionForm = function() {
            var form = $('form[lk-region-form="ExperimentRun"]');
            if (form.length == 1) {
                return form[0];
            }
            else {
                throw new Error('Unable to resolve region form. Did the "lk-region-form" selector change?');
            }
        };

        <%
        if (bean.getTransformResult().getWarnings() != null)
        {
        %>
        $(function() {

            var form = getRegionForm();

            <%-- Mark inputs as disabled or readonly and check the option to use the previous result --%>
            var elements = form.elements;
            for (var i = 0, len = elements.length; i < len; ++i) {
                if (elements[i].type != "hidden") {
                    if (elements[i].type == "radio") {
                        if (elements[i].id == "Previouslyuploadedfiles")
                            elements[i].checked = true;
                        else
                            elements[i].disabled = true;
                    }
                    else
                        elements[i].readOnly = true;
                }
            }

            <%-- disable buttons in the form --%>
            $('.labkey-button', form).addClass('labkey-disabled-button');

            <%-- populate name field --%>
            $('input[name=name]').val(<%= PageFlowUtil.jsString(bean.getName()) %>);
            <% if (bean.getComments() != null) { %>
            $('input[name=comments]').val(<%= PageFlowUtil.jsString(bean.getComments()) %>);
            <% } %>

            hideAllCollectors();
            showCollector('Previously uploaded files');
        });
        <%
        }
        %>

    })(jQuery);

</script>
