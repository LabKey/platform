<%
/*
 * Copyright (c) 2014-2015 LabKey Corporation
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
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.Portal" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%
    Portal.WebPart webPart = (Portal.WebPart)HttpView.currentModel();
    ViewContext context = HttpView.currentContext();
    String selected = StringUtils.defaultString(webPart.getPropertyMap().get("style"), "full");
%>

<labkey:form name="frmCustomize" method="post" action="<%=webPart.getCustomizePostURL(context)%>">
    <input type="radio" name="style" value="full"<%=checked("full".equals(selected))%>>&nbsp;full<br>
    <input type="radio" name="style" value="simple"<%=checked("simple".equals(selected))%>>&nbsp;simple<br>
    <input type="submit">
</labkey:form>
