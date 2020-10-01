<%
    /*
     * Copyright (c) 2013-2016 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
     * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
     */
%>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.api.study.SpecimenService" %>
<%@ page import="org.labkey.api.util.Button" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.study.SpecimenTransform" %>
<%@ page import="java.util.Collection" %>

<%@ page import="com.google.common.collect.Iterables" %>
<%@ page import="org.labkey.api.util.URLHelper" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.api.util.HtmlString" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<%
    Collection<SpecimenTransform> specimenTransforms = SpecimenService.get().getSpecimenTransforms(getContainer());
    specimenTransforms.removeIf(transform -> null == transform.getManageAction(getContainer(), getUser()));

    URLHelper cancelLink = getActionURL().getReturnURL();
    if (cancelLink == null)
        cancelLink = new ActionURL(StudyController.ManageStudyAction.class, getContainer());
    int numberOfTransforms = specimenTransforms.size();
    int rowNumber = 0;
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
        <%
            String selected = SpecimenService.get().getActiveSpecimenImporter(getContainer());
        %>
        <p>
            Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.
        </p>
        <br/>

        <labkey:panel id="overview" className="lk-sg-section">
            <h4 class="labkey-page-section-header">Configure Specimen Import</h4>

            <labkey:form method="post">
                <table class="labkey-data-region-legacy labkey-show-borders">
                    <tr>
                        <td class="labkey-column-header">Name</td>
                        <td class="labkey-column-header">Enabled</td>
                        <td class="labkey-column-header"></td>
                    </tr>

                    <%
                        for (SpecimenTransform transform : specimenTransforms)
                        {
                            ActionURL manageAction = transform.getManageAction(getContainer(), getUser());
                            HtmlString transformName = h(transform.getName());

                    %>
                            <tr class="<%=getShadeRowClass(rowNumber++)%>">
                                <td class="lk-study-prop-label"><%=transformName%></td>
                                <td class="lk-study-prop-desc">
                                    <div class="importer-radio-cell">
                                        <input
                                            type="radio"
                                            name="activeTransform"
<%--                                               would using this (below) be good form?--%>
<%--                                               value="<%=StudyController.EnabledSpecimenImportForm.ActiveTransformEnum.FreezerPro%>"--%>
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
        </labkey:panel>
    <% } else if (numberOfTransforms == 1) { %>
        <%
            SpecimenTransform transform = Iterables.get(specimenTransforms, 0);
            ActionURL manageAction = transform.getManageAction(getContainer(), getUser());
        %>
        <p>
            Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.
        </p>
        <br/>

<%--    I go back of fourth on which looks better, panel or no --%>
<%--        <labkey:panel id="overview" className="lk-sg-section">--%>
<%--            <h4 class="labkey-page-section-header">Configure Specimen Import</h4>--%>

        <table class="labkey-data-region-legacy labkey-show-borders">
            <tr class="<%=getShadeRowClass(rowNumber++)%>">
                <td class="lk-study-prop-label"><%=h(transform.getName())%></td>
                <td><%=link("configure", manageAction)%></td>
            </tr>
        </table>

        <br/><br/>

        <%=  new Button.ButtonBuilder("Done")
                .href(cancelLink)
                .build()
        %>
<%--        </labkey:panel>--%>

    <% } else { %>
        <div class="alert alert-info">
            <h1 class="fa fa-star-o"> Professional/Enterprise Edition Feature</h1>
            <h3>Specimen Import is not available with your current edition of LabKey Server.</h3>
            <hr>
            <p>Professional/Enterprise edition subscribers have the ability to import from an external specimen repository like FreezerPro or a specimen data mart.</p>
            <p><a class="alert-link" href="" target="_blank" rel="noopener noreferrer">Learn more <i class="fa fa-external-link"></i></a></p>
            <p>In addition to this feature, Professional/Enterprise editions of LabKey Server provide professional support and advanced functionality to help teams maximize the value of the platform.</p>
            <br>
            <p><a class="alert-link" href="https://www.labkey.com/platform/go-premium/" target="_blank" rel="noopener noreferrer">Learn more about Professional/Enterprise editions <i class="fa fa-external-link"></i></a></p>
        </div>
    <%
        }
    %>
</div>
