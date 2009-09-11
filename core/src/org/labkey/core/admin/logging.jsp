<%
/*
 * Copyright (c) 2009 LabKey Corporation
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
<%@ page import="org.labkey.api.util.SessionAppender" %>
<%@ page import="org.apache.log4j.spi.LoggingEvent" %>
<%@ page import="org.labkey.api.util.DateUtil" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%    
    boolean loggingEnabled = SessionAppender.isLogging(request);
    LoggingEvent[] events = SessionAppender.getLoggingEvents(request);
%>

<form method=POST>
logging: <input name=logging type="checkbox" <%=loggingEnabled?"checked":""%> value="true"><input type="submit">
</form>

<table><%
for (LoggingEvent e : events)
{
    %><tr><td valign="top" nowrap style="color:#808080;"><%=DateUtil.toISO(e.timeStamp).substring(11)%></td><td valign="top"><%=e.getLevel()%></td><td><pre><%=h(e.getMessage())%></pre></td></tr><%
}
%></table>