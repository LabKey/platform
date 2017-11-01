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
<%@ page import="gwt.client.org.labkey.study.designer.client.model.GWTAntigen" %>
<%@ page import="gwt.client.org.labkey.study.designer.client.model.GWTCohort" %>
<%@ page import="gwt.client.org.labkey.study.designer.client.model.GWTImmunogen" %>
<%@ page import="gwt.client.org.labkey.study.designer.client.model.GWTStudyDefinition" %>
<%@ page import="org.labkey.api.attachments.Attachment" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.security.permissions.ReadPermission" %>
<%@ page import="org.labkey.api.study.Study" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.study.controllers.designer.DesignerController" %>
<%@ page import="org.labkey.study.designer.StudyDesignInfo" %>
<%@ page import="org.labkey.study.designer.StudyDesignManager" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="java.util.List" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    Container c = getContainer();
    User user = getUser();
    Study study = StudyManager.getInstance().getStudy(c);
    if (null == study)
    {      %>
    No study is active in the current container.<br>
    <%= button("Create Study").href(new ActionURL(StudyController.ManageStudyPropertiesAction.class, c)) %>
<%
        return;
    }

    // issue 21432: only create study design as admin if the label is not already in use
    StudyDesignInfo info = StudyDesignManager.get().getDesignForStudy(study);
    boolean designExistsForLabel = StudyDesignManager.get().getStudyDesign(c, study.getLabel()) != null;
    if (info == null && c.hasPermission(user, AdminPermission.class) && !designExistsForLabel)
    {
        info = StudyDesignManager.get().getDesignForStudy(user, study, true);
    }

    if (null == info)
    {%>
        No protocol has been registered for this study.<%
        return;
    }
    //Shouldn't happen, but being defensive
    if (!info.getContainer().equals(study.getContainer()) && !info.getContainer().hasPermission(getUser(), ReadPermission.class))
    {%>
        Study protocol is in another folder you do not have permission to read.
<%

    }
    GWTStudyDefinition revision = StudyDesignManager.get().getGWTStudyDefinition(user, info.getContainer(), info);
%>
<%
    if (null == study.getDescription() && null != revision.getDescription()) //No study description. Generate one from the study design
    {
%>
This study was created from a vaccine study protocol with the following description.
<blockquote>
    <%=h(revision.getDescription(), true)%>
</blockquote>
<b>Immunogens:</b> <%
    String sep = "";
    for (GWTImmunogen immunogen : revision.getImmunogens())
    {
        out.print(sep);
        out.print(h(immunogen.getName()));
        String antigenSep = "";
        for (GWTAntigen antigen : immunogen.getAntigens())
        {
            out.print(antigenSep);
            out.print(h(antigen.getName()));
            antigenSep = ",";
        }
        sep = ", ";
    }
%><br>
<b>Cohorts:</b> <%
    sep = "";
    for (GWTCohort cohort : revision.getGroups())
    {
        out.print(sep);
        out.print(h(cohort.getName()));
        out.print(" (" + cohort.getCount() + ")");
        sep = ", ";
    }
%>
    <br>
<%
    ActionURL url = new ActionURL(DesignerController.DesignerAction.class, info.getContainer());
    url.replaceParameter("studyId", String.valueOf(info.getStudyId()));
%>
<%=textLink("View Complete Protocol", url)%>
<%
    }
    else
    {
        boolean isAdmin = c.hasPermission(user, AdminPermission.class);
        String descriptionHtml = study.getDescriptionHtml();
        String investigator = study.getInvestigator();
        String grant = study.getGrant();
        List<Attachment> protocolDocs = study.getProtocolDocuments();
        ActionURL editMetadataURL = new ActionURL(StudyController.ManageStudyPropertiesAction.class, c);
        editMetadataURL.addParameter("returnURL", getActionURL().toString());
    %>
    <script type="text/javascript">
        LABKEY.requiresCss("editInPlaceElement.css");
    </script>
    <table width="100%">
        <tr>
            <td valign="top">
                <div>
                    <span style="float: left">
                        <%
                            if(investigator != null)
                            {
                                out.print(h("Investigator: " +investigator));
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
                    <a href="<%= h(StudyController.getProtocolDocumentDownloadURL(c, attachment.getName())) %>">
                        <img src="<%= getViewContext().getContextPath() + attachment.getFileIcon() %>" alt="[<%= h(attachment.getName()) %>]">
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
                        <br><a href="<%= h(StudyController.getProtocolDocumentDownloadURL(c, doc.getName())) %>">
                            <img src="<%= getViewContext().getContextPath() + doc.getFileIcon() %>" alt="[<%= h(doc.getName()) %>]">
                            <%= h(h(doc.getName())) %>
                        </a><%
                            }
                        }
                    %>
                </p>
            </td>
        </tr>
    </table>
<%
      if (isAdmin)
      { %>
<%=textLink("Edit", editMetadataURL)%>
<%    }
  }
%>