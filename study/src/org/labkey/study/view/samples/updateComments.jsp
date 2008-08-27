<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.view.WebPartView" %>
<%@ page import="org.labkey.study.controllers.samples.SpringSpecimenController" %>
<%@ page import="org.labkey.study.model.Specimen" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<SpringSpecimenController.UpdateSpecimenCommentsBean> me = (JspView<SpringSpecimenController.UpdateSpecimenCommentsBean>) HttpView.currentView();
    SpringSpecimenController.UpdateSpecimenCommentsBean bean = me.getModelBean();
    Container container = me.getViewContext().getContainer();
%>
<%
    WebPartView.startTitleFrame(out, "Vial Comments", null, null, null);
%>
<labkey:errors/>
<form action="updateComments.post" id="updateCommentForm" method="POST">
    <input type="hidden" name="referrer" value="<%= bean.getReferrer() %>" />
    <input type="hidden" name="saveCommentsPost" value="<%= Boolean.TRUE.toString() %>" />
    <%
        for (Specimen vial : bean.getSamples())
        {
    %>
        <input type="hidden" name="rowId" value="<%= vial.getRowId() %>">
    <%
        }
    %>
    <table>
        <tr>
            <td>
                <textarea rows="10" cols="60" name="comments"><%= h(bean.getCurrentComment()) %></textarea><br>

            </td>
        </tr>
        <tr>
            <td>
                <%= generateButton("Clear Comment", "#", "document.forms['updateCommentForm'].comments.value = ''; return false;") %>
                <%= generateSubmitButton("Save Changes") %>
                <%= generateButton("Cancel", new ActionURL(bean.getReferrer()))%>
            </td>
        </tr>
    </table>
</form>
<%
    WebPartView.endTitleFrame(out);
    WebPartView.startTitleFrame(out, "Selected Vials", null, null, null);
%>
<% me.include(bean.getSpecimenQueryView(), out); %>
<%
    WebPartView.endTitleFrame(out);
%>
