<%
/*
 * Copyright (c) 2007-2017 LabKey Corporation
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
<%@ page import="org.labkey.api.study.actions.AssayRunUploadForm" %>
<%@ page import="org.labkey.api.study.actions.AssayRunsAction" %>
<%@ page import="org.labkey.api.study.actions.TransformResultsAction" %>
<%@ page import="org.labkey.api.study.assay.AssayProvider" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="java.io.File" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("internal/jQuery");
        dependencies.add("completion");
    }
%>
<%
    JspView<AssayRunUploadForm> me = (JspView<AssayRunUploadForm>) HttpView.currentView();
    AssayRunUploadForm<? extends AssayProvider> bean = me.getModelBean();

    ActionURL returnURL = new ActionURL(AssayRunsAction.class, getContainer()).addParameter("rowId", bean.getRowId())
            .addParameter("uploadAttemptID", bean.getUploadAttemptID());

    if (bean.getTransformResult().getWarnings() != null)
    {
%>
        <div class="labkey-error"><%= text(bean.getTransformResult().getWarnings()) %></div>
<%
        if (!bean.getTransformResult().getFiles().isEmpty())
        {
%>
            <br/>
            <div style="font-weight: bold;">Output Files</div>
<%
            for (File file : bean.getTransformResult().getFiles())
            {
%>
                <div>
                    <a class="labkey-text-link" href='<%= new ActionURL(TransformResultsAction.class,getContainer())
                        .addParameter("name",file.getName()).addParameter("uploadAttemptID", bean.getUploadAttemptID())%>'><%= h(file.getName())%></a>
                </div>
<%
            }
        }
%>
        <br/>
        <%= button("Proceed").onClick("submitForm(getRegionForm()); return false;") %>
        <%= button("Cancel").href(returnURL).attributes("style: margin: 0 0 0 10px;") %>
<%
    }
%>

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
