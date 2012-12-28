<%
/*
 * Copyright (c) 2009-2012 LabKey Corporation
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
<%@ page import="org.labkey.api.data.DbScope" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.query.controllers.QueryController" %>
<%@ page import="org.labkey.query.persist.LinkedSchemaDef" %>
<%@ page import="org.labkey.data.xml.externalSchema.TemplateSchemaType" %>
<%@ page import="org.labkey.api.util.XmlBeansUtil" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    QueryController.LinkedSchemaBean bean = (QueryController.LinkedSchemaBean)HttpView.currentModel();
    LinkedSchemaDef def = bean.getSchemaDef();

%>

<labkey:errors/>
<div id="form"></div>

<table>
    <tr>
        <td>Schema Name</td>
        <td><%=h(def.getUserSchemaName())%></td>
    </tr>
    <tr>
        <td>Schema Template</td>
        <td>
            <%=h(def.getSchemaTemplate())%>
            <%
                if (def.getSchemaTemplate() != null)
                {
                    TemplateSchemaType template = def.lookupTemplate(getViewContext().getContainer());
                    if (template == null)
                    {
                        %><span class='labkey-error'>template not found</span><%
                    }
                    else
                    {
                        %><pre><%=h(template.xmlText(XmlBeansUtil.getDefaultSaveOptions()))%></pre><%
                    }
                }
            %>
        </td>
    </tr>
    <tr>
        <td>Source Schema</td>
        <td><%=h(def.getSourceSchemaName())%></td>
    </tr>
    <tr>
        <td>Source Container</td>
        <td><%=h(def.getSourceContainerId())%></td>
    </tr>
    <tr>
        <td>Linked Definition</td>
        <td><%=h(def.getSchemaTemplate())%></td>
    </tr>
    <tr>
        <td>Tables</td>
        <td><%=h(def.getTables())%></td>
    </tr>
</table>
