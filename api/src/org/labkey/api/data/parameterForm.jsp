<%
/*
 * Copyright (c) 2011-2014 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
%>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page import="org.apache.commons.beanutils.ConvertUtils" %>
<%@ page import="org.labkey.api.data.DataRegion" %>
<%@ page import="org.labkey.api.query.QueryService" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="java.util.Collection" %>
<%@ page import="java.util.Map" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<labkey:errors/>
<%
    DataRegion.ParameterViewBean bean = (DataRegion.ParameterViewBean)HttpView.currentModel();
%>
<script>
(function (){
var dataRegionName = <%=q(bean.dataRegionName)%>;
var divId = <%=q("dataregion_" + bean.dataRegionName)%>;
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
        %><%=text(COMMA)%><%
        %>{name:<%=q(p.getName())%><%
        %>,jdbctype:<%=q(p.getJdbcType().name())%><%
        %>,jsontype:<%=q(p.getJdbcType().json)%><%
        %>,xtype:<%=q(p.getJdbcType().xtype)%><%
        %>,defaultValue:<%=q(ConvertUtils.convert(p.getDefault()))%><%
        %>,value:<%=q(ConvertUtils.convert(value))%><%
        %>}<%
        COMMA = ",";
    }
    %>];
var formpanel;
function submitHandler()
{
    var valuesRaw = formpanel.getForm().getValues();
    formpanel.destroy();
    delete formpanel;

    var values = {};
    for (var i=0 ; i<decl.length ; i++)
    {
        var parameter = dataRegionName + ".param." + decl[i].name;
        values[parameter] = valuesRaw[parameter];
    }

    var dataRegion = LABKEY.DataRegions[dataRegionName];
    if (dataRegion)
    {
        dataRegion.setParameters(values);
    }
    else
    {
        var query = LABKEY.ActionURL.getParameters();
        query = Ext.apply(query||{}, values);
        var u = LABKEY.ActionURL.queryString(query);
        window.location.search = "?" + u;
    }
}
Ext.onReady(function()
{
    var items = [];
    for (var i=0 ; i<decl.length ; i++)
    {
        var p = decl[i];
        var item = {};
        item.xtype = p.xtype || 'textfield';
        if (p.jsontype == 'int')
            item.decimalPrecision=0;
        item.fieldLabel = p.name;
        item.width = 250;
        item.name = <%=PageFlowUtil.qh(bean.dataRegionName)%> + ".param." + Ext.util.Format.htmlEncode(p.name);
        item.value= LABKEY.ActionURL.getParameter(item.name) || p.value;
        items.push(item);
    }
    formpanel = new LABKEY.ext.FormPanel({
        items:items,
        bodyStyle: 'padding: 5px;',
        bbar:[{text:'Submit', handler:submitHandler}]
    });
    formpanel.render(divId);
});
})();
</script>
<%-- NOTE: div id must match DataRegion.js expected <table> element so it can be removed in DataRegion.destroy() --%>
<div id=<%=PageFlowUtil.qh("dataregion_" + bean.dataRegionName)%>></div>
