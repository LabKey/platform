<%
/*
 * Copyright (c) 2008-2017 LabKey Corporation
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
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.files.FileContentService" %>
<%@ page import="org.labkey.api.pipeline.PipelineUrls" %>
<%@ page import="org.labkey.api.pipeline.view.SetupForm" %>
<%@ page import="org.labkey.api.services.ServiceRegistry" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="java.io.File" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    JspView<SetupForm> thisView = (JspView<SetupForm>) HttpView.currentView();
    SetupForm bean = thisView.getModelBean();
    Container c = getContainer();

    // the default project pipeline root is based on the file root
    String projectDefaultRoot = "";
    String folderRadioBtnLabel = "Set a pipeline override";
    boolean hasInheritedOverride = SetupForm.hasInheritedOverride(c);

    File fileRoot = ServiceRegistry.get().getService(FileContentService.class).getFileRoot(c);
    if (fileRoot != null)
    {
        projectDefaultRoot = fileRoot.getAbsolutePath();
    }

    if (bean.getConfirmMessage() != null)
    { %>
            <p class="labkey-message"><%=h(bean.getConfirmMessage()) %></p>
    <% }
%>

<labkey:errors />
<labkey:form enctype="multipart/form-data" method="POST" action="">
    <table>
        <tr><td></td></tr>
        <tr><td colspan="10">
            The LabKey Data Processing Pipeline allows you to process and import data files with tools we supply, or
            with tools you build on your own. If you have a pre-existing directory that contains the files you want
            to process, you can set a pipeline override to allow the data processing pipeline to operate on the
            files in your preferred directory instead of the one that LabKey creates for each folder.
<%      if (bean.isShowAdditionalOptionsLink()) { %>
            For additional pipeline options, <a href="<%=h(urlProvider(PipelineUrls.class).urlSetup(c))%>">click here</a>.
<%      } %>
        </td></tr>
        <tr><td></td></tr>
        <tr>
            <td colspan="10">
                <table>
                    <tr style="height: 1.75em">
                        <td><input type="radio" name="pipelineRootOption" id="pipeOptionSiteDefault" value="<%= h(SetupForm.SITE_DEFAULT_TYPE) %>"<%=disabled(hasInheritedOverride)%>
                            <%=checked(SetupForm.SITE_DEFAULT_TYPE.equals(bean.getPipelineRootOption()))%>
                                   onclick="updatePipelineSelection();">
<%                      if (hasInheritedOverride) { %>
                            <span class="labkey-disabled">Use a default based on the file root</span><%=
                            PageFlowUtil.helpPopup("Pipeline root", "Setting a default pipeline root for this folder is not supported because a pipeline " +
                                    "override has been set in a parent folder.")%><span class="labkey-disabled">: <%=h(projectDefaultRoot)%></span><%
                        } else { %>
                            Use a default based on the file root: <%=h(projectDefaultRoot)%><%
                        } %>
                        </td>
                    </tr>
                    <% if (hasInheritedOverride) { %>
                        <tr style="height: 1.75em">
                            <td>
                                <input type="radio" name="pipelineRootOption" id="revertOverride" value="<%= h(SetupForm.REVERT_OVERRIDE) %>" onclick="updatePipelineSelection();">
                                Remove this pipeline override and inherit settings from parent
                            </td>
                        </tr>
                    <% } %>
                    <tr style="height: 1.75em">
                        <td>
                            <input type="radio" name="pipelineRootOption" id="pipeOptionProjectSpecified" value="<%=h(SetupForm.PROJECT_SPECIFIED_TYPE)%>"
                                        <%=checked(SetupForm.PROJECT_SPECIFIED_TYPE.equals(bean.getPipelineRootOption())) %>
                                               onclick="updatePipelineSelection();">
                            <%=h(folderRadioBtnLabel)%>
                        </td>
                    </tr>
                    <tr>
                        <td colspan="2">
                            <table class="lk-fields-table" style="margin: 0 0 10px 20px;" id="pipelineRootSettings">
                                <tr>
                                    <td class="labkey-form-label">Primary directory</td>
                                    <td><input type="text" id="pipeProjectRootPath" name="path" size="50" value="<%=h(bean.getPath())%>"></td>
                                </tr>
                                <tr>
                                    <td class="labkey-form-label">Searchable</td>
                                    <td id="pipeIndexTd">
                                        <input type="checkbox" name="searchable" id="pipeOptionIndexable"<%=checked(bean.isSearchable() && SetupForm.PROJECT_SPECIFIED_TYPE.equals(bean.getPipelineRootOption()))%>>
                                        Allow files to be indexed for full-text search
                                    </td>
                                </tr>
                                <tr>
                                    <td class="labkey-form-label" valign="top">Supplemental directory</td>
                                    <td id="pipeSupplementalPathTd"><input type="checkbox" id="pipeOptionSupplementalPath"<%=checked(bean.getSupplementalPath() != null)%> onclick="document.querySelector('#supplementalPathDiv').style.display = (document.querySelector('#pipeOptionSupplementalPath').checked ? '' : 'none'); document.querySelector('#pipeProjectSupplementalPath').disabled = !document.querySelector('#pipeOptionSupplementalPath');">
                                        Include an additional directory when looking for files. No files will be written to this directory.
                                        <div id="supplementalPathDiv" <% if (bean.getSupplementalPath() == null) { %>style="display:none"<% } %>>
                                            <input type="text" id="pipeProjectSupplementalPath" <% if (bean.getSupplementalPath() == null) { %>disabled<% } %> name="supplementalPath" size="50" value="<%=h(bean.getSupplementalPath())%>">
                                        </div>
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                </table>
            </td>
        </tr>
        <tr>
            <td colspan="2">
                <labkey:button text="Save"/>
                <labkey:button text="Cancel" href="<%= bean.getReturnActionURL(getContainer().getStartURL(getUser()))%>"/>
            </td>
        </tr>
    </table>
    <input type="hidden" name="pipelineRootForm" value="true">
</labkey:form>
<script type="text/javascript">

    function updatePipelineSelection()
    {
        if (document.getElementById('pipeOptionSiteDefault').checked)
        {
            var permDiv = document.getElementById('pipelineFilesPermissions');
            if (permDiv) permDiv.style.display = 'none';

            document.getElementById('pipelineRootSettings').style.display = 'none';
        }
        if (document.getElementById('pipeOptionProjectSpecified').checked)
        {
            var permDiv = document.getElementById('pipelineFilesPermissions');
            if (permDiv) permDiv.style.display = '';

            document.getElementById('pipelineRootSettings').style.display = '';
        }
    }

    updatePipelineSelection();
</script>
