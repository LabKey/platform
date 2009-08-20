<%
/*
 * Copyright (c) 2006-2008 LabKey Corporation
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
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.springframework.validation.ObjectError" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="java.util.List" %>
<%@ page import="org.springframework.validation.BindException" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>

<table>
<%
    BindException errors = (BindException)request.getAttribute("errors");
    if (errors != null)
    {
        for (ObjectError e : (List<ObjectError>) errors.getAllErrors())
        {
            %><tr><td colspan=3><font class="labkey-error"><%=h(HttpView.currentContext().getMessage(e))%></font></td></tr><%
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
<labkey:errors/>
<form action="uploadVisitMap.post" method="post">
    Paste VisitMap content here:<br>
    <textarea name="content" cols="80" rows="30"></textarea><br>
    <%= generateSubmitButton("Import")%>&nbsp;<%= generateButton("Cancel", "manageVisits.view")%>
</form>