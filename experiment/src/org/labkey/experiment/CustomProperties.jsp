<%
/*
 * Copyright (c) 2005-2017 LabKey Corporation
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
<%@ page import="org.labkey.api.view.JspView"%>
<%@ page import="org.labkey.experiment.CustomProperties" %>
<%@ page import="org.labkey.experiment.CustomPropertiesView" %>
<%@ page import="java.io.IOException" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<CustomPropertiesView.CustomPropertiesBean> me = (JspView<CustomPropertiesView.CustomPropertiesBean>) HttpView.currentView();
    CustomPropertiesView.CustomPropertiesBean form = me.getModelBean();
%>
<table class="lk-fields-table">
<%
    final JspWriter fout = out;

    CustomProperties.iterate(getContainer(), form.getCustomProperties().values(), form.getRenderers(), (indent, description, value) ->
    {
        try
        {
            fout.println("<tr>");
            fout.println("    <td class=\"labkey-form-label\">");

            int i = 0;
            while(i < indent)
            {
                i++;
                fout.print(text("&nbsp;&nbsp;&nbsp;&nbsp;"));
            }

            // Note: StandardPropertyRenderer HTML encodes description and value
            fout.println(description);
            fout.println("    </td>");
            fout.println("    <td>" + value + "</td>");
            fout.println("</tr>");
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    });
%>
</table>