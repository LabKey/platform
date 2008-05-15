<%@ page import="org.labkey.wiki.model.Wiki" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.util.DateUtil" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%
    JspView<Wiki> me = (JspView<Wiki>) HttpView.currentView();
    Wiki wiki = me.getModelBean();
%>
<div class="normal" style="padding:10px;">
    <%  if (null == wiki.latestVersion())
        {%>
            This page does not have any printable content. The page may have been deleted or renamed by another user.<br><br>
        <%}
        else
        {%>
            <table style="width:100%;">
                <tr>
                    <td align=left><h3 class=".heading-1"><%=PageFlowUtil.filter(wiki.latestVersion().getTitle())%></h3></td>
                    <td align=right><%=DateUtil.formatDate()%></td>
                </tr>
            </table>
            <hr>
            <%=wiki.latestVersion().getHtml(me.getViewContext().getContainer(),wiki)%>
        <%}%>
</div>