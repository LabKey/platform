<%
    /*
     * Copyright (c) 2017 LabKey Corporation
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
<%@ page import="org.labkey.api.exp.property.DomainProperty" %>
<%@ page import="org.labkey.api.study.actions.AssayRunUploadForm" %>
<%@ page import="org.labkey.api.study.assay.AssayProvider" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="java.util.Map" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    JspView<AssayRunUploadForm> me = (JspView<AssayRunUploadForm>) HttpView.currentView();
    AssayRunUploadForm<? extends AssayProvider> bean = me.getModelBean();

%>
<labkey:form layout="horizontal">
<%
    for (Map.Entry<DomainProperty, String> entry : bean.getBatchProperties().entrySet())
    {
        if (entry.getKey().isShownInInsertView())
        {
%>
    <labkey:input type="displayfield"
          label="<%= h(entry.getKey().getPropertyDescriptor().getNonBlankCaption()) %>"
          value="<%= h(bean.getBatchPropertyValue(entry.getKey().getPropertyDescriptor(), entry.getValue())) %>"
    />
<%
        }
    }
%>
</labkey:form>