<%
/*
 * Copyright (c) 2011 LabKey Corporation
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
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView"%>
<%@ page import="org.labkey.api.writer.Writer"%>
<%@ page import="java.util.Collection" %>
<%@ page import="java.util.LinkedList" %>
<%@ page import="org.labkey.core.admin.FolderSettingsAction.FolderSettingsForm" %>
<%@ page import="org.labkey.core.admin.writer.FolderSerializationRegistryImpl" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    JspView<FolderSettingsForm> me = (JspView<FolderSettingsForm>) HttpView.currentView();
    FolderSettingsForm form = me.getModelBean();
%>
<labkey:errors/>
<form action="" method="post">
    <table>
        <tr><td>Folder objects to export:</td></tr>
        <%
            Collection<Writer> writers = new LinkedList<Writer>(FolderSerializationRegistryImpl.get().getInternalFolderWriters());
            writers.addAll(FolderSerializationRegistryImpl.get().getRegisteredFolderWriters());

            for (Writer writer : writers)
            {
                String text = writer.getSelectionText();

                if (null != text)
                { %>
        <tr><td><input type="checkbox" name="types" value="<%=text%>" checked> <%=text%></td></tr><%
                }
            }
        %>
        <tr><td>&nbsp;</td></tr>
        <tr><td>Export to:</td></tr>
        <tr><td><input type="radio" name="location" value="0">Pipeline root <b>export</b> directory, as individual files</td></tr>
        <tr><td><input type="radio" name="location" value="1">Pipeline root <b>export</b> directory, as zip file</td></tr>
        <tr><td><input type="radio" name="location" value="2" checked>Browser as zip file</td></tr>
        <tr><td>&nbsp;</td></tr>
        <tr>
            <td>
                <%=PageFlowUtil.generateSubmitButton("Export")%>&nbsp;<%=PageFlowUtil.generateButton("Cancel", "manageFolders.view")%>
            </td>
        </tr>
    </table>
</form>