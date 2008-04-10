<%@ page import="org.labkey.api.study.PlateTemplate" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.study.controllers.plate.PlateController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<%
    JspView<PlateController.CopyTemplateBean> me = (JspView<PlateController.CopyTemplateBean>) HttpView.currentView();
    PlateController.CopyTemplateBean bean = me.getModelBean();
%>
<labkey:errors />
<table class="normal">
    <tr>
        <td>Copy <b><%= h(bean.getTemplateName()) %></b> to:</td>
    </tr>
    <%= bean.getTreeHtml() %>
    <tr>
        <td>
            <br>
            <form action="handleCopy.post" method="POST">
                <input type="hidden" name="destination" value="<%= h(bean.getSelectedDestination()) %>">
                <input type="hidden" name="templateName" value="<%= h(bean.getTemplateName()) %>">
                <%= buttonLink("Cancel", "plateTemplateList.view")%>
                <%= buttonImg("Copy", bean.getSelectedDestination() != null ? "" : "alert('Please select a destination folder.'); return false;") %>
            </form>
        </td>
    </tr>
<%
    PlateTemplate[] templates = bean.getDestinationTemplates();
    if (templates != null)
    {
%>
    <tr>
        <th align="left">Templates currently in <%= bean.getSelectedDestination() %>:</th>
    </tr>
<%
        if (templates.length == 0)
        {
%>
    <tr>
        <td style="padding-left:20px">None</td>
    </tr>
<%
        }
        else
        {
            for (PlateTemplate template : templates)
            {
%>
    <tr>
        <td style="padding-left:20px"><%= template.getName() %></td>
    </tr>
<%
            }
        }
    }
%>
</table>
