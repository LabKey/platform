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