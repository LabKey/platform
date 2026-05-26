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
<%@ page import="org.labkey.api.attachments.Attachment" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.study.Study" %>
<%@ page import="org.labkey.api.util.HtmlString" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="java.util.List" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    Container c = getContainer();
    User user = getUser();
    Study study = StudyManager.getInstance().getStudy(c);
    if (null == study)
    {
%>
        No study is active in the current container.<br>
        <%= button("Create Study").href(new ActionURL(StudyController.ManageStudyPropertiesAction.class, c)) %>
<%
        return;
    }
%>

<%
    boolean isAdmin = c.hasPermission(user, AdminPermission.class);
    HtmlString descriptionHtml = study.getDescriptionHtml();
    String investigator = study.getInvestigator();
    String grant = study.getGrant();
    List<Attachment> protocolDocs = study.getProtocolDocuments();
    ActionURL editMetadataURL = new ActionURL(StudyController.ManageStudyPropertiesAction.class, c);
    editMetadataURL.addReturnUrl(getActionURL());
%>
    <script type="text/javascript" nonce="<%=getScriptNonce()%>">
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
                            Attachment attachment = protocolDocs.getFirst();
                    %>
                    <%=attachment.renderDownloadLink(StudyController.getProtocolDocumentDownloadURL(c, attachment.getName()), "Study Protocol Document")%>
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
                        <br><%=doc.renderDownloadLink(StudyController.getProtocolDocumentDownloadURL(c, doc.getName()))%><%
                            }
                        }
                    %>
                </p>
            </td>
        </tr>
    </table>
<%
    if (isAdmin)
    {
%>
    <%=link("Edit", editMetadataURL)%>
<%
    }
%>