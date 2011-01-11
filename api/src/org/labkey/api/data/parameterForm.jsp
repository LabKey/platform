<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page import="org.labkey.api.query.QueryService" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.data.DataRegion" %>
<%@ page import="java.util.Collection" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<labkey:errors/>
<%
    DataRegion.ParameterViewBean bean = (DataRegion.ParameterViewBean)HttpView.currentModel();
%>
<script>
(function (){
var dataregion = <%=q(bean.dataRegion)%>;
var divId = <%=q(bean.dataRegion + "ParameterDiv")%>;
var decl = [
    <%
    Collection<QueryService.ParameterDecl> decls = bean.params;
    Map values = bean.values;
    String COMMA = "";
    for (QueryService.ParameterDecl p : decls)
    {
        Object value = null==values?null:values.get(p.getName());
        if (null == value)
            value = p.getDefault();
        %><%=COMMA%><%
        %>{name:<%=q(p.getName())%><%
        %>,type:<%=q(p.getType().name())%><%
        %>,xtype:<%=q(p.getType().xtype)%><%
        %>,defaultValue:<%=q(org.apache.commons.beanutils.ConvertUtils.convert(p.getDefault()))%><%
        %>,value:<%=q(org.apache.commons.beanutils.ConvertUtils.convert(value))%><%
        %>}<%
        COMMA = ",";
    }
    %>];
var formpanel;
function submitHandler()
{
    var values = formpanel.getForm().getValues();
    var query = LABKEY.ActionURL.getParameters();
    query = Ext.apply(query||{}, values);
    var u = LABKEY.ActionURL.queryString(query);
    window.location.search = "?" + u;
}
Ext.onReady(function()
{
    var items = [];
    for (var i=0 ; i<decl.length ; i++)
    {
        var p = decl[i];
        var item = {};
        item.xtype = p.xtype || 'textfield';
        item.fieldLabel = p.name;
        item.name = dataregion + "!" + p.name;
        items.push(item);
    }
    formpanel = new Ext.form.FormPanel({
        items:items,
        bbar:[{text:'Submit', handler:submitHandler}]
    });
    formpanel.render(divId);
});
})();
</script>
<div id="<%=h(bean.dataRegion + "ParameterDiv")%>"></div>