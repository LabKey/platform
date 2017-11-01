<%
/*
 * Copyright (c) 2010-2017 LabKey Corporation
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
<%@ page import="org.labkey.api.assay.dilution.DilutionAssayRun" %>
<%@ page import="org.labkey.api.assay.nab.RenderAssayBean" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.nab.NabUrls" %>
<%@ page import="org.labkey.api.query.QueryView" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.security.permissions.DeletePermission" %>
<%@ page import="org.labkey.api.security.permissions.InsertPermission" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<RenderAssayBean> me = (JspView<RenderAssayBean>) HttpView.currentView();
    RenderAssayBean bean = me.getModelBean();
    Container c = getContainer();
    User user = getUser();
    DilutionAssayRun assay = bean.getAssay();

    ActionURL rerunURL = assay.getProvider().getImportURL(getContainer(), assay.getProtocol());
    rerunURL.addParameter("reRunId", bean.getRunId());

    if (bean.needsCurveNote())
    {
        boolean deleteAndInsertPerms = c.hasPermission(user, DeletePermission.class) && c.hasPermission(user, InsertPermission.class);
%>
<tr>
    <td>
        <p>This run is shown with a <strong><%= h(assay.getRenderedCurveFitType().getLabel()) %></strong> curve fit,
        but is saved with a <strong><%= h(assay.getSavedCurveFitType() != null ? assay.getSavedCurveFitType().getLabel() : "unknown") %></strong> curve fit.
        To replace<br>the saved data with the displayed data,
        <%
        if (deleteAndInsertPerms)
        {
        %>
            you must <a href="<%= text(rerunURL.getLocalURIString()) %>">delete and re-import</a> the run.
        <%
        }
        else
        {
        %>
            a user with appropriate permissions must delete and re-import the run.
        <%
        }
        %>
        </p>
    </td>
</tr>
<%
    }
    if (bean.needsNewRunNote())
    {
%>
<tr>
    <td class="labkey-form-label">
        <p>This run has been automatically saved.
    <%
            if (c.hasPermission(user, DeletePermission.class))
            {
                ActionURL deleteUrl = PageFlowUtil.urlProvider(NabUrls.class).urlDeleteRun(c);
                deleteUrl.addParameter("rowId", bean.getRunId());
    %>
    <%= button("Delete Run").href(deleteUrl).onClick("return confirm('Permanently delete this run?')") %>
    <%= button("Delete and Re-Import").href(rerunURL) %>
    <%
            }
    %>
        </p>
    </td>
</tr>
<%
    }
    if (bean.needsDupFileNote())
    {
        QueryView duplicateDataFileView = bean.getDuplicateDataFileView(getViewContext());
        if (duplicateDataFileView != null)
        {
%>
<tr>
    <td class="labkey-form-label">
        <p><span class="labkey-error"><b>WARNING</b>: The following runs use a data file by the same name.</span><br><br>
        <% include(duplicateDataFileView, out); %></p>
    </td>
</tr>
<%
        }
    }
%>
