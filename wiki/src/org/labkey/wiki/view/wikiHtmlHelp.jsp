<%
/*
 * Copyright (c) 2007-2011 LabKey Corporation
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
<%
    boolean useVisualEditor = (Boolean)HttpView.currentView().getModelBean();
%>
<table>
    <tr>
        <td colspan=2><b>Formatting Guide:</b></td>
    </tr>
    <% if (!useVisualEditor)
    {
    %>
    <tr>
        <td>Link to a wiki page</td>
        <td>&lt;a href="pageName"&gt;My Page&lt;/a&gt;</td>
    </tr>
    <tr>
        <td>Link to an attachment</td>
        <td>&lt;a href="attachment.doc"&gt;My Document&lt;/a&gt;</td>
    </tr>
    <tr>
        <td>Show an attached image</td>
        <td>&lt;img src="imageName.jpg"&gt;</td>
    </tr>
    <% } else
    {
    %>
    <tr>
        <td>Link to a wiki page</td>
        <td>Select text and right click. Then select "Insert/edit link."
         Type the name of the wiki page in "Link URL" textbox.</td>
    </tr>
    <tr>
        <td>Link to an attachment</td>
        <td>Select text and right click. Then select "Insert/edit link."
         Type the name of the attachment with the file extension in "Link URL" textbox.</td>
    </tr>
    <% }  %>
</table>