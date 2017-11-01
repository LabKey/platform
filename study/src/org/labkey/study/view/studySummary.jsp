<%
/*
 * Copyright (c) 2006-2017 LabKey Corporation
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
<%@ page import="org.labkey.api.admin.AdminUrls"%>
<%@ page import="org.labkey.api.attachments.Attachment"%>
<%@ page import="org.labkey.api.data.Container"%>
<%@ page import="org.labkey.api.pipeline.PipelineService" %>
<%@ page import="org.labkey.api.pipeline.PipelineUrls" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.study.controllers.BaseStudyController" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.study.security.permissions.ManageRequestSettingsPermission" %>
<%@ page import="org.labkey.study.view.StudySummaryWebPartFactory" %>
<%@ page import="java.util.List" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<StudySummaryWebPartFactory.StudySummaryBean> me = (JspView<StudySummaryWebPartFactory.StudySummaryBean>) HttpView.currentView();
    StudySummaryWebPartFactory.StudySummaryBean bean = me.getModelBean();

    User user = (User)request.getUserPrincipal();
    Container c = getContainer();

    if (null == bean.getStudy())
    {
        out.println("<p>This folder does not contain a study.</p>");
        if (c.hasPermission(user, AdminPermission.class))
        {
            ActionURL createURL = new ActionURL(StudyController.ManageStudyPropertiesAction.class, c);
            out.println(button("Create Study").href(createURL));

            if (PipelineService.get().hasValidPipelineRoot(c))
            {
                out.println(button("Import Study").href(urlProvider(AdminUrls.class).getImportFolderURL(c).addParameter("origin", "Study")));
            }
            else if (PipelineService.get().canModifyPipelineRoot(user, c))
            {
                ActionURL pipelineURL = urlProvider(PipelineUrls.class).urlSetup(c);
                out.println(button("Pipeline Setup").href(pipelineURL));
            }
        }
        else
        {
%>
    Contact an administrator to create a study.
<%
        }

        return;
    }

    boolean isAdmin = c.hasPermission(user, AdminPermission.class);
    ActionURL url = new ActionURL(StudyController.BeginAction.class, bean.getStudy().getContainer());
    String descriptionHtml = bean.getDescriptionHtml();
    String investigator = bean.getInvestigator();
    String grant = bean.getGrant();
    List<Attachment> protocolDocs = bean.getProtocolDocuments();
    ActionURL editMetadataURL = new ActionURL(StudyController.ManageStudyPropertiesAction.class, c);
    editMetadataURL.addParameter("returnURL", bean.getCurrentURL());
%>
<script type="text/javascript">
    LABKEY.requiresCss("editInPlaceElement.css");
</script>
<table width="100%">
    <tr>
        <td class= "study-properties" valign="top">
            <div>
                <span style="float: left">
                    <%
                        if(investigator != null)
                        {
                            out.print(h("Investigator: " + investigator));
                        }
                    %>
                </span>

                <span style="float: right">
                    <%
                        if(grant != null)
                        {
                            out.print(h("Grant: " + grant));
                        }
                    %>
                </span>
            </div>
            <br><br>
                <div style="clear: both;">
                <%=descriptionHtml%>
                </div>
            <p>
                <%
                    if (protocolDocs.size() == 1)
                    {
                        Attachment attachment = protocolDocs.get(0);
                %>
                <a href="<%=h(StudyController.getProtocolDocumentDownloadURL(c, attachment.getName()))%>">
                    <img src="<%=getWebappURL(attachment.getFileIcon())%>" alt="[<%= h(attachment.getName()) %>]">
                    Study Protocol Document
                </a>
                <%
                    }
                    else if (protocolDocs.size() > 1)
                    {
                %>
                Protocol documents:
                <%
                        for (Attachment doc : protocolDocs)
                        {
                %>
                    <br><a href="<%=h(StudyController.getProtocolDocumentDownloadURL(c, doc.getName()))%>">
                        <img src="<%=getWebappURL(doc.getFileIcon())%>" alt="[<%= h(doc.getName()) %>]">
                        <%= h(h(doc.getName())) %>
                    </a><%
                        }
                    }
                %>
            </p>
        </td>

        <td style="vertical-align:top;border-left:solid #DDDDDD 1px;padding-left:1em">
                <a href="<%=h(BaseStudyController.getStudyOverviewURL(bean.getStudy().getContainer()))%>"><img src="<%=request.getContextPath()%>/_images/studyNavigator.gif" alt="Study Navigator"> </a><br>
                <%=textLink("Study Navigator", BaseStudyController.getStudyOverviewURL(bean.getStudy().getContainer()))%><br>
            <%
                if (isAdmin)
                {
                    out.write("<p>");
                    out.write(textLink("Manage Study", url.setAction(StudyController.ManageStudyAction.class)));
                    out.write("</p>");

                    // if there is a pipeline override, show the pipeline view, else show the file browser
                    ActionURL pipelineUrl;

                    if (PipelineService.get().hasSiteDefaultRoot(c))
                        pipelineUrl = urlProvider(PipelineUrls.class).urlBrowse(c, getViewContext().getActionURL());
                    else
                        pipelineUrl = urlProvider(PipelineUrls.class).urlBegin(c);

                    out.write("<p>");
                    out.write(textLink("Manage Files", pipelineUrl));
                    out.write("</p>");
                }
                else if (c.hasPermission(user, ManageRequestSettingsPermission.class) &&
                        bean.getStudy().getRepositorySettings().isEnableRequests())
                {
                    out.write("<p>");
                    out.write(textLink("Manage Specimen Request Settings", url.setAction(StudyController.ManageStudyAction.class)));
                    out.write("</p>");
                }
                else if (c.hasPermission(user, ManageRequestSettingsPermission.class))
                {
                    out.write("<p>");
                    out.write(textLink("Manage Study", url.setAction(StudyController.ManageStudyAction.class)));
                    out.write("</p>");
                }
            %>
        </td>
    </tr>
</table>
