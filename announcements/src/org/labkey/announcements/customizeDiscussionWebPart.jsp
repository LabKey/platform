<%
/*
 * Copyright (c) 2006-2014 LabKey Corporation
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
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.Portal" %>
<%@ page import="java.util.Map" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    HttpView<Portal.WebPart> me = (HttpView<Portal.WebPart>) HttpView.currentView();
    Portal.WebPart part = me.getModelBean();
    Map<String, String> props = part.getPropertyMap();
%>
This webpart displays a single discussion.  It's designed to work in conjunction with custom pages built using the JavaScript API, though it could be placed on a portal page to display a single, specific discussion.<br><br>

<labkey:form name="frmCustomize" method="post" action="<%=h(part.getCustomizePostURL(getViewContext()))%>">
    <table>
        <tr>
            <td>Entity Id:</td>
            <td><input type="text" name="entityId" width="120" value="<%=h(props.get("entityId"))%>"></td>
        </tr>
        <tr>
            <td colspan="2"><labkey:button text="Submit"/></td>
        </tr>
    </table>
</labkey:form>