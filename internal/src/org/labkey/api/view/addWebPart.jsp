<%@ page import="org.labkey.api.view.Portal" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    Portal.AddWebParts bean = (Portal.AddWebParts)HttpView.currentModel();
%>
<table width="100%">
<tr>
    <td class="normal" align="left">
		<form action=addWebPart.view>
		<table><tr><td>
		<input type="hidden" name="pageId" value="<%=bean.pageId%>"/>
		<input type="hidden" name="location" value="<%=bean.location%>"/>
        <select name="name">
            <option value="">&lt;Select Part&gt;</option>
<%          for ( String name : bean.webPartNames)
            {
                %><option value="<%=h(name)%>"><%=h(name)%></option> <%
            } %>
        </select>
        </td><td>
		<input type="image" src='<%=PageFlowUtil.buttonSrc("Add Web Part")%>'>
        </td></tr></table>
       </form>
    </td>
<% if (bean.rightWebPartNames != null && !bean.rightWebPartNames.isEmpty())
    { %>
    <td class="normal"align="right">
        <form action=addWebPart.view>
        <table><tr><td>
        <input type="hidden" name="pageId" value="<%=bean.pageId%>"/>
        <input type="hidden"name="location"value="right"/>
        <select name="name">
            <option value="">&lt;Select Part&gt;</option>
<%          for (String name : bean.rightWebPartNames)
            {
                %><option value="<%=h(name)%>"><%=h(name)%></option> <%
            } %>
        </select>
        </td><td>
            <input type="image" src='<%=PageFlowUtil.buttonSrc("Add Web Part")%>'>
        </td></tr></table>
        </form>
    </td>
<%  } %>
</tr>
</table>
