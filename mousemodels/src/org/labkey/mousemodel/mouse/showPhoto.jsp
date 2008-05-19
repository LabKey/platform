<%
/*
 * Copyright (c) 2007-2008 LabKey Corporation
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
<%@ page import="org.labkey.api.attachments.Attachment" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<table border=0>
    <%
        ViewContext ctx = HttpView.currentContext();
        Attachment[] attachments = (Attachment[]) ctx.get("attachments");
        boolean canDelete = (Boolean) ctx.get("canDelete");
        ActionURL deleteUrl = (ActionURL)ctx.get("deleteUrl");

        int index = 0;

        for (Attachment a : attachments) {
            String downloadUrl = a.getDownloadUrl("MouseModel-Mouse");

            if ((index % 2) == 0)
                out.print("<tr>");%>
    <td class="normal" align="center">
    <a href="<%=downloadUrl%>" target="_blank"><img border=0 width=300 src="<%=downloadUrl%>"></a><br>
<%
        if (canDelete)
        {
            deleteUrl.replaceParameter("name", a.getName());
%>
    [<a href="#delete" onclick="window.open('<%=deleteUrl.getEncodedLocalURIString()%>', null, 'height=200,width=450', false);">Delete</a>]
<%
        }

        out.print("</td>");
        index++;

        if ((index % 2) == 0)
            out.print("</tr>");
    }

    if ((index % 2) != 0)
        out.print("</tr>");
%>
</table>
