<%
/*
 * Copyright (c) 2013-2016 LabKey Corporation
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
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.study.Study" %>
<%@ page import="org.labkey.api.study.TimepointType" %>
<%@ page import="org.labkey.api.study.Visit" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.study.controllers.CohortController" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.study.controllers.StudyDesignController" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="org.labkey.study.security.permissions.ManageStudyPermission" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.portal.ProjectUrls" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("study/vaccineDesign/vaccineDesign.lib.xml");
        dependencies.add("study/vaccineDesign/VaccineDesign.css");
    }
%>
<%
    JspView<StudyDesignController.ManageTreatmentsBean> me = (JspView<StudyDesignController.ManageTreatmentsBean>) HttpView.currentView();
    StudyDesignController.ManageTreatmentsBean bean = me.getModelBean();

    Container c = getContainer();
    User user = getUser();

    Study study = StudyManager.getInstance().getStudy(c);
    boolean canManageStudy = c.hasPermission(user, ManageStudyPermission.class);

    // treatment schedule is editable for the individual studies in a Dataspace project
    boolean isDataspaceProject = c.isProject() && c.isDataspace();

    String visitNoun = "Visit";
    if (study != null && study.getTimepointType() == TimepointType.DATE)
        visitNoun = "Timepoint";

    String subjectNoun = "Subject";
    if (study != null)
        subjectNoun = study.getSubjectNounSingular();

    String returnUrl = bean.getReturnUrl() != null ? bean.getReturnUrl() : PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(c).toString();
%>

<script type="text/javascript">
    Ext4.onReady(function()
    {
            LABKEY.Query.selectDistinctRows({
                schemaName: 'study',
                queryName: 'product',
                column:'role',
                success: function(response) {
                    var panelClass = <%=q(bean.isSingleTable() ? "LABKEY.VaccineDesign.TreatmentScheduleSingleTablePanel" : "LABKEY.VaccineDesign.TreatmentSchedulePanel")%>;

                    var productRoles = response.values.sort(function(a, b){
                        var aOrder = a == 'Immunogen' ? 1 : a == 'Adjuvant' ? 2 : 3;
                        var bOrder = b == 'Immunogen' ? 1 : b == 'Adjuvant' ? 2 : 3;
                        return aOrder - bOrder;
                    });

                    Ext4.create(panelClass, {
                        renderTo : 'treatment-schedule-panel',
                        disableEdit : <%=isDataspaceProject%>,
                        subjectNoun : <%=q(subjectNoun)%>,
                        visitNoun : <%=q(visitNoun)%>,
                        returnURL : <%=q(returnUrl)%>,
                        productRoles: productRoles
                    });
                }
            });
    });
</script>

<%
if (isDataspaceProject)
{
%>
Treatment information is defined at the individual study level for Dataspace projects. The grids below are read-only.
<br/><br/>
<%
}
else
{
%>
Enter treatment information in the grids below.
<div style="width: 1400px;">
    <ul>
        <%
            if (bean.isSingleTable())
            {
        %>
        <li>
            Click on time point to define its treatment products by selecting a combination of study products.
        </li>
        <%
            }
            else
            {
        %>
        <li>
            Each treatment label must be unique and must consist of at least one study products.
        </li>
        <%
            }
        %>
        <li>
            Use the manage study products page to change or update the set of available values.
            <%
                ActionURL manageStudyProductsURL = new ActionURL(StudyDesignController.ManageStudyProductsAction.class, getContainer());
                manageStudyProductsURL.addReturnURL(getActionURL());
            %>
            <%=textLink("Manage Study Products", manageStudyProductsURL)%>
        </li>
        <li>
            Each cohort label must be unique. Enter the number of <%=study.getSubjectNounPlural().toLowerCase()%> for
            the cohort in the count column.</li>
        <li>
            Use the manage cohorts page to further configuration information about the cohorts for this study.
            <%=textLink("Manage Cohorts", CohortController.ManageCohortsAction.class)%>
        </li>
        <li>
            Use the manage <%=h(visitNoun.toLowerCase())%>s page to further configure
            information about the <%=h(visitNoun.toLowerCase())%>s for this study or to change
            the <%=h(visitNoun.toLowerCase())%> display order.
            <%=textLink("Manage " + visitNoun + "s", StudyController.ManageVisitsAction.class)%>
        </li>
<%
    if (canManageStudy)
    {
        if (study != null && study.getTimepointType() == TimepointType.VISIT && study.getVisits(Visit.Order.DISPLAY).size() > 1)
        {
%>
            <li>Use the change visit order page to adjust the display order of visits in the treatment schedule table.
            <%= textLink("Change Visit Order", new ActionURL(StudyController.VisitOrderAction.class, c).addReturnURL(getActionURL())) %>
            </li>
<%
        }
    }
%>
    </ul>
</div>
<%
}
%>

<div id="treatment-schedule-panel"></div>