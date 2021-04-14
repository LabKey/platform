<%
/*
 * Copyright (c) 2013-2016 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
%>
<%@ page import="com.google.common.collect.Iterables" %>
<%@ page import="org.labkey.api.study.SpecimenService" %>
<%@ page import="org.labkey.api.study.SpecimenTransform" %>
<%@ page import="org.labkey.api.util.Button" %>
<%@ page import="org.labkey.api.util.HtmlString" %>
<%@ page import="org.labkey.api.util.URLHelper" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.api.util.HtmlString" %>
<%@ page import="java.util.Collection" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.module.ModuleLoader" %>
<%@ page import="org.labkey.api.admin.AdminUrls" %>
<%@ page import="static org.labkey.api.util.HtmlString.NBSP" %>
<%@ page import="org.labkey.api.study.SpecimenUrls" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<%
    Container c = getContainer();
    User user = getUser();
    Collection<SpecimenTransform> specimenTransforms = SpecimenService.get().getSpecimenTransforms(c);
    specimenTransforms.removeIf(transform -> null == transform.getManageAction(c, user));

    URLHelper cancelLink = getActionURL().getReturnURL();
    if (cancelLink == null)
        cancelLink = new ActionURL(StudyController.ManageStudyAction.class, c);
    int numberOfTransforms = specimenTransforms.size();
    int rowNumber = 0;

    String selected = SpecimenService.get().getActiveSpecimenImporter(c);
    HtmlString manageFoldersLink = h(urlProvider(AdminUrls.class).getFolderTypeURL(c));
    HtmlString labkeyEditionsLink = h("https://www.labkey.com/products-services/labkey-server/labkey-server-editions-feature-comparison/");
    HtmlString contactUsLink = h("https://www.labkey.com/about/contact/");
    HtmlString manuallyImportSpecimensLink = h(urlProvider(SpecimenUrls.class).getUploadSpecimensURL(c));
%>

<style type="text/css">
    .importer-radio-cell {
        text-align: center;
        margin-top: 4px;
    }
</style>

<labkey:errors/>

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

                <%=  new Button.ButtonBuilder("Save")
                        .submit(true)
                        .build()
                %>

                <%=  new Button.ButtonBuilder("Cancel")
                        .href(cancelLink)
                        .build()
                %>
            </labkey:form>

        <% } else if (numberOfTransforms == 1) { %>
            <%
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
        <% } else { %>

            <% if (ModuleLoader.getInstance().hasModule("professional")) { %>
                <div class="alert alert-warning">
                    External Specimen Import is not currently available for this folder.
                    To use External import, <a href=<%=manageFoldersLink%>> enable the Professional Module </a> for this folder.
                </div>
            <% } else { %>
                <div class="alert alert-warning">
                    <h1 class="fa fa-star"><%=NBSP%></h1>
                    External Specimen Import is a Premium LabKey feature. <a href=<%=labkeyEditionsLink%>> Learn more </a> or
                    <a href=<%=contactUsLink%>> contact LabKey </a>.
                </div>
            <%
                }
            %>

            <a href=<%=manuallyImportSpecimensLink%>> Import specimens manually </a>
        <%
            }
        %>
    </labkey:panel>

    <% if (numberOfTransforms == 1) { %>
        <%=  new Button.ButtonBuilder("Done")
                .href(cancelLink)
                .build()
        %>
    <%
        }
    %>
</div>
