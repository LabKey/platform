<%
/*
 * Copyright (c) 2011-2017 LabKey Corporation
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
<%-- NOTE: div id must match DataRegion.js expected <table> element so it can be removed in DataRegion.destroy() --%>
<div id=<%=PageFlowUtil.qh(bean.dataRegionDomId)%>></div>
<script type="text/javascript">
(function(){
var regionDomId = <%=q(bean.dataRegionDomId)%>;
var dataRegionName = <%=q(bean.dataRegionName)%>;
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

    <%-- This page is loaded only at render time, require Ext 4 --%>
    LABKEY.requiresExt4Sandbox(function() {
        Ext4.onReady(function() {

            var region = LABKEY.DataRegions[dataRegionName];
            var items = [], item, p;

            for (var i=0; i < decl.length; i++) {
                p = decl[i];
                name = Ext4.htmlEncode(region.name) + '.param.' + Ext4.htmlEncode(p.name);

                item = {
                    xtype: p.xtype || 'textfield',
                    fieldLabel: p.name,
                    width: 250,
                    name: name,
                    value: LABKEY.ActionURL.getParameter(name) || p.value
                };

                if (p.jsontype == 'int') {
                    item.decimalPrecision = 0;
                }
                else if (p.jsontype == 'float') {
                    // Allow lots of precision or ExtJS will truncate potentially useful digits
                    item.decimalPrecision = 15;
                }
                items.push(item);
            }

            var parameterForm = Ext4.create('Ext.form.Panel', {
                renderTo: regionDomId,
                border: false,
                bodyStyle: 'padding: 5px; background-color: transparent;',
                items: items,
                dockedItems: [{
                    xtype: 'toolbar',
                    dock: 'bottom',
                    ui: 'footer',
                    style: 'background-color: transparent;',
                    items: [{
                        text: 'Submit',
                        handler: function() {
                            var valuesRaw = parameterForm.getForm().getValues();
                            parameterForm.destroy();
                            delete parameterForm;

                            var values = {};
                            for (var i=0; i < decl.length; i++) {
                                var parameter = dataRegionName + '.param.' + decl[i].name;
                                values[parameter] = valuesRaw[parameter];
                            }

                            var dataRegion = LABKEY.DataRegions[dataRegionName];
                            if (dataRegion) {
                                dataRegion.setParameters(values);
                            }
                            else {
                                var query = Ext4.apply(LABKEY.ActionURL.getParameters() || {}, values);
                                window.location.search = "?" + LABKEY.ActionURL.queryString(query);
                            }
                        }
                    }]
                }]
            });

        });
    });
})();
</script>
