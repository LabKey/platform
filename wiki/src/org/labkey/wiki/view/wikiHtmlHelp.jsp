<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView"%>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%
    JspView me = (JspView) HttpView.currentView();
    ViewContext ctx = me.getViewContext();
    boolean useVisualEditor = ((Boolean)ctx.get("useVisualEditor")).booleanValue();
%>
<table>
    <tr>
        <td colspan=2><b>Formatting Guide:</b></td>
    </tr>
    <% if (!useVisualEditor)
    {
    %>
    <tr>
        <td>Link to a wiki page</td>
        <td>&lt;a href="pageName"&gt;My Page&lt;/a&gt;</td>
    </tr>
    <tr>
        <td>Link to an attachment</td>
        <td>&lt;a href="attachment.doc"&gt;My Document&lt;/a&gt;</td>
    </tr>
    <tr>
        <td>Show an attached image</td>
        <td>&lt;img src="imageName.jpg"&gt;</td>
    </tr>
    <% } else
    {
    %>
    <tr>
        <td>Link to a wiki page</td>
        <td>Select text and right click. Then select "Insert/edit link."
         Type the name of the wiki page in "Link URL" textbox.</td>
    </tr>
    <tr>
        <td>Link to an attachment</td>
        <td>Select text and right click. Then select "Insert/edit link."
         Type the name of the attachment with the file extension in "Link URL" textbox.</td>
    </tr>
    <% }  %>
</table>