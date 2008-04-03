<%@ page import="org.labkey.api.query.QueryAction"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.query.controllers.SourceForm" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    SourceForm form = (SourceForm)HttpView.currentModel();
    boolean canEdit = form.canEdit();
%>
<labkey:errors />
<form method="POST" action="<%=form.urlFor(QueryAction.sourceQuery)%>">
    <input type="hidden" name="ff_redirect" id="ff_redirect" value="<%=form.ff_redirect%>">
    <p>SQL:<br>
<textarea rows="20" cols="80" wrap="off"
          name="ff_queryText"<%=canEdit ? "" : " READONLY"%>><%=h(form.ff_queryText)%></textarea><br>
</p><%
if (canEdit)
{
    %><labkey:button text="Save" onclick="submit_onclick('sourceQuery')" />&nbsp;<%
}
    %><labkey:button text="Design View" onclick="submit_onclick('designQuery')" />&nbsp;<labkey:button text="Run Query" onclick="submit_onclick('executeQuery')" />
<p>Metadata XML:<br>
    <textarea rows="20" cols="80" wrap="off" name="ff_metadataText"<%=canEdit ? "" : " READONLY"%>><%=h(form.ff_metadataText)%></textarea>
</p>

</form>


<script type="text/javascript">
function _id(s) {return document.getElementById(s);}

var origQueryText = _id("ff_queryText").value;
var origMetadataText = _id("ff_metadataText").value;

function isDirty()
{
    return origQueryText != _id("ff_queryText").value || origMetadataText != _id("ff_metadataText").value;  
}
window.onbeforeunload = LABKEY.beforeunload(isDirty);

function submit_onclick(method)
{
    document.getElementById('ff_redirect').value = method;
    window.onbeforeunload = null;
}
</script>