<%
/*
 * Copyright (c) 2008-2019 LabKey Corporation
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
<%@ page import="org.labkey.api.admin.AdminUrls"%>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.security.SecurityManager" %>
<%@ page import="org.labkey.api.security.permissions.AdminOperationsPermission" %>
<%@ page import="org.labkey.api.security.permissions.ApplicationAdminPermission" %>
<%@ page import="org.labkey.api.settings.DateParsingMode" %>
<%@ page import="org.labkey.api.settings.LookAndFeelProperties" %>
<%@ page import="org.labkey.api.util.DateUtil" %>
<%@ page import="org.labkey.api.util.FolderDisplayMode" %>
<%@ page import="org.labkey.api.util.Formats" %>
<%@ page import="org.labkey.api.util.HtmlString" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.module.ModuleLoader" %>
<%@ page import="org.labkey.core.admin.AdminController" %>
<%@ page import="org.labkey.core.admin.AdminController.AdminUrlsImpl" %>
<%@ page import="java.util.Arrays" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    AdminController.LookAndFeelBean bean = ((JspView<AdminController.LookAndFeelBean>)HttpView.currentView()).getModelBean();
    Container c = getContainer();
    boolean folder = !c.isRoot() && !c.isProject();
    boolean hasAdminOpsPerm = c.hasPermission(getUser(), AdminOperationsPermission.class);
    HtmlString clearMessage = HtmlString.unsafe(folder ? "the default format properties" : "all look & feel properties");
    LookAndFeelProperties laf = LookAndFeelProperties.getInstance(c);
    String themeName = laf.getThemeName();
    String siteThemeName = themeName;
    if (!c.isRoot())
        siteThemeName = LookAndFeelProperties.getInstance(ContainerManager.getRoot()).getThemeName();
    boolean themeNameInherited = !c.isRoot() && laf.isThemeNameInherited();
    boolean canUpdate = !c.isRoot() || c.hasPermission(getUser(), ApplicationAdminPermission.class);
    boolean hasPremiumModule = ModuleLoader.getInstance().hasModule("Premium");
%>
<%=formatMissedErrors("form")%>
<labkey:form name="preferences" method="post" id="form-preferences">
<table class="lk-fields-table">
<%=getTroubleshooterWarning(canUpdate, HtmlString.unsafe("<tr><td colspan=2>"), HtmlString.unsafe("</td></tr>"))%>
<tr>
    <td colspan=2>&nbsp;</td>
</tr>
<% if (c.isProject()) {%>
<tr>
    <td colspan=2>Security defaults</td>
</tr>
<tr>
    <td class="labkey-form-label">New folders should inherit permissions by default</td>
    <td><input type="checkbox" name="shouldInherit" size="50"<%=checked(SecurityManager.shouldNewSubfoldersInheritPermissions(c))%>></td>
</tr>
<tr>
    <td colspan=2>&nbsp;</td>
</tr>
<%
   }

   // If this is a folder then skip everything except default date & number formats
   if (!folder)
   {
%>
<tr>
    <td colspan=2>Customize the look and feel of <%=h(c.isRoot() ? "your LabKey Server installation" : "the '" + c.getProject().getName() + "' project")%> (<%=bean.helpLink%>)</td>
</tr>
<tr>
    <td class="labkey-form-label">System description (used in emails)</td>
    <td><input type="text" name="systemDescription" size="50" value="<%= h(laf.getDescription()) %>"></td>
</tr>
<tr>
    <td class="labkey-form-label">Header short name (appears in every page header and in emails)</td>
    <td><input type="text" name="systemShortName" size="50" value="<%= h(laf.getShortName()) %>"></td>
</tr>
<tr>
    <td class="labkey-form-label">Theme</td>
    <td>
        <select name="themeName">
        <%
            if (!c.isRoot())
            {
                %><option value="" <%=selected(themeNameInherited)%>>Site Default (<%=h(siteThemeName)%>)</option><%
            }
            for (String name : Arrays.asList("Harvest","Leaf","Madison","Mono","Ocean","Overcast","Seattle","Sky"))
            {
                %><option value="<%=h(name)%>" <%=selected(!themeNameInherited && name.equalsIgnoreCase(themeName))%>><%=h(name)%></option><%
            }
        %>
        </select>
    </td>
</tr>
<tr>
    <td class="labkey-form-label">Show Project and Folder Navigation</td>
    <td><%
            FolderDisplayMode currentMode = laf.getFolderDisplayMode();
        %>
        <label><input type="radio" name="folderDisplayMode" value="<%=h(FolderDisplayMode.ALWAYS.toString())%>"<%=checked(currentMode == FolderDisplayMode.ALWAYS)%>> <%=h(FolderDisplayMode.ALWAYS.getDisplayString())%></label><br>
        <label><input type="radio" name="folderDisplayMode" value="<%=h(FolderDisplayMode.ADMIN.toString())%>"<%=checked(currentMode == FolderDisplayMode.ADMIN)%>> <%=h(FolderDisplayMode.ADMIN.getDisplayString())%></label><br>
    </td>
</tr>
    <% if (hasPremiumModule)
    {
    %>
<tr>
    <%
        FolderDisplayMode currentMenuDisplayMode = laf.getApplicationMenuDisplayMode();
    %>
    <td class="labkey-form-label">
        Show Application Selection Menu

    </td>
    <td>
        <label><input onclick="document.getElementById('app-menu-warning').style.display='none'" type="radio" name="applicationMenuDisplayMode" value="<%=h(FolderDisplayMode.ALWAYS.toString())%>"<%=checked(currentMenuDisplayMode == FolderDisplayMode.ALWAYS)%>> <%=h(FolderDisplayMode.ALWAYS.getDisplayString())%></label><br>
        <label><input onclick="document.getElementById('app-menu-warning').style.display='block'" type="radio" name="applicationMenuDisplayMode" value="<%=h(FolderDisplayMode.ADMIN.toString())%>"<%=checked(currentMenuDisplayMode == FolderDisplayMode.ADMIN)%>>
            <%=h(FolderDisplayMode.ADMIN.getDisplayString())%> <div id="app-menu-warning" class="labkey-error" style=<%=currentMenuDisplayMode == FolderDisplayMode.ADMIN ? q("display:block;"): q("display:none;")%>>Users will not be able to navigate between applications and LabKey Server when this menu is hidden.</div>
        </label><br>
    </td>
</tr>
    <%
    }
    %>
<tr>
    <td class="labkey-form-label">Show LabKey Help menu item</td>
    <td><input type="checkbox" name="enableHelpMenu" size="50"<%=checked(laf.isHelpMenuEnabled())%>></td>
</tr>
<%
    String enableDiscussionHelp = "Some items within LabKey Server, like reports and wiki pages, support discussions " +
            "that are scoped directly to that report or wiki page. Administrators can disable this feature.";
%>
<tr>
    <td class="labkey-form-label">Enable Object-Level Discussions<%=helpPopup("Enable Discussion", enableDiscussionHelp, true)%></td>
    <td><input type="checkbox" name="enableDiscussion" size="50"<%=checked(laf.isDiscussionEnabled())%>></td>
</tr>
<tr>
    <td class="labkey-form-label">Logo link (specifies page that header logo links to)</td>
    <td><input type="text" name="logoHref" size="50" value="<%=h(laf.getUnsubstitutedLogoHref())%>"></td>
</tr>
<tr>
    <td class="labkey-form-label">Support link (specifies page where users can request support)</td>
    <td><input type="text" name="reportAProblemPath" size="50" value="<%=h(laf.getUnsubstitutedReportAProblemPath())%>"></td>
</tr>
<tr>
    <td class="labkey-form-label">Support email (shown to users if they don't have permission<br/>to see a page, or are having trouble logging in)</td>
    <td style="vertical-align: top;"><input type="text" name="supportEmail" size="50" value="<%=h(laf.getSupportEmail())%>"></td>
</tr>
<tr>
    <td colspan=2>Customize settings used in system emails (<%=bean.helpLink%>)</td>
</tr>
<tr>
    <td class="labkey-form-label">
        System email address (<i>from</i> address for system notification emails)<%=helpPopup("System email address", "Requires AdminOperationsPermission to update.", false)%>
    </td>
    <td><input type="text" name="systemEmailAddress" size="50" value="<%= h(laf.getSystemEmailAddress()) %>" <%=h(!hasAdminOpsPerm ? "disabled" : "")%>></td>
</tr>
<tr>
    <td class="labkey-form-label">Organization name (appears in notification emails sent by system)</td>
    <td><input type="text" name="companyName" size="50" value="<%= h(laf.getCompanyName()) %>"></td>
</tr>
<tr>
    <td>&nbsp;</td>
</tr>
<%
    }  // End of project/site only settings

    String decimalFormatHelp = "The pattern string for numbers must be compatible with the format that the java class " +
            "<code>DecimalFormat</code> understands. A valid <code>DecimalFormat</code> is a pattern " +
            "specifying a prefix, numeric part, and suffix. For more information see the " +
            "<a href=\"" + Formats.getDecimalFormatDocumentationURL() + "\" target=\"blank\">java&nbsp;documentation</a>. " +
            "The following table has an abbreviated guide to pattern symbols:<br/>" +
            "<table class=\"labkey-data-region-legacy labkey-show-borders\">" +
            "<colgroup><col><col><col><col></colgroup>" +
            "<tr class=\"labkey-frame\"><th align=left>Symbol<th align=left>Location<th align=left>Localized?<th align=left style=\"width:200px;\">Meaning</tr>" +
            "<tr valign=top class=\"labkey-row\"><td><code>0</code><td>Number<td>Yes<td>Digit</tr>" +
            "<tr valign=top class=\"labkey-alternate-row\"><td><code>#</code><td>Number<td>Yes<td>Digit, zero shows as absent</tr>" +
            "<tr valign=top class=\"labkey-row\"><td><code>.</code><td>Number<td>Yes<td>Decimal separator or monetary decimal separator</tr>" +
            "<tr valign=top class=\"labkey-alternate-row\"><td><code>-</code><td>Number<td>Yes<td>Minus sign</tr>" +
            "<tr valign=top class=\"labkey-row\"><td><code>,</code><td>Number<td>Yes<td>Grouping separator</tr>" +
            "</table>";

    String simpleDateFormatDocs = "<br><br>The pattern string must be compatible with the format that the java class " +
            "<code>SimpleDateFormat</code> understands. For more information see the " +
            "<a href=\"" + DateUtil.getSimpleDateFormatDocumentationURL() + "\" target=\"blank\">java&nbsp;documentation</a>. " +
            "The following table has a partial guide to pattern symbols:<br/>" +
            "<table class=\"labkey-data-region-legacy labkey-show-borders\">" +
            "<colgroup><col><col style=\"width: 100%;\"><col></colgroup>" +
            "<tr class=\"labkey-frame\"><th align=left>Letter<th align=left>Date or Time Component<th align=left>Examples</tr>" +
            "<tr class=\"labkey-row\"><td><code>G</code><td>Era designator<td><code>AD</code></tr>" +
            "<tr class=\"labkey-alternate-row\"><td><code>y</code><td>Year<td><code>1996</code>; <code>96</code></tr>" +
            "<tr class=\"labkey-row\"><td><code>M</code><td>Month in year<td><code>July</code>; <code>Jul</code>; <code>07</code></tr>" +
            "<tr class=\"labkey-alternate-row\"><td><code>w</code><td>Week in year<td><code>27</code></td></tr>" +
            "<tr class=\"labkey-row\"><td><code>W</code><td>Week in month<td><code>2</code></td></tr>" +
            "<tr class=\"labkey-alternate-row\"><td><code>D</code><td>Day in year<td><code>189</code></td></tr>" +
            "<tr class=\"labkey-row\"><td><code>d</code><td>Day in month<td><code>10</code></tr>" +
            "<tr class=\"labkey-alternate-row\"><td><code>F</code><td>Day of week in month<td><code>2</code></tr>" +
            "<tr class=\"labkey-row\"><td><code>E</code><td>Day in week<td><code>Tuesday</code>; <code>Tue</code></tr>" +
            "<tr class=\"labkey-alternate-row\"><td><code>a</code><td>Am/pm marker<td><code>PM</code></tr>" +
            "<tr class=\"labkey-row\"><td><code>H</code><td>Hour in day (0-23)<td><code>0</code></tr>" +
            "<tr class=\"labkey-alternate-row\"><td><code>k</code><td>Hour in day (1-24)<td><code>24</code></tr>" +
            "<tr class=\"labkey-row\"><td><code>K</code><td>Hour in am/pm (0-11)<td><code>0</code></tr>" +
            "<tr class=\"labkey-alternate-row\"><td><code>h</code><td>Hour in am/pm (1-12)<td><code>12</code></tr>" +
            "<tr class=\"labkey-row\"><td><code>m</code><td>Minute in hour<td><code>30</code></tr>" +
            "<tr class=\"labkey-alternate-row\"><td><code>s</code><td>Second in minute<td><code>55</code></tr>" +
            "<tr class=\"labkey-row\"><td><code>S</code><td>Millisecond<td><code>978</code></tr>" +
            "</table>";
    String dateFormatHelp = "This format is applied when displaying a column that is defined with a date-only data type or annotated with the \"Date\" meta type. Most standard LabKey date columns use date-time data type (see below)." + simpleDateFormatDocs;
    String dateTimeFormatHelp = "This format is applied when displaying a column that is defined with a date-time data type or annotated with the \"DateTime\" meta type. Most standard LabKey date columns use this format." + simpleDateFormatDocs;

    String dateParsingHelp = "This pattern is attempted first when parsing text input for a column that is designated with a date-only data type or annotated with the \"Date\" meta type. Most standard LabKey date columns use date-time data type instead (see below)." + simpleDateFormatDocs;
    String dateTimeParsingHelp = "This pattern is attempted first when parsing text input for a column that is designated with a date-time data type or annotated with the \"DateTime\" meta type. Most standard LabKey date columns use this pattern." + simpleDateFormatDocs;
%>
<tr>
    <td colspan=2>Customize date and number display formats (<%=bean.helpLink%>)</td>
</tr>
<tr>
    <td class="labkey-form-label">Default display format for dates<%=helpPopup("Date format", dateFormatHelp, true, 300)%></td>
    <td><input type="text" name="defaultDateFormat" size="50" value="<%= h(laf.getDefaultDateFormat()) %>"></td>
</tr>
<tr>
    <td class="labkey-form-label">Default display format for date-times<%=helpPopup("Date-time format", dateTimeFormatHelp, true, 300)%></td>
    <td><input type="text" name="defaultDateTimeFormat" size="50" value="<%= h(laf.getDefaultDateTimeFormat()) %>"></td>
</tr>
<tr>
    <td class="labkey-form-label">Default display format for numbers<%=helpPopup("Number format", decimalFormatHelp, true, 350)%></td>
    <td><input type="text" name="defaultNumberFormat" size="50" value="<%= h(laf.getDefaultNumberFormat()) %>"></td>
</tr>
<tr>
    <td>&nbsp;</td>
</tr>

<tr>
    <td colspan=2>Customize date parsing behavior (<%=bean.helpLink%>)</td>
</tr>
<%
    // TODO: This check is temporary and should switch to "if (!folder) {}" once the date parsing methods pass Container consistently
    if (c.isRoot())
    {
        DateParsingMode dateParsingMode = laf.getDateParsingMode();
        String dateParsingModeHelp = "LabKey needs to understand how to interpret (parse) dates that users enter into input forms. " +
                "For example, if a user enters the date \"10/4/2013\" does that person mean October 4, 2013 (typical interpretation " +
                "in the United States) or April 10, 2013 (typical interpretation in most other countries)? Choose the " +
                "parsing mode that matches your users' expectations.";
%>
<tr>
    <td class="labkey-form-label">Date parsing mode<%=helpPopup("Date parsing mode", dateParsingModeHelp, false)%></td>
    <td>
        <label><input type="radio" name="dateParsingMode" value="<%=h(DateParsingMode.US.toString())%>"<%=checked(dateParsingMode == DateParsingMode.US)%>> <%=h(DateParsingMode.US.getDisplayString())%> </label><br>
        <label><input type="radio" name="dateParsingMode" value="<%=h(DateParsingMode.NON_US.toString())%>"<%=checked(dateParsingMode == DateParsingMode.NON_US)%>> <%=h(DateParsingMode.NON_US.getDisplayString())%> </label><br>
    </td>
</tr><%
    }
%>
<tr>
    <td class="labkey-form-label">Additional parsing pattern for dates<%=helpPopup("Extra date parsing pattern", dateParsingHelp, true, 300)%></td>
    <td><input type="text" name="extraDateParsingPattern" size="50" value="<%= h(laf.getExtraDateParsingPattern()) %>"></td>
</tr>
<tr>
    <td class="labkey-form-label">Additional parsing pattern for date-times<%=helpPopup("Extra date-time parsing pattern", dateTimeParsingHelp, true, 300)%></td>
    <td><input type="text" name="extraDateTimeParsingPattern" size="50" value="<%= h(laf.getExtraDateTimeParsingPattern()) %>"></td>
</tr>
<tr>
    <td>&nbsp;</td>
</tr>

<tr>
    <td colspan=2>Customize column restrictions (<%=bean.customColumnRestrictionHelpLink%>)</td>
</tr>
<tr>
    <td class="labkey-form-label">Restrict charting columns by measure and dimension flags</td>
    <td><input type="checkbox" name="restrictedColumnsEnabled" size="50"<%=checked(laf.areRestrictedColumnsEnabled())%>></td>
</tr>
<tr>
    <td>&nbsp;</td>
</tr>
<%
    if (!folder) {
        String customLoginHelp = "The custom login page is specified as a string composed of the module name and a page name in" +
            " the format: [module]-[name]. For example the string 'myModule-customLogin' can be entered to enable a custom login provided as" +
            " an HTML page called customLogin.html located in the /resources/views directory of myModule." +
            "<br/><br/>Requires AdminOperationsPermission to update.";
%>
<tr>
    <td colspan=2>Provide a custom login page (<%=bean.helpLink%>)</td>
</tr>
<tr>
    <td class="labkey-form-label">Alternative login page<%=helpPopup("Custom Login Page", customLoginHelp, true)%></td>
    <td><input type="text" name="customLogin" size="50" value="<%= h(laf.getCustomLogin()) %>" <%=h(!hasAdminOpsPerm ? "disabled" : "")%>></td>
</tr>
<tr>
    <td>&nbsp;</td>
</tr>
<%
    }

    if (c.isRoot())
    {
        String customWelcomeHelp = "The relative URL of the page, either a full LabKey view or simple HTML resource, to be loaded as the welcome page." +
            " The welcome page will be loaded when a user loads the site with no action provided (i.e. https://www.labkey.org)." +
            " This is often used to provide a splash screen for guests. Note: do not include the contextPath in this string." +
            " For example: /myModule/welcome.view to select a view within a module, or /myModule/welcome.html for a simple HTML page in the web directory of your module.";
%>
<tr>
    <td colspan=2>Provide a custom site welcome page (<%=bean.welcomeLink%>)</td>
</tr>
<tr>
    <td class="labkey-form-label">Alternative site welcome page<%=helpPopup("Custom Welcome Page", customWelcomeHelp, false)%></td>
    <td><input type="text" name="customWelcome" size="50" value="<%= h(laf.getCustomWelcome()) %>"></td>
</tr>
<tr>
    <td>&nbsp;</td>
</tr>
<%
    }

    if (canUpdate)
    {
%>
<tr>
    <td><%=button("Save").submit(true).onClick("_form.setClean();") %>&nbsp;
        <%=button("Reset").onClick("return confirmReset();") %>
    </td>
</tr>
<%
    }
    else
    {
%>
<tr>
    <td><%=button("Done").href(urlProvider(AdminUrls.class).getAdminConsoleURL())%></td>
</tr>
<%
    }
%>
<tr>
    <td>&nbsp;</td>
</tr>
</table>
</labkey:form>
<script type="text/javascript">
    var _form = new LABKEY.Form({ formElement: 'form-preferences'});

    function confirmReset()
    {
        if (confirm('Are you sure you want to clear <%=clearMessage%>?'))
        {
            _form.setClean();
            LABKEY.Utils.postToAction(<%=q(new AdminUrlsImpl().getResetLookAndFeelPropertiesURL(c))%>);
            return true;
        }
        else
            return false;
    }
</script>
