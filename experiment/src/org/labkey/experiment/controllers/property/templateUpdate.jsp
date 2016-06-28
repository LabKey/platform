<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.experiment.controllers.property.PropertyController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%

    HttpView<PropertyController.CompareWithTemplateModel> me = HttpView.currentView();
    PropertyController.CompareWithTemplateModel model = me.getModelBean();
    ViewContext context = me.getViewContext();
%>
<style>
    DIV.comparetemplate TH
    {
        text-align:left;
    }
    DIV.comparetemplate TD.colA
    {
        min-width:80pt;
    }
    DIV.comparetemplate TD.colB
    {
        min-width:80pt;
    }
    DIV.comparetemplate TD.colSTATUS
    {
        min-width:80pt;
    }
</style>
<div id="updateDomainDiv">

</div>
<script src="<%=getContextPath()%>/Experiment/domainTemplate.js"></script>
<script>
(function($)
{
    var schemaName = <%=  q(model.schemaName) %>;
    var queryName = <%=  q(model.queryName) %>;
    var domain = <%= text(PropertyController.convertDomainToJson(model.domain)) %>;
    domain.schemaName = schemaName;
    domain.queryName = queryName;
    var template = <%= text(null==model.template ? "null" : PropertyController.convertDomainToJson(model.template)) %>;
    <% if (null == model.info) { %>
    var templateInfo = null;
    <% } else { %>
    var templateInfo = {module:<%=q(model.info.getModuleName())%>, group:<%=q(model.info.getTemplateGroupName())%>, template:<%=q(model.info.getTableName())%>};
    <% } %>
    renderUpdateDomainView("#updateDomainDiv", templateInfo, domain, template);
})(jQuery);
</script>