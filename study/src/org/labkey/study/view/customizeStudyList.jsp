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
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.Portal" %>
<%@ page import="org.labkey.study.view.StudyListWebPartFactory" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<Portal.WebPart> me = (JspView<Portal.WebPart>) HttpView.currentView();
    Portal.WebPart webPart = me.getModelBean();
    String displayType = webPart.getPropertyMap().get(StudyListWebPartFactory.DISPLAY_TYPE_PROPERTY);
%>
<% // Post to current action; URL includes pageId and index parameters %>
<labkey:form name="frmCustomize" method="post">
    <table>
        <tr>
            <td>Select the display type for the study list.</td>
            <td>
                <select name="displayType">
                    <option value="grid"<%=selected("grid".equals(displayType))%>>Grid</option>
                    <option value="details"<%=selected(null == displayType || "details".equals(displayType))%>>Details</option>
                </select>
            </td>
         </tr>
    <tr>
    </table>
    <br/>
    <%= button("Submit").submit(true) %>
    <%= button("Cancel").href(getContainer().getStartURL(getUser())) %>
</labkey:form>