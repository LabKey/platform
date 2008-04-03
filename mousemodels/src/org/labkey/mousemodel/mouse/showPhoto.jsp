<%@ page import="org.labkey.api.attachments.Attachment" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<table border=0>
    <%
        ViewContext ctx = HttpView.currentContext();
        Attachment[] attachments = (Attachment[]) ctx.get("attachments");
        boolean canDelete = (Boolean) ctx.get("canDelete");
        ActionURL deleteUrl = (ActionURL)ctx.get("deleteUrl");

        int index = 0;

        for (Attachment a : attachments) {
            String downloadUrl = a.getDownloadUrl("MouseModel-Mouse");

            if ((index % 2) == 0)
                out.print("<tr>");%>
    <td class="normal" align="center">
    <a href="<%=downloadUrl%>" target="_blank"><img border=0 width=300 src="<%=downloadUrl%>"></a><br>
<%
        if (canDelete)
        {
            deleteUrl.replaceParameter("name", a.getName());
%>
    [<a href="#delete" onclick="window.open('<%=deleteUrl.getEncodedLocalURIString()%>', null, 'height=200,width=450', false);">Delete</a>]
<%
        }

        out.print("</td>");
        index++;

        if ((index % 2) == 0)
            out.print("</tr>");
    }

    if ((index % 2) != 0)
        out.print("</tr>");
%>
</table>
