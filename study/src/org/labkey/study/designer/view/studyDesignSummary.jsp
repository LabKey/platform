<%
/*
 * Copyright (c) 2007-2009 LabKey Corporation
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
<%@ page import="org.labkey.api.security.permissions.ReadPermission" %>
<%@ page import="org.labkey.api.study.Study" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.study.designer.StudyDesignInfo" %>
<%@ page import="org.labkey.study.designer.StudyDesignManager" %>
<%@ page import="gwt.client.org.labkey.study.designer.client.model.GWTAntigen" %>
<%@ page import="gwt.client.org.labkey.study.designer.client.model.GWTCohort" %>
<%@ page import="gwt.client.org.labkey.study.designer.client.model.GWTImmunogen" %>
<%@ page import="gwt.client.org.labkey.study.designer.client.model.GWTStudyDefinition" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="java.util.List" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    Container c = HttpView.currentContext().getContainer();
    Study study = StudyManager.getInstance().getStudy(c);
    if (null == study)
    {      %>
    No study is active in the current container.<br>
    <%=generateButton("Create Study", new ActionURL("Study", "manageStudyProperties.view", c))%>
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
    if (!info.getContainer().equals(study.getContainer()) && !info.getContainer().hasPermission(HttpView.currentContext().getUser(), ReadPermission.class))
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