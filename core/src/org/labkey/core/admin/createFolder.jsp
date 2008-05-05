<%@ page import="org.labkey.api.data.Container"%>
<%@ page import="org.labkey.api.module.FolderType" %>
<%@ page import="org.labkey.api.module.ModuleLoader" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.core.admin.AdminController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<AdminController.ManageFoldersForm> me = (JspView<AdminController.ManageFoldersForm>) HttpView.currentView();
    AdminController.ManageFoldersForm form = me.getModelBean();
    Container c = me.getViewContext().getContainer();

    String name = form.getName();
    String containerDescription = (c.isRoot() ? "Project" : "Folder");
    String containerType = containerDescription.toLowerCase();
%>

<form name="createForm" action="createFolder.view" method="post">
    <%=formatMissedErrors("form")%>
    <table border=0 cellspacing=2 cellpadding=0 class="dataRegion">
        <tr><td colspan=2><%
        if (!c.isRoot())
        {
            %>New <%=h(containerType)%> under <b><%=h(c.getName())%></b><%
        }
        else
        {
            %>New <%=h(containerType)%><%
        } %>
        </tr>
        <tr>
            <td>Name:&nbsp;</td>
            <td><input id="name" name="name" value="<%=h(name)%>"/></td>
        </tr>
        <tr>
            <td>
                Folder Type
            </td>
            <td>
                <table>
<%
    String folderTypeName = form.getFolderType();
    if (null == folderTypeName) //Try to avoid "None"
        folderTypeName = FolderType.NONE.equals(c.getFolderType()) ? "Collaboration" : c.getFolderType().getName();
    int radioIndex = 0;
    for (FolderType ft : ModuleLoader.getInstance().getFolderTypes())
    {
%>
                    <tr>
                        <td valign="top">
                            <input type="radio" name="folderType" value="<%=h(ft.getName())%>" <%=folderTypeName.equals(ft.getName()) ? "checked" : ""%> >
                         </td>
                        <td valign="top">
                            <span style="cursor:pointer;font-weight:bold" onclick="document.createForm.folderType[<%=radioIndex%>].checked = true;"><%=h(ft.getLabel())%></span><br>
                            <%=h(ft.getDescription())%>
                        </td>
                    </tr>
<%
    radioIndex++;
}
%>
                </table>
            </td>
        </tr>
    </table>

    <table border=0 cellspacing=2 cellpadding=0>
        <tr>
            <td><input type="image" src='<%=PageFlowUtil.buttonSrc("Next")%>'></td>
            <td><%
                if (!c.isRoot())
                {
                    %><a href="manageFolders.view"><%
                }
                else
                {
                    %><a href="" onclick="window.history.back(); return false;"><%
                } %><img border=0 src='<%=PageFlowUtil.buttonSrc("Cancel")%>'></a></td>
        </tr>
    </table>
</form>
