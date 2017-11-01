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
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<table class="lk-fields-table">
    <tr>
        <td class="labkey-export-tab-options">
            <table class="labkey-export-tab-layout">
                <tr>
                    <td><span class="labkey-strong">Set Folder default</span></td>
                    <td><select>
                        <option value="daily">Daily</option>
                        <option value="individual">Individual</option>
                        <option value="none">None</option>
                    </select></td>
                    <td><%= PageFlowUtil.button("Set folder default").href("javascript:void(0)").onClick("alert('on set folder default');") %></td>
                </tr>
                <tr>
                    <td><span class="labkey-strong">Configure Selected Users</span></td>
                    <td><select>
                        <option value="default">Folder default</option>
                        <option value="daily">Daily</option>
                        <option value="individual">Individual</option>
                        <option value="none">None</option>
                    </select></td>
                    <td><%= button("Configure selected users").submit(true).attributes("labkey-requires-selection=\"users\"") %></td>
                </tr>
            </table>
        </td>
    </tr>
</table>

