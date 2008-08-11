<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.study.controllers.samples.SpringSpecimenController" %>
<%@ page import="org.labkey.study.model.Site" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<SpringSpecimenController.SelectSpecimenProviderBean> me = (JspView<SpringSpecimenController.SelectSpecimenProviderBean>) HttpView.currentView();
    SpringSpecimenController.SelectSpecimenProviderBean bean = me.getModelBean();
%>
<labkey:errors/>
<p>Vials from the selected speicmens can be shipped to you from multiple locations.  Please select your preferred location:</p>
<form action="<%= bean.getFormTarget().getLocalURIString() %>" method="POST">
<%= bean.getSourceForm().getHiddenFormInputs() %>
<p>
    <select name="preferredLocation">
    <%
        for (Site site : bean.getPossibleSites())
        {
    %>
    <option value="<%= site.getRowId() %>"><%= h(site.getLabel())%></option>
    <%
        }
    %>
</select>
</p>
<p>
    <%= buttonLink("Cancel", "javascript:back()")%>
    <%= buttonImg("Select") %> 
</p>
</form>