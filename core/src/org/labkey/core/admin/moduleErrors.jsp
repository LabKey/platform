<%@ page import="java.util.Map" %>
<%@ page import="org.labkey.api.module.ModuleLoader" %>
<%@ page import="java.io.PrintWriter" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%
    Map<String,Throwable> moduleFailures = ModuleLoader.getInstance().getModuleFailures();

%>
    <table cellspacing="0" cellpadding="1"
           id="dataregion_Gen Runs"
           style="border-top: 1px solid rgb(170, 170, 170);
           border-left: 1px solid rgb(170, 170, 170);
           border-bottom: 1px solid rgb(170, 170, 170);"
           class="grid">
        <tr><th><b>Module name</b></th><th><b>Stack trace</b></th></tr>

<%
    for(Map.Entry<String,Throwable> entry : moduleFailures.entrySet())
    {

%>
        <tr>
            <td valign="top"><pre><%=entry.getKey()%></pre></td>
            <td valign="top"><pre><%entry.getValue().printStackTrace(new PrintWriter(out));%></pre></td>

        </tr>
<%

    }
%>

    </table>

