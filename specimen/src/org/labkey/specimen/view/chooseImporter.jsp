<%
/*
 * Copyright (c) 2020-2026 LabKey Corporation
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
<%@ page import="com.google.common.collect.Iterables" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.study.SpecimenService" %>
<%@ page import="org.labkey.api.study.SpecimenTransform" %>
<%@ page import="org.labkey.api.study.StudyUrls" %>
<%@ page import="org.labkey.api.util.ButtonBuilder" %>
<%@ page import="org.labkey.api.util.HtmlString" %>
<%@ page import="org.labkey.api.util.URLHelper" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.specimen.actions.ShowUploadSpecimensAction" %>
<%@ page import="java.util.Collection" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    Container c = getContainer();
    User user = getUser();
    Collection<SpecimenTransform> specimenTransforms = SpecimenService.get().getSpecimenTransforms(c);
    specimenTransforms.removeIf(transform -> null == transform.getManageAction(c, user));

    URLHelper cancelLink = getActionURL().getReturnUrl();
    if (cancelLink == null)
        cancelLink = urlProvider(StudyUrls.class).getManageStudyURL(getContainer());
    int numberOfTransforms = specimenTransforms.size();
    int rowNumber = 0;

    String selected = SpecimenService.get().getActiveSpecimenImporter(c);
    HtmlString manuallyImportSpecimensLink = h(urlFor(ShowUploadSpecimensAction.class));
%>

<style type="text/css">
    .importer-radio-cell {
        text-align: center;
        margin-top: 4px;
    }
</style>

<labkey:errors/>

<%
    // At the moment, there's exactly one SpecimenTransform, QueryBasedSpecimenTransform, which is provided by the
    // Specimen module. As a result, this "choose importer" page is never linked (if a single transform exists, the
    // "Configure Specimen Import" link navigates straight its configuration page. This probably won't ever change,
    // but we'll leave this page in place just in case.
%>
<div>
    <% if (numberOfTransforms > 1) { %>
        <p>
            Activate automatic import of specimen data from an external source on this page. In order to prevent automated reloads from overwriting specimen data upon manual or scheduled imports, only one specimen import mechanism may be active at a time for a given container.
        </p>
    <%
        }
    %>
    <p>
        Learn more about <%=helpLink("externalSpecimens", "Automated External Specimen Imports")%>.
    </p>

    <br/>

    <labkey:panel id="overview" className="lk-sg-section">
        <h4 class="labkey-page-section-header">Configure Specimen Import</h4>

        <% if (numberOfTransforms > 1) { %>
            <labkey:form method="post">
                <table class="labkey-data-region-legacy labkey-show-borders">
                    <tr>
                        <td class="labkey-column-header">Name</td>
                        <td class="labkey-column-header">Active</td>
                        <td class="labkey-column-header"></td>
                    </tr>

                    <%
                        for (SpecimenTransform transform : specimenTransforms)
                        {
                            ActionURL manageAction = transform.getManageAction(c, user);
                            HtmlString transformName = h(transform.getName());

                    %>
                        <tr class="<%=getShadeRowClass(rowNumber++)%>">
                            <td class="lk-study-prop-label"><%=transformName%></td>
                            <td class="lk-study-prop-desc">
                                <div class="importer-radio-cell">
                                    <input
                                        type="radio"
                                        name="activeTransform"
                                        value="<%=transformName%>"
                                        <%=checked(transformName.toString().equals(selected))%>
                                    >
                                </div>
                            </td>
                            <td><%=link("configure", manageAction)%></td>
                        </tr>
                    <%
                        }
                    %>
                </table>

                <br/>

                <%=  new ButtonBuilder("Save")
                        .submit(true)
                        .build()
                %>

                <%=  new ButtonBuilder("Cancel")
                        .href(cancelLink)
                        .build()
                %>
            </labkey:form>

        <% } else if (numberOfTransforms == 1) {
                SpecimenTransform transform = Iterables.get(specimenTransforms, 0);
                ActionURL manageAction = transform.getManageAction(c, user);
            %>

            <br/>

            <table class="labkey-data-region-legacy labkey-show-borders">
                <tr class="<%=getShadeRowClass(rowNumber++)%>">
                    <td class="lk-study-prop-label"><%=h(transform.getName())%></td>
                    <td><%=link("configure", manageAction)%></td>
                </tr>
            </table>

            <br/><br/>

            <%=  new ButtonBuilder("Done")
                        .href(cancelLink)
                        .build()
            %>

        <% } else { %>

            <a href=<%=manuallyImportSpecimensLink%>> Import specimens manually </a>
        <%
            }
        %>
    </labkey:panel>
</div>
