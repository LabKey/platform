<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.study.model.Study" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="org.labkey.study.designer.StudyDesignInfo" %>
<%@ page import="org.labkey.study.designer.StudyDesignManager" %>
<%@ page import="org.labkey.api.security.ACL" %>
<%@ page import="java.util.List" %>
<%@ page import="org.labkey.study.designer.client.model.*" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    Container c = HttpView.currentContext().getContainer();
    Study study = StudyManager.getInstance().getStudy(c);
    if (null == study)
    {      %>
    No study is active in the current container.<br>
    <%=buttonLink("Create Study", new ActionURL("Study", "manageStudyProperties.view", c))%>
<%
        return;
    }

    StudyDesignInfo info = StudyDesignManager.get().getDesignForStudy(study);
    if (null == info)
    {%>
        No protocol has been registered for this study.<%
        return;
    }
    //Shouldn't happen, but being defensive
    if (!info.getContainer().equals(study.getContainer()) && !info.getContainer().hasPermission(HttpView.currentContext().getUser(), ACL.PERM_READ))
    {%>
        Study protocol is in another folder you do not have permission to read.
<%

    }
    GWTStudyDefinition revision = StudyDesignManager.get().getGWTStudyDefinition(info.getContainer(), info);
%>
This study was created from a vaccine study protocol with the following description.
<blockquote>
    <%=h(revision.getDescription(), true)%>
</blockquote>
<b>Immunogens:</b> <%
    String sep = "";
    for (GWTImmunogen immunogen : (List<GWTImmunogen>) revision.getImmunogens())
    {
        out.print(sep);
        out.print(h(immunogen.getName()));
        String antigenSep = "";
        for (GWTAntigen antigen : (List<GWTAntigen>) immunogen.getAntigens())
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
    for (GWTCohort cohort : (List<GWTCohort>) revision.getGroups())
    {
        out.print(sep);
        out.print(h(cohort.getName()));
        out.print(" (" + cohort.getCount() + ")");
        sep = ", ";
    }
%>
    <br>
<%
    ActionURL url = new ActionURL("Study-Designer", "designer.view", info.getContainer());
    url.replaceParameter("studyId", String.valueOf(info.getStudyId()));
%>
<%=textLink("View Complete Protocol", url)%>