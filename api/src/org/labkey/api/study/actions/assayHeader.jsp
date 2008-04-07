<%@ page import="org.labkey.api.data.Container"%>
<%@ page import="org.labkey.api.study.actions.AssayHeaderView" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="java.util.Map" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<AssayHeaderView> me = (JspView<AssayHeaderView>) HttpView.currentView();
    AssayHeaderView bean = me.getModelBean();
%>
<table class="normal" cellspacing="0" width="100%">
    <tr>
        <td>
            <p><%= h(bean.getProtocol().getProtocolDescription()) %></p>
            <%
                if (bean.showProjectAdminLink())
                {
                    Container assayContainer = bean.getProtocol().getContainer();
                    ActionURL assayLink = getViewContext().cloneActionURL();
                    assayLink.setExtraPath(assayContainer.getPath());
                    assayLink.setPageFlow("assay");
            %>
            <p>This assay design is defined in folder <b><%= h(assayContainer.getPath())%></b>.  To manage this assay design, you must
                <%= textLink("view assay in definition folder", assayLink)%>.</p>
            <%
                }
            %>
            <p>
                <%
                    if (bean.getManagePopupView() != null)
                    {
                %>
                [<a id="manageMenu" href="javascript: void(0);">manage assay design >></a>]
                <%
                    }
                    for (Map.Entry<String, ActionURL> entry : bean.getLinks().entrySet())
                    {
                        ActionURL current = getViewContext().getActionURL();
                        ActionURL link = entry.getValue();
                        boolean active = current.getLocalURIString().equals(link.getLocalURIString()); %>
                        <%= active ? "<strong>" : "" %><%= textLink(entry.getKey(), link) %><%= active ? "</strong>" : "" %>
                <% } %>
            </p>
        </td>
    </tr>
</table>
<%
    if (bean.getManagePopupView() != null)
        me.include(bean.getManagePopupView(), out);
%>
