<%
/*
 * Copyright (c) 2009-2015 LabKey Corporation
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
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.security.UserManager" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.study.importer.StudyReload.ReloadInterval" %>
<%@ page import="org.labkey.study.model.StudyImpl" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    StudyImpl study = getStudy();
    boolean allowReload = study.isAllowReload();
    boolean queryValidation = study.isValidateQueriesAfterImport();
    ReloadInterval currentInterval = ReloadInterval.getForSeconds(study.getReloadInterval());

    User reloadUser = (allowReload && null != study.getReloadUser() ? UserManager.getUser(study.getReloadUser()) : null);
%>
<labkey:panel>
    <labkey:form action="" method="post">
        <table width="65%">
            <%=formatMissedErrorsInTable("form", 2)%>
            <tr>
                <td colspan=2>A study can be configured to reload its data from the pipeline root, either manually or automatically at preset intervals:
                    <ul>
                        <li><strong>Manual Reload:</strong> Check the "Allow Study Reload" box and set the "Reload Interval" to &lt;Never&gt; to
                            configure the study for manual reload. A reload attempt can be initiated by an administrator
                            clicking the "Attempt Reload Now" button below or by an external script invoking that same URL.</li>
                        <li><strong>Automatic Reload:</strong> Check the "Allow Study Reload" box and set the "Reload Interval" to a time interval
                            to configure the study for automatic reload. In this case, a reload is attempted automatically each
                            time the specified interval elapses.</li>
                    </ul>
                    In either case, a reload attempt causes the server to locate a file named <strong>studyload.txt</strong> in the
                    pipeline root. If this file has changed (i.e., the file's modification time has changed) since the last
                    reload then the server will reload the study data from the pipeline.  For more information about the file
                    formats used see the <%=helpLink("importExportStudy", "Import/Export/Reload a Study documentation page")%>.
                </td>
            </tr>
            <tr>
                <td colspan=2>&nbsp;</td>
            </tr>
            <tr>
                <th align="left" width=200>Allow Study Reload</th>
                <td><input id="allowReload" name="allowReload" type="checkbox"<%=checked(allowReload)%> onchange="updateDisplay();"></td>
            </tr>
            <tr>
                <th align="left" width=200>Reload Interval</th>
                <td align="left"><select name="interval" id="interval"><%
                    for (ReloadInterval interval : ReloadInterval.values())
                        if (interval.shouldDisplay())
                            out.println("<option value=\"" + interval.getSeconds() + "\"" + (interval == currentInterval ? " selected>" : ">") + h(interval.getDropDownLabel()) + "</option>");
                %>
                </select></td>
            </tr>
            <tr>
                <th align="left" width=200>Reload User</th>
                <td><%=allowReload ? (null == reloadUser ? "<div class=\"labkey-error\">Error: Reload user not defined!</div>" : h(reloadUser.getDisplayName(getUser()))) : ""%></td>
            </tr>
            <tr>
                <th align="left" width=200>Validate All Queries After Import</th>
                <td><input id="queryValidation" type="checkbox" name="queryValidation" <%=checked(queryValidation)%>></td>
            </tr>
            <tr>
                <td width=200>&nbsp;</td>
                <td><%=allowReload ? PageFlowUtil.button("Attempt Reload Now").href(buildURL(StudyController.CheckForReload.class, "ui=1")).attributes("id=\"reloadNow\"") :
                                     PageFlowUtil.button("Attempt Reload Now").href("javascript:void(0);").attributes("id=\"reloadNow\"") %></td>
            </tr>
            <tr>
                <td><%= button("Update").submit(true) %>&nbsp;<%= button("Cancel").href(StudyController.ManageStudyAction.class, getContainer()) %></td>
            </tr>
        </table>
    </labkey:form>
</labkey:panel>
<script type="text/javascript">
    function updateDisplay()
    {
        document.getElementById("interval").disabled = !document.getElementById("allowReload").checked;
        document.getElementById("queryValidation").disabled = !document.getElementById("allowReload").checked;
    }
</script>
<script type="text/javascript" for=window event=onload>
    updateDisplay();
    <%
        // If reload is not enabled, override the default class to disable the button
        if (!allowReload)
            out.println("    document.getElementById(\"reloadNow\").className = \"labkey-disabled-button\";");
    %>
</script>