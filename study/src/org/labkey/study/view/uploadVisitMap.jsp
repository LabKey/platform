<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.springframework.validation.ObjectError" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="java.util.List" %>
<%@ page import="org.springframework.validation.BindException" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>

<table border=0 cellspacing=2 cellpadding=0>
<%
    BindException errors = (BindException)request.getAttribute("errors");
    if (errors != null)
    {
        for (ObjectError e : (List<ObjectError>) errors.getAllErrors())
        {
            %><tr><td colspan=3><font color="red" class="error"><%=h(HttpView.currentContext().getMessage(e))%></font></td></tr><%
        }
    }
%>
</table>

VisitMap data can be imported to quickly define a study.  VisitMap data generally follows the form of this sample:
<p><pre>
    0|B|Baseline|1|9 (mm/dd/yy)|0|0| 1 2 3 4 5 6 7 8||99
    10|S|One Week Followup|9|9 (mm/dd/yy)|7|0| 9 10 14||
    20|S|Two Week Followup|9|9 (mm/dd/yy)|14|0| 9 10||
    30|T|Termination Visit|9|9 (mm/dd/yy)|21|0| 11 12||
</pre></p>
<%= PageFlowUtil.getStrutsError(request,"main")%>
<form action="uploadVisitMap.post" method="post">
    Paste VisitMap content here:<br>
    <textarea name="content" cols="80" rows="30"></textarea><br>
    <%= buttonImg("Import")%>&nbsp;<%= buttonLink("Cancel", "manageVisits.view")%>
</form>