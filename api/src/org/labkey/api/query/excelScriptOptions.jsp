<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.labkey.api.util.GUID" %>
<%
JspView<Map<String, ActionURL>> me = (JspView<Map<String, ActionURL>>) HttpView.currentView();
Map<String, ActionURL> map = me.getModelBean();
String guid = GUID.makeGUID();
boolean first = true;
%>

<table cellspacing="4">
    <% for (Map.Entry<String, ActionURL> entry : map.entrySet())
    { %>
        <tr>
            <td valign="center"><%= first ? "Scripting language:" : "" %></td>
            <td valign="center"><input type="radio" <%= first ? "id=\"" + guid + "\"" : "" %> name="scriptExportType" <%= first ? "checked=\"true\"" : "" %> value="<%= PageFlowUtil.filter(entry.getValue()) %>"/></td>
            <td valign="center"><%= PageFlowUtil.filter(entry.getKey())%></td>
        </tr>
    <%  first = false;
    }%>
    <tr>
        <td colspan="2" />
        <td><%= PageFlowUtil.generateButton("Create Script", "", "window.location = getRadioButtonValue(document.getElementById(\"" + guid + "\")); return false;") %></td>
    </tr>
</table>
