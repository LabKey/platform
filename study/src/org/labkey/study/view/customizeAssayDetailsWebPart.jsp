<%@ page import="org.labkey.api.view.*" %>
<%@ page import="org.labkey.api.study.assay.AssayService" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.TreeMap" %>
<%@ page import="org.labkey.api.study.assay.AssayProvider" %>
<%@ page import="org.labkey.study.view.AssayDetailsWebPartFactory" %>
<%@ page import="org.labkey.api.exp.api.ExpProtocol" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<Portal.WebPart> me = (JspView<Portal.WebPart>) HttpView.currentView();
    Portal.WebPart bean = me.getModelBean();
    ViewContext ctx = me.getViewContext();
    ActionURL postUrl = new ActionURL("Project", "customizeWebPart.post", ctx.getContainer());
    String viewProtocolIdStr = bean.getPropertyMap().get(AssayDetailsWebPartFactory.PREFERENCE_KEY);
    boolean showButtons = Boolean.parseBoolean(bean.getPropertyMap().get(AssayDetailsWebPartFactory.SHOW_BUTTONS_KEY));
    int viewProtocolId = -1;
    try
    {
        if (viewProtocolIdStr != null)
            viewProtocolId = Integer.parseInt(viewProtocolIdStr);
    }
    catch (NumberFormatException e)
    {
        // fall through
    }
    Map<String, Integer> nameToId = new TreeMap<String, Integer>();
    for (ExpProtocol protocol : AssayService.get().getAssayProtocols(ctx.getContainer()))
    {
        AssayProvider provider = AssayService.get().getProvider(protocol);
        nameToId.put(provider.getName() + ": " + protocol.getName(), protocol.getRowId());
    }
%>
<p>Each Assays webpart can be customized to display a list of all available assays or a summary of a specific assay.</p>

<p>This webpart should display:<br>
<form action="<%=postUrl%>" method="post">
    <input type="hidden" name="pageId" value="<%=bean.getPageId()%>">
    <input type="hidden" name="index" value="<%=bean.getIndex()%>">
    <select name="<%= AssayDetailsWebPartFactory.PREFERENCE_KEY %>">
<%
    for (Map.Entry<String, Integer> entry : nameToId.entrySet())
    {
%>
     <option value="<%= entry.getValue() %>" <%= viewProtocolId == entry.getValue() ? "SELECTED" : ""%>>
        <%= h(entry.getKey()) %></option>
<%
    }
%>
    </select></p>
<p>
    <input type="checkbox" name="<%= AssayDetailsWebPartFactory.SHOW_BUTTONS_KEY%>" value="true" <%= showButtons ? "CHECKED" : "" %>> Show buttons in web part
</p>
    <%=buttonImg("Submit")%> <%=buttonLink("Cancel", "begin.view")%>

</form>