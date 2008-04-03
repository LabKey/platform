<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.query.controllers.SourceForm"%>
<%@ page import="org.labkey.api.query.QueryAction"%>
<%@ page import="org.labkey.query.controllers.MetadataForm"%>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.query.controllers.Page" %>
<%
    MetadataForm form = (MetadataForm) __form;
    boolean canEdit = form.canEdit();
%>
<labkey:errors />
<form method="POST" action="<%=form.getQueryDef().urlFor(QueryAction.metadataQuery)%>">
<%=PageFlowUtil.getStrutsError(request, null)%>
<textarea rows="20" cols="80" wrap="off"
          name="ff_metadataText"<%=canEdit ? "" : " READONLY"%>><%=h(form.ff_metadataText)%></textarea><br>
<% if (canEdit)
{ %>
<input type="Submit" name="ff_action" value="Save Changes"/>
<% } %>
</form>