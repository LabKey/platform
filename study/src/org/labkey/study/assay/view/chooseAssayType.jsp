<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.study.assay.AssayProvider" %>
<%@ page import="java.util.List" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<List<org.labkey.api.study.assay.AssayProvider>> me = (JspView<List<AssayProvider>>) HttpView.currentView();
    List<AssayProvider> providers = me.getModelBean();
%>
<form action="designerRedirect.view">
    <div>
        Select the assay design type:
        <select name="providerName">
            <% for (AssayProvider provider : providers)
            { %>
                <option value="<%= h(provider.getName()) %>"><%= h(provider.getName()) %></option>
            <% } %>
        </select>
    </div>
    <div><%= buttonLink("Cancel", getViewContext().cloneActionURL().setAction("begin")) %> <%= buttonImg("Next" )%></div>
</form>