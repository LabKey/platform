<%
/*
 * Copyright (c) 2007-2009 LabKey Corporation
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
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.study.assay.AssayProvider" %>
<%@ page import="java.util.List" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.study.controllers.assay.AssayController" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.pipeline.PipelineUrls" %>
<%@ page import="org.labkey.api.exp.api.ExperimentUrls" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.study.permissions.DesignAssayPermission" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    AssayController.ChooseAssayBean bean = (AssayController.ChooseAssayBean)getModelBean();
    List<AssayProvider> providers = bean.getProviders();
%>
<p>
    Each assay is a customized version of a particular assay type. 
    The assay type defines things like how the data is parsed and what kinds of analysis are provided.
</p>
<p>If you have an existing assay design to import in the XAR file format (a .xar or .xar.xml file), you can place
    the file in this folder's pipeline directory and upload using the
    <a href="<%= PageFlowUtil.urlProvider(PipelineUrls.class).urlBrowse(getViewContext().getContainer(), getViewContext().getActionURL().toString()) %>">Data Pipeline</a>
    or <a href="<%= PageFlowUtil.urlProvider(ExperimentUrls.class).getUploadXARURL(getViewContext().getContainer()) %>">upload XAR the file directly</a>.
</p>
<p>
    To create a new assay design, please choose which assay type you would like to customize with your own settings and input options.
</p>
<labkey:errors />
<form method="POST">
    <input type="hidden" name="returnURL" value="<%=h(bean.getReturnURL())%>">
    <table>
        <% for (AssayProvider provider : providers) { %>
        <tr>
            <td><input id="providerName_<%=provider.getName()%>" name="providerName" type="radio" value="<%= h(provider.getName()) %>"/></td>
            <td><label for="providerName_<%=provider.getName()%>"><strong><%= h(provider.getName())%></strong></label></td>
        </tr>
        <tr>
            <td />
            <td><label for="providerName_<%=provider.getName()%>"><%= provider.getDescription() %></label></td>
        </tr>
        <% } %>
        <tr><td>&nbsp;</td><td>&nbsp;</td></tr>
        <%
            Container project = getViewContext().getContainer().getProject();
            if (!getViewContext().getContainer().equals(project))
            {
                boolean canCreateInProject = project.hasPermission(getViewContext().getUser(), DesignAssayPermission.class);
        %>
        <tr>
            <td><input id="createInProject" name="createInProject" type="checkbox" value="true" <%=canCreateInProject ? "checked" : ""%> <%=canCreateInProject ? "" : "disabled"%>></td>
            <td><label for="createInProject"><span class="<%=canCreateInProject ? "" : "labkey-disabled"%>">
                Create assay in project folder so it can be shared in sub-folders?
                </span></label>
                <% if (!canCreateInProject) { %>
                    <br><em>Requires project administrator permission.</em>
                <% } %>
            </td>
        </tr>
        <%
            }
        %>
        <tr>
            <td />
            <td><%= generateSubmitButton("Next" )%><%= generateButton("Cancel", new ActionURL(AssayController.BeginAction.class, getViewContext().getContainer())) %></td>
        </tr>
    </table>
</form>