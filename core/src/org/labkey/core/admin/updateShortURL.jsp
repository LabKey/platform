<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.core.admin.AdminController" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<labkey:errors/>

<%
    AdminController.ShortURLForm bean = (AdminController.ShortURLForm) HttpView.currentModel();
%>

<labkey:form action="<%=urlFor(AdminController.UpdateShortURLAction.class)%>" method="POST" id="updateShortUrlForm">
    <table class="lk-fields-table">
        <tr>
            <td class="labkey-form-label">Short URL: </td>
            <td>
                <input type="hidden" name="shortURL" value="<%= h(bean.getShortURL()) %>"/>
                <%= h(bean.getShortURL()) %>
            </td>
        </tr>
        <tr>
            <td class="labkey-form-label">Target URL: </td>
            <td><textarea rows="3" cols="80" name="fullURL"><%= h(bean.getFullURL()) %></textarea></td>
        </tr>
    </table>
    <div style="margin-top: 10px;">
        <%= button("Update").submit(true) %>
        <%= button("Cancel").href(new ActionURL(AdminController.ShortURLAdminAction.class, getContainer())) %>
    </div>
</labkey:form>

<div style="margin-top: 20px;">
    <%= button("Delete")
            .usePost("Are you sure you want to delete the short URL " + bean.getShortURL() + "?")
            .href(urlFor(AdminController.UpdateShortURLAction.class).addParameter("shortURL", bean.getShortURL()).addParameter("delete", true)) %>
</div>