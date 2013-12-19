
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.Portal" %>
<%@ page import="org.labkey.study.model.StudyImpl" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="org.labkey.study.model.AssaySpecimenConfigImpl" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page import="org.labkey.study.model.LocationImpl" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="java.util.List" %>
<%@ page import="org.labkey.study.model.VisitImpl" %>
<%@ page import="org.labkey.api.view.WebThemeManager" %>
<%@ page import="org.labkey.api.view.WebTheme" %>
<%@ page import="org.labkey.study.SampleManager" %>
<%@ page import="org.labkey.study.model.PrimaryType" %>
<%@ page import="org.labkey.study.model.DerivativeType" %>
<%@ page import="org.labkey.study.security.permissions.ManageStudyPermission" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%!
    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
        resources.add(ClientDependency.fromFilePath("study/StudyVaccineDesign.css"));
        return resources;
    }
%>
<%
    JspView<Portal.WebPart> me = (JspView) HttpView.currentView();
    Container c = me.getViewContext().getContainer();
    StudyImpl study = StudyManager.getInstance().getStudy(c);

    User user = me.getViewContext().getUser();
    boolean canManageStudy  = c.hasPermission(user, ManageStudyPermission.class);

    WebTheme theme = WebThemeManager.getTheme(c);
    String link        = theme.getLinkColor();
    String grid        = theme.getGridColor();
%>

<style type="text/css">

    table.study-vaccine-design .labkey-col-header {
        background-color: #<%= grid %>;
    }

    table.study-vaccine-design .labkey-col-header-active {
        background-color: #<%= grid %>;
    }

    table.study-vaccine-design .labkey-col-header-active .gwt-Label {
        color: #<%= link %>;
    }

    table.study-vaccine-design .labkey-col-header-active .gwt-Label:hover {
        color: #<%= link %>;
    }

    table.study-vaccine-design .labkey-row-header {
        background-color: #<%= grid %>;
    }

    table.study-vaccine-design .labkey-row-active .gwt-Label, table.study-vaccine-design .labkey-row-header .gwt-Label {
        color: #<%= link %>;
    }

    table.study-vaccine-design .labkey-row-active .gwt-Label:hover, table.study-vaccine-design .labkey-row-header .gwt-Label:hover {
        color: #<%= link %>;
    }

    table.study-vaccine-design .assay-corner {
        background-color: #<%= grid %>;
    }

    a.labkey-button, a.labkey-button:visited, a.gwt-Anchor {
        color: #<%= link %>;
    }

</style>

<%
    if (study != null)
    {
        List<AssaySpecimenConfigImpl> assaySpecimenConfigs = study.getAssaySpecimenConfigs();
        List<VisitImpl> visits = StudyManager.getInstance().getVisitsForAssaySchedule(c);

        if (assaySpecimenConfigs.size() == 0)
        {
%>
            <p>No assays have been scheduled.</p>
<%
        }
        else
        {
%>
            <table class="labkey-read-only labkey-data-region labkey-show-borders study-vaccine-design" style="border: solid #ddd 1px;">
                <tr>
                    <td class="labkey-col-header">Assay</td>
                    <td class="labkey-col-header">Lab</td>
                    <td class="labkey-col-header">Sample Type</td>
<%
            for (VisitImpl visit : visits)
            {
%>
                    <td class="labkey-col-header">
                        <%=h(visit.getDisplayString())%>
                        <%=(visit.getDescription() != null ? PageFlowUtil.helpPopup("Description", visit.getDescription()) : "")%>
                    </td>
<%
    }
%>
                </tr>
<%
            for (AssaySpecimenConfigImpl assaySpecimen : assaySpecimenConfigs)
            {
                // concatenate sample type (i.e. primary, derivative, tube type)
                String sampleType = "";
                String sep = "";
                if (assaySpecimen.getPrimaryTypeId() != null)
                {
                    PrimaryType pt = SampleManager.getInstance().getPrimaryType(c, assaySpecimen.getPrimaryTypeId());
                    sampleType += (pt != null ? pt.getPrimaryType() : assaySpecimen.getPrimaryTypeId());
                    sep = " / ";
                }
                if (assaySpecimen.getDerivativeTypeId() != null)
                {
                    DerivativeType dt = SampleManager.getInstance().getDerivativeType(c, assaySpecimen.getDerivativeTypeId());
                    sampleType += sep + (dt != null ? dt.getDerivative() : assaySpecimen.getDerivativeTypeId());
                    sep = " / ";
                }
                if (assaySpecimen.getTubeType() != null)
                {
                    sampleType += sep + assaySpecimen.getTubeType();
                }

                String locationLabel = "";
                if (assaySpecimen.getLocationId() != null)
                {
                    LocationImpl location = StudyManager.getInstance().getLocation(c, assaySpecimen.getLocationId());
                    locationLabel = location != null ? location.getLabel() : "";
                }

%>
                <tr>
                    <td class="assay-row-padded-view"><%=h(assaySpecimen.getAssayName())%>
                        <%=(assaySpecimen.getDescription() != null ? PageFlowUtil.helpPopup("Description", assaySpecimen.getDescription()) : "")%>
                    </td>
                    <td class="assay-row-padded-view"><%=h(locationLabel)%></td>
                    <td class="assay-row-padded-view"><%=h(sampleType)%></td>
<%
                List<Integer> assaySpecimenVisits = StudyManager.getInstance().getAssaySpecimenVisitIds(c, assaySpecimen);
                for (VisitImpl visit : visits)
                {
                    %><td class="assay-row-padded-view" align="center"><%=h(assaySpecimenVisits.contains(visit.getRowId()) ? "[x]" : " ")%></td><%
                }
%>
                </tr>
<%
            }
%>
            </table>
<%
        }
    }
    else
    {
%>
    <p>The folder must contain a study in order to display an assay schedule.</p>
<%
    }

    if (canManageStudy)
    {
        %><br/><%=textLink("Manage Assay Schedule", StudyController.ManageAssaySpecimenAction.class)%><%
    }
%>