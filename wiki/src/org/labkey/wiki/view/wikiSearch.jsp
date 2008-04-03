<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.wiki.model.SearchViewContext" %>
<%
    JspView<SearchViewContext> _me = (JspView<SearchViewContext>) HttpView.currentView();
    SearchViewContext _ctx = _me.getModelBean();
%>
<script type="text/javascript">
    function submitSearch()
    {
        var frm = document.getElementById("frmSearch");
        if(frm)
            frm.submit();
    }
</script>
<form action="<%=_ctx.getSearchUrl()%>" id="frmSearch">
<input type="hidden" name="includeSubfolders" value="on"/>
<table width="100%" cellpadding="2px" cellspacing="0" border="0">
    <tr>
        <td width="99%" align="left">
            <input type="text" name="search" style="width:100%">
        </td>
        <td width="1%">
            <%=PageFlowUtil.buttonLink("Search", "javascript:{}", "submitSearch();")%>
        </td>
    </tr>
</table>
</form>
