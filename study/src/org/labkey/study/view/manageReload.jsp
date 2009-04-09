<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%
    StudyController.ReloadForm form = (StudyController.ReloadForm)getModelBean();

    boolean reload = StudyController.isSetToReload(this.getViewContext().getContainer());

%>
<form action="" method="post">
    <table>
        <tr>
            <td colspan=2>A study can be configured to reload study data from the file system, either manually or automatically at pre-set intervals.</td>
        </tr>
        <tr>
            <td colspan=2>&nbsp;</td>
        </tr>
        <tr>
            <th align="left" width=200>Allow Study Reload</th>
            <td><input name="allowReload" type="checkbox"<%=reload ? " checked" : ""%>></td>
        </tr>
        <tr>
            <th align="left" width=200>Reload Interval</th>
            <td align="left"><select name="interval">
                <option value="1">Manual</option>
                <option value="1">24 hours</option>
                <option value="2">1 hour</option>
                <option value="3">5 minutes</option>
                <option value="4"<%=reload ? " selected" : ""%>>10 seconds</option>
            </select></td>
        </tr>
        <tr>
            <td width=200>&nbsp;</td>
            <td><%= generateSubmitButton("Update")%>&nbsp;<%= generateButton("Cancel", "manageStudy.view")%></td>
        </tr>
    </table>
</form>