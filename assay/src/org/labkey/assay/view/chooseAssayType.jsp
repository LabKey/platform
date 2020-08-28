<%
/*
 * Copyright (c) 2008-2019 LabKey Corporation
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
<%@ page import="org.labkey.api.assay.AssayProvider" %>
<%@ page import="org.labkey.api.assay.AssayService" %>
<%@ page import="org.labkey.api.assay.AssayUrls" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.exp.api.ExperimentUrls" %>
<%@ page import="org.labkey.api.files.FileContentService" %>
<%@ page import="org.labkey.api.pipeline.PipelineUrls" %>
<%@ page import="org.labkey.api.util.HelpTopic" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.util.Pair" %>
<%@ page import="org.labkey.assay.AssayController" %>
<%@ page import="java.util.LinkedHashMap" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    AssayController.ChooseAssayBean bean = (AssayController.ChooseAssayBean)getModelBean();
    List<AssayProvider> providers = bean.getProviders();
    Map<String, String> locations = new LinkedHashMap<>();
    String defaultLocation = null;

    FileContentService fileContentService = FileContentService.get();
    boolean isCloudRoot = null != fileContentService && fileContentService.isCloudRoot(getContainer());
    for (Pair<Container, String> entry : AssayService.get().getLocationOptions(getContainer(), getUser()))
    {
        locations.put(entry.getKey().getId(), entry.getValue());
        if (defaultLocation == null)
            defaultLocation = entry.getKey().getId();
    }
%>
<p>
    Each assay design is a customized version of a particular assay type.
    The assay type defines things like how the data is parsed and what kinds of analysis tools are provided.
</p>
<p>If you want to import an existing assay design in the <a href="<%= h(new HelpTopic("XarTutorial").getHelpTopicHref()) %>">XAR file format</a> (a .xar or .xar.xml file), you can
    <%= link("upload", urlProvider(ExperimentUrls.class).getUploadXARURL(getContainer())) %>it directly
    or upload the file into this folder's pipeline directory and import using the
    <%= link("Data Pipeline", urlProvider(PipelineUrls.class).urlBrowse(getContainer(), getActionURL())) %>
</p>
<p>
    To create a new assay design, please choose which assay type you would like to customize with your own settings and input options.
</p>
<labkey:errors />
<labkey:form method="POST">
    <%= generateReturnUrlFormField(bean.getReturnURL()) %>
    <table>
        <% for (AssayProvider provider : providers) {
            boolean isCloudAndUnsupported = (isCloudRoot && null != provider.getPipelineProvider() && !provider.getPipelineProvider().supportsCloud());
        %>
        <tr>
            <td><input id="providerName_<%=h(provider.getName())%>" name="providerName" type="radio" value="<%= h(provider.getName()) %>"
                <%=h(isCloudAndUnsupported ? "disabled" : "")%>
            /></td>
            <td><label for="providerName_<%=h(provider.getName())%>"><strong><%= h(provider.getName())%></strong></label></td>
        </tr>
        <tr>
            <td>&nbsp;</td>
            <td><label for="providerName_<%=h(provider.getName())%>" style="font-weight: normal;"><%= text(provider.getDescription()) %>
                <%=h(isCloudAndUnsupported ? " (Does not support using cloud-based storage.)" : "")%>
            </label></td>
        </tr>
        <% } %>
        <tr><td>&nbsp;</td><td>&nbsp;</td></tr>
        <%
            if (!locations.isEmpty()) {
        %>
        <tr>
            <td></td>
            <td><span><strong>Assay Location</strong>&nbsp;Create assay in project or shared locations so it is visible in subfolders.</span></td>
        </tr>
        <tr>
            <td></td>
            <td><select name="assayContainer" id="assayContainer"><labkey:options value="<%=h(defaultLocation)%>" map="<%=locations%>"></labkey:options></select></td>
        </tr>
        <tr><td>&nbsp;</td><td>&nbsp;</td></tr>
        <%
            }
        %>
        <tr>
            <td />
                <%
                    if(null != bean.getReturnURL()) {
                %>
                <td><%= button("Next" ).submit(true) %><%= button("Cancel").href(bean.getReturnURL()) %></td>
                <%
                    } else {
                %>
                <td><%= button("Next" ).submit(true) %><%= button("Cancel").href(PageFlowUtil.urlProvider(AssayUrls.class).getBeginURL(getContainer())) %></td>
                <%
                    }
                %>
            </td>
        </tr>
    </table>
</labkey:form>