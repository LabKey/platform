<%@ page import="org.labkey.api.view.ActionURL"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.module.ModuleLoader" %>
<%@ page import="java.util.Collection" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<form action="deleteStudy.post" method="post">
This will delete all study data in this folder.
<ul>
<%
Collection<String> summaries = ModuleLoader.getInstance().getCurrentModule().getSummary(getStudy().getContainer());
for (String s : summaries)
{
%>
    <li><%=h(s)%></li>
<%
}
%>
</ul>
    <br>
    Check the box below to confirm that you want to delete this study. <br>
<input type=checkbox name=confirm value=true> Confirm Delete<br><br>
<%=buttonImg("Delete")%> <%=buttonLink("Cancel", "manageStudy.view")%>
</form>