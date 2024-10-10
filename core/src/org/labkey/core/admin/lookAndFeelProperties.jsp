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
<%@ page import="org.labkey.api.admin.AdminBean" %>
<%@ page import="org.labkey.api.admin.AdminUrls" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.jsp.JspBase" %>
<%@ page import="org.labkey.api.module.ModuleLoader" %>
<%@ page import="org.labkey.api.security.SecurityManager" %>
<%@ page import="org.labkey.api.security.permissions.AdminOperationsPermission" %>
<%@ page import="org.labkey.api.security.permissions.ApplicationAdminPermission" %>
<%@ page import="org.labkey.api.settings.DateParsingMode" %>
<%@ page import="org.labkey.api.settings.LookAndFeelProperties" %>
<%@ page import="org.labkey.api.settings.Theme" %>
<%@ page import="org.labkey.api.util.DateUtil" %>
<%@ page import="org.labkey.api.util.FolderDisplayMode" %>
<%@ page import="org.labkey.api.util.Formats" %>
<%@ page import="org.labkey.api.util.HtmlString" %>
<%@ page import="org.labkey.api.util.HtmlStringBuilder" %>
<%@ page import="org.labkey.api.util.PageFlowUtil.HelpPopupBuilder" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.core.admin.AdminController" %>
<%@ page import="org.labkey.core.admin.AdminController.AdminUrlsImpl" %>
<%@ page import="org.labkey.core.admin.DateDisplayFormatType" %>
<%@ page import="java.io.IOException" %>
<%@ page import="java.text.SimpleDateFormat" %>
<%@ page import="java.util.Arrays" %>
<%@ page import="java.util.Date" %>
<%@ page import="java.util.LinkedHashMap" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.Set" %>
<%@ page import="java.util.stream.Collectors" %>
<%@ page import="static org.labkey.api.settings.LookAndFeelProperties.Properties.*" %>
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
    HelpPopupBuilder inheritHelp = null;
    if (!c.isRoot())
    {
        siteThemeName = LookAndFeelProperties.getInstance(ContainerManager.getRoot()).getThemeName();
        Container parent = c.getParent();
        String parentName = parent.isRoot() ? "the site root" : (parent.isProject() ? "project" : "folder") + " " + parent.getPath();
        String helpText = "Settings where the \"Inherited\" box is checked inherit their values from " + parentName +
            ". Settings where \"Inherited\" is unchecked override their values in this " + (c.isProject() ? "project": "folder") + ".";
        inheritHelp = helpPopup("Inherited", helpText, false);
    }
    boolean themeNameInherited = !c.isRoot() && laf.isThemeNameInherited();
    boolean canUpdate = !c.isRoot() || c.hasPermission(getUser(), ApplicationAdminPermission.class);
    boolean hasPremiumModule = ModuleLoader.getInstance().hasModule("Premium");
%>
<%=formatMissedErrors("form")%>
<div id="dateFormatWarning" style="display: none;" class="alert alert-warning alert-dismissable">
    <div class="lk-dismissable-warn">Warning: One or more date, time, or date-time display formats are using non-standard patterns. <%=helpLink("studyDateNumber", "Click here")%> to learn more.</div>
</div>
<labkey:form name="preferences" method="post" id="form-preferences">
<table class="lk-fields-table">
<%=getTroubleshooterWarning(canUpdate, HtmlString.unsafe("<tr><td colspan=2>"), HtmlString.unsafe("</td></tr>"))%>
<tr>
    <td colspan=2>&nbsp;</td>
</tr>
<%
    if (c.isProject())
    {
%>
<tr>
    <td>Security defaults</td>
    <td style="padding-left: 5px; padding-right: 5px;">Inherited<%=inheritHelp%></td>
</tr>
<tr>
    <td class="labkey-form-label"><label for="shouldInherit">New folders should inherit permissions by default</label></td>
    <td><input type="checkbox" id="shouldInherit" name="shouldInherit" size="50"<%=checked(SecurityManager.shouldNewSubfoldersInheritPermissions(c))%>></td>
</tr>
<tr>
    <td colspan=2>&nbsp;</td>
</tr>
<%
    }

    // If this is a folder then skip everything except default date & number formats
    if (!folder)
    {
        HtmlString shortNameHelp = HtmlStringBuilder.of("The header short name supports string substitution of server properties. " +
                "Note that the substituted values will be visible to all users including guests (e.g., on the login page).")
            .unsafeAppend("<br><br>")
            .append("As an example, a header short name set to:")
            .unsafeAppend("<br><br><code>&nbsp;&nbsp;LabKey ${releaseVersion} built ${buildTime}</code><br><br>")
            .append("will currently result in this header text:")
            .unsafeAppend("<br><br><code>&nbsp;&nbsp;")
            .append("LabKey " + AdminBean.releaseVersion + " built " + AdminBean.buildTime)
            .unsafeAppend("</code><br><br>")
            .append("The supported properties and their current values are listed in the table below.")
            .unsafeAppend("<br><br>")
            .append(AdminBean.getPropertyGridHtml(AdminBean.getPropertyMap()))
            .getHtmlString();
%>
<tr>
    <td colspan=2>Customize the look and feel of <%=h(c.isRoot() ? "your LabKey Server installation" : "the '" + c.getProject().getName() + "' project")%> (<%=bean.helpLink%>)</td>
</tr>
<tr>
    <td class="labkey-form-label"><label for="<%=systemDescription%>">System description (used in emails)</label></td>
    <td><input type="text" id="<%=systemDescription%>" name="<%=systemDescription%>" size="50" value="<%= h(laf.getDescription()) %>"></td>
</tr>
<tr>
    <td class="labkey-form-label"><label for="<%=systemShortName%>">Header short name (appears in every page header and in emails)</label><%=helpPopup("Header short name", shortNameHelp, 350)%></td>
    <td><input type="text" id="<%=systemShortName%>" name="<%=systemShortName%>" size="50" value="<%= h(laf.getUnsubstitutedShortName()) %>"></td>
</tr>
<tr>
    <td class="labkey-form-label"><label for="themeName">Theme</label></td>
    <td>
        <select id="themeName" name="themeName">
        <%
            if (!c.isRoot())
            {
                %><option value="" <%=selected(themeNameInherited)%>>Site Default (<%=h(siteThemeName)%>)</option><%
            }
            for (Theme theme : Theme.values())
            {
                String name = theme.name();
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
        <label><input type="radio" name="<%=folderDisplayMode%>" value="<%=FolderDisplayMode.ALWAYS%>"<%=checked(currentMode == FolderDisplayMode.ALWAYS)%>> <%=h(FolderDisplayMode.ALWAYS.getDisplayString())%></label><br>
        <label><input type="radio" name="<%=folderDisplayMode%>" value="<%=FolderDisplayMode.ADMIN%>"<%=checked(currentMode == FolderDisplayMode.ADMIN)%>> <%=h(FolderDisplayMode.ADMIN.getDisplayString())%></label><br>
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
        <label><input id="menu_always" type="radio" name="<%=applicationMenuDisplayMode%>" value="<%=FolderDisplayMode.ALWAYS%>"<%=checked(currentMenuDisplayMode == FolderDisplayMode.ALWAYS)%>> <%=h(FolderDisplayMode.ALWAYS.getDisplayString())%></label><br>
        <label><input id="menu_admin"  type="radio" name="<%=applicationMenuDisplayMode%>" value="<%=FolderDisplayMode.ADMIN%>"<%=checked(currentMenuDisplayMode == FolderDisplayMode.ADMIN)%>>
            <%=h(FolderDisplayMode.ADMIN.getDisplayString())%> <div id="app-menu-warning" class="labkey-error" style=<%=currentMenuDisplayMode == FolderDisplayMode.ADMIN ? q("display:block;"): q("display:none;")%>>Users will not be able to navigate between applications and LabKey Server when this menu is hidden.</div>
        </label><br>
<%
            addHandler("menu_always", "click", "document.getElementById('app-menu-warning').style.display='none';");
            addHandler("menu_admin", "click", "document.getElementById('app-menu-warning').style.display='block';");
%>
    </td>
</tr>
<%
    }
%>
<tr>
    <td class="labkey-form-label"><label for="<%=helpMenuEnabled%>">Show LabKey Help menu item</label></td>
    <td><input type="checkbox" id="<%=helpMenuEnabled%>" name="<%=helpMenuEnabled%>" size="50"<%=checked(laf.isHelpMenuEnabled())%>></td>
</tr>
<%
    String enableDiscussionHelp = "Some items within LabKey Server, like reports and wiki pages, support discussions " +
            "that are scoped directly to that report or wiki page. Administrators can disable this feature.";
%>
<tr>
    <td class="labkey-form-label"><label for="<%=discussionEnabled%>">Enable Object-Level Discussions</label><%=helpPopup("Enable Discussion", enableDiscussionHelp, true)%></td>
    <td><input type="checkbox" id="<%=discussionEnabled%>" name="<%=discussionEnabled%>" size="50"<%=checked(laf.isDiscussionEnabled())%>></td>
</tr>
<tr>
    <td class="labkey-form-label"><label for="<%=logoHref%>">Logo link (specifies page that header logo links to)</label></td>
    <td><input type="text" id="<%=logoHref%>" name="<%=logoHref%>" size="50" value="<%=h(laf.getUnsubstitutedLogoHref())%>"></td>
</tr>
<tr>
    <td class="labkey-form-label"><label for="<%=reportAProblemPath%>">Support link (specifies page where users can request support)</label></td>
    <td><input type="text" id="<%=reportAProblemPath%>" name="<%=reportAProblemPath%>" size="50" value="<%=h(laf.getUnsubstitutedReportAProblemPath())%>"></td>
</tr>
<tr>
    <td class="labkey-form-label"><label for="<%=supportEmail%>">Support email (shown to users if they don't have permission<br/>to see a page, or are having trouble logging in)</label></td>
    <td style="vertical-align: top;"><input type="text" id="<%=supportEmail%>" name="<%=supportEmail%>" size="50" value="<%=h(laf.getSupportEmail())%>"></td>
</tr>
<tr>
    <td>&nbsp;</td>
</tr>
<tr>
    <td colspan=2>Customize settings used in system emails (<%=bean.helpLink%>)</td>
</tr>
<tr>
    <td class="labkey-form-label">
        <label for="<%=systemEmailAddress%>">System email address (<i>from</i> address for system notification emails)</label><%=helpPopup("System email address", "Requires AdminOperationsPermission to update.", false)%>
    </td>
    <td><input type="text" id="<%=systemEmailAddress%>" name="<%=systemEmailAddress%>" size="50" value="<%= h(laf.getSystemEmailAddress()) %>"<%=disabled(!hasAdminOpsPerm)%>></td>
</tr>
<tr>
    <td class="labkey-form-label"><label for="<%=companyName%>">Organization name (appears in notification emails sent by system)</label></td>
    <td><input type="text" id="<%=companyName%>" name="<%=companyName%>" size="50" value="<%= h(laf.getCompanyName()) %>"></td>
</tr>
<tr>
    <td>&nbsp;</td>
</tr>
<%
    }  // End of project/site only settings

    String sizingPrefix = "<div style=\"width: 500px;\">";
    String sizingSuffix = "</div>";

    String decimalFormatHelp = sizingPrefix + "The pattern string for numbers must be compatible with the format that the Java class " +
            "<code>DecimalFormat</code> understands. A valid <code>DecimalFormat</code> is a pattern " +
            "specifying a prefix, numeric part, and suffix. For more information see the " +
            "<a href=\"" + Formats.getDecimalFormatDocumentationURL() + "\" target=\"blank\">documentation</a>. " +
            "The following table has an abbreviated guide to pattern symbols:<br/>" +
            "<table class=\"labkey-data-region-legacy labkey-show-borders\">" +
            "<tr class=\"labkey-frame\"><th align=left>Symbol<th align=left>Location<th align=left>Localized?<th align=left style=\"width:200px;\">Meaning</tr>" +
            "<tr valign=top class=\"labkey-row\"><td><code>0</code><td>Number<td>Yes<td>Digit</tr>" +
            "<tr valign=top class=\"labkey-alternate-row\"><td><code>#</code><td>Number<td>Yes<td>Digit, zero shows as absent</tr>" +
            "<tr valign=top class=\"labkey-row\"><td><code>.</code><td>Number<td>Yes<td>Decimal separator or monetary decimal separator</tr>" +
            "<tr valign=top class=\"labkey-alternate-row\"><td><code>-</code><td>Number<td>Yes<td>Minus sign</tr>" +
            "<tr valign=top class=\"labkey-row\"><td><code>,</code><td>Number<td>Yes<td>Grouping separator</tr>" +
            "</table>" + sizingSuffix;

    String simpleDateDocHeader = "<br><br>The pattern string must be compatible with the format that the Java class " +
            "<code>SimpleDateFormat</code> understands. For more information see the " +
            "<a href=\"" + DateUtil.getSimpleDateFormatDocumentationURL() + "\" target=\"blank\">documentation</a>. " +
            "The following table has a partial guide to pattern symbols:<br/>" +
            "<table class=\"labkey-data-region-legacy labkey-show-borders\">" +
            "<tr class=\"labkey-frame\"><th align=left>Letter<th align=left>Date or Time Component<th align=left>Examples</tr>";

    String dateDocs =
            "<tr class=\"labkey-row\"><td><code>G</code><td>Era designator<td><code>AD</code></tr>" +
            "<tr class=\"labkey-alternate-row\"><td><code>y</code><td>Year<td><code>1996</code>; <code>96</code></tr>" +
            "<tr class=\"labkey-row\"><td><code>M</code><td>Month in year<td><code>July</code>; <code>Jul</code>; <code>07</code></tr>" +
            "<tr class=\"labkey-alternate-row\"><td><code>w</code><td>Week in year<td><code>27</code></td></tr>" +
            "<tr class=\"labkey-row\"><td><code>W</code><td>Week in month<td><code>2</code></td></tr>" +
            "<tr class=\"labkey-alternate-row\"><td><code>D</code><td>Day in year<td><code>189</code></td></tr>" +
            "<tr class=\"labkey-row\"><td><code>d</code><td>Day in month<td><code>10</code></tr>" +
            "<tr class=\"labkey-alternate-row\"><td><code>F</code><td>Day of week in month<td><code>2</code></tr>" +
            "<tr class=\"labkey-row\"><td><code>E</code><td>Day in week<td><code>Tuesday</code>; <code>Tue</code></tr>";

    String timeDocs = "<tr class=\"labkey-alternate-row\"><td><code>a</code><td>Am/pm marker<td><code>PM</code></tr>" +
            "<tr class=\"labkey-row\"><td><code>H</code><td>Hour in day (0-23)<td><code>0</code></tr>" +
            "<tr class=\"labkey-alternate-row\"><td><code>k</code><td>Hour in day (1-24)<td><code>24</code></tr>" +
            "<tr class=\"labkey-row\"><td><code>K</code><td>Hour in am/pm (0-11)<td><code>0</code></tr>" +
            "<tr class=\"labkey-alternate-row\"><td><code>h</code><td>Hour in am/pm (1-12)<td><code>12</code></tr>" +
            "<tr class=\"labkey-row\"><td><code>m</code><td>Minute in hour<td><code>30</code></tr>" +
            "<tr class=\"labkey-alternate-row\"><td><code>s</code><td>Second in minute<td><code>55</code></tr>" +
            "<tr class=\"labkey-row\"><td><code>S</code><td>Millisecond<td><code>978</code></tr>";

    String simpleDateFormatDocs = simpleDateDocHeader + dateDocs + "</table>";
    String simpleDateTimeFormatDocs = simpleDateDocHeader + dateDocs + timeDocs + "</table>";
    String simpleTimeFormatDocs = simpleDateDocHeader + timeDocs + "</table>";
    String dateFormatHelp = sizingPrefix + "This format is applied when displaying a column that is defined with a date-only data type or annotated with the \"Date\" meta type. Most standard LabKey date columns use date-time data type (see below)." + sizingSuffix;
    String dateTimeFormatHelp = sizingPrefix + "This format is applied when displaying a column that is defined with a date-time data type or annotated with the \"DateTime\" meta type. Most standard LabKey date columns use this format." + sizingSuffix;
    String timeFormatHelp = sizingPrefix + "This format is applied when displaying a column that is defined with a time data type or annotated with the \"Time\" meta type. Most standard LabKey time columns use this format." + sizingSuffix;

    String dateParsingHelp = sizingPrefix + "This pattern is attempted first when parsing text input for a column that is designated with a date-only data type or annotated with the \"Date\" meta type. Most standard LabKey date columns use date-time data type instead (see below)." + simpleDateFormatDocs + sizingSuffix;
    String dateTimeParsingHelp = sizingPrefix + "This pattern is attempted first when parsing text input for a column that is designated with a date-time data type or annotated with the \"DateTime\" meta type. Most standard LabKey date columns use this pattern." + simpleDateTimeFormatDocs + sizingSuffix;
    String timeParsingHelp = sizingPrefix + "This pattern is attempted first when parsing text input for a column that is designated with a time data type or annotated with the \"Time\" meta type. Most standard LabKey time columns use this pattern." + simpleTimeFormatDocs + sizingSuffix;
%>
<tr>
    <td<%=h(!folder ? " colspan=3" : "")%>>Customize date, time, and number display formats (<%=bean.helpLink%>)</td>
    <%
        if (folder)
        {
    %>
    <td style="padding-left: 5px; padding-right: 5px;">Inherited<%=inheritHelp%></td>
    <%
        }
    %>
</tr>
<tr>
    <td class="labkey-form-label"><label for="<%=defaultDateFormat%>">Default display format for dates</label><%=helpPopup("Date format", dateFormatHelp, true)%></td>
    <% boolean inherited = null == laf.getDefaultDateFormatStored(); %>
    <%=inheritCheckbox(c, inherited, this, defaultDateFormat.name())%>
    <td><% select(out, DateDisplayFormatType.Date, defaultDateFormat.name(), DateUtil.STANDARD_DATE_DISPLAY_FORMATS, laf.getDefaultDateFormat(), false, inherited); %></td>
</tr>
<tr>
    <td class="labkey-form-label"><label for="<%=defaultDateTimeFormat%>">Default display format for date-times</label><%=helpPopup("Date-time format", dateTimeFormatHelp, true)%></td>
    <% inherited = null == laf.getDefaultDateTimeFormatStored(); %>
    <%=inheritCheckbox(c, inherited, defaultDateTimeFormat.name(), this, "dateSelect", "timeSelect")%>
<%
        String[] parts = DateUtil.splitDateTimeFormat(laf.getDefaultDateTimeFormat());
%>
    <td>
        <% select(out, DateDisplayFormatType.Date, "dateSelect", DateUtil.STANDARD_DATE_DISPLAY_FORMATS, parts.length > 0 ? parts[0] : null, false, inherited); %>&nbsp;&nbsp;
        <% select(out, DateDisplayFormatType.Time, "timeSelect", DateUtil.STANDARD_TIME_DISPLAY_FORMATS, parts.length > 1 ? parts[1] : NONE, true, inherited); %>
        <input type="hidden" name="<%=defaultDateTimeFormat%>" id="<%=defaultDateTimeFormat%>">
    </td>
</tr>
<tr>
    <td class="labkey-form-label"><label for="<%=defaultTimeFormat%>">Default display format for time-only values</label><%=helpPopup("Time format", timeFormatHelp, true)%></td>
    <% inherited = null == laf.getDefaultTimeFormatStored(); %>
    <%=inheritCheckbox(c, inherited, this, defaultTimeFormat.name())%>
    <td><% select(out, DateDisplayFormatType.Time, defaultTimeFormat.name(), DateUtil.STANDARD_TIME_DISPLAY_FORMATS, laf.getDefaultTimeFormat(), false, inherited); %></td>
</tr>
<tr>
    <td class="labkey-form-label"><label for="<%=defaultNumberFormat%>">Default display format for numbers</label><%=helpPopup("Number format", decimalFormatHelp, true)%></td>
    <% inherited = null == laf.getDefaultNumberFormatStored(); %>
    <%=inheritCheckbox(c, inherited, this, defaultNumberFormat.name())%>
    <td><input type="text" id="<%=defaultNumberFormat%>" name="<%=defaultNumberFormat%>" size="50" value="<%= h(laf.getDefaultNumberFormat()) %>"<%=disabled(inherited)%>></td>
</tr>
<tr>
    <td>&nbsp;</td>
</tr>

<tr>
    <td colspan=3>Customize date and time parsing behavior (<%=bean.helpLink%>)</td>
</tr>
<%
    // TODO: This check is temporary and should switch to "if (!folder) {}" once the date parsing methods pass Container consistently
    if (c.isRoot())
    {
        DateParsingMode mode = laf.getDateParsingMode();
        String dateParsingModeHelp = "LabKey needs to understand how to interpret (parse) dates that users enter into input forms. " +
                "For example, if a user enters the date \"10/4/2013\" does that person mean October 4, 2013 (typical interpretation " +
                "in the United States) or April 10, 2013 (typical interpretation in most other countries)? Choose the " +
                "parsing mode that matches your users' expectations.";
%>
<tr>
    <td class="labkey-form-label">Date parsing mode<%=helpPopup("Date parsing mode", dateParsingModeHelp, false)%></td>
    <td>
        <label><input type="radio" name="<%=dateParsingMode%>" value="<%=DateParsingMode.US%>"<%=checked(mode == DateParsingMode.US)%>> <%=h(DateParsingMode.US.getDisplayString())%> </label><br>
        <label><input type="radio" name="<%=dateParsingMode%>" value="<%=DateParsingMode.NON_US%>"<%=checked(mode == DateParsingMode.NON_US)%>> <%=h(DateParsingMode.NON_US.getDisplayString())%> </label><br>
    </td>
</tr>
<%
    }
%>
<tr>
    <td class="labkey-form-label"><label for="<%=extraDateParsingPattern%>">Additional parsing pattern for dates</label><%=helpPopup("Extra date parsing pattern", dateParsingHelp, true)%></td>
    <% inherited = null == laf.getExtraDateParsingPatternStored(); %>
    <%=inheritCheckbox(c, inherited, this, extraDateParsingPattern.name())%>
    <td><input type="text" id="<%=extraDateParsingPattern%>" name="<%=extraDateParsingPattern%>" size="50" value="<%= h(laf.getExtraDateParsingPattern()) %>"<%=disabled(inherited)%>></td>
</tr>
<tr>
    <td class="labkey-form-label"><label for="<%=extraDateTimeParsingPattern%>">Additional parsing pattern for date-times</label><%=helpPopup("Extra date-time parsing pattern", dateTimeParsingHelp, true, 300)%></td>
    <% inherited = null == laf.getExtraDateTimeParsingPatternStored(); %>
    <%=inheritCheckbox(c, inherited, this, extraDateTimeParsingPattern.name())%>
    <td><input type="text" id="<%=extraDateTimeParsingPattern%>"  name="<%=extraDateTimeParsingPattern%>" size="50" value="<%= h(laf.getExtraDateTimeParsingPattern()) %>"<%=disabled(inherited)%>></td>
</tr>
<tr>
    <td class="labkey-form-label"><label for="<%=extraTimeParsingPattern%>">Additional parsing pattern for times</label><%=helpPopup("Extra time parsing pattern", timeParsingHelp, true)%></td>
    <% inherited = null == laf.getExtraTimeParsingPatternStored(); %>
    <%=inheritCheckbox(c, inherited, this, extraTimeParsingPattern.name())%>
    <td><input type="text" id="<%=extraTimeParsingPattern%>" name="<%=extraTimeParsingPattern%>" size="50" value="<%= h(laf.getExtraTimeParsingPattern()) %>"<%=disabled(inherited)%>></td>
</tr>
<tr>
    <td>&nbsp;</td>
</tr>

<tr>
    <td colspan=3>Customize column restrictions (<%=bean.customColumnRestrictionHelpLink%>)</td>
</tr>
<tr>
    <td class="labkey-form-label"><label for="<%=restrictedColumnsEnabled%>">Restrict charting columns by measure and dimension flags</label></td>
    <% inherited = null == laf.areRestrictedColumnsEnabledStored(); %>
    <%=inheritCheckbox(c, inherited, this, restrictedColumnsEnabled.name())%>
    <td><input type="checkbox" id="<%=restrictedColumnsEnabled%>" name="<%=restrictedColumnsEnabled%>" size="50"<%=checked(laf.areRestrictedColumnsEnabled())%><%=disabled(inherited)%>></td>
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
    <td class="labkey-form-label"><label for="<%=customLogin%>">Alternative login page</label><%=helpPopup("Custom Login Page", customLoginHelp, true)%></td>
    <td><input type="text" id="<%=customLogin%>" name="<%=customLogin%>" size="50" value="<%= h(laf.getCustomLogin()) %>"<%=disabled(!hasAdminOpsPerm)%>></td>
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
    <td class="labkey-form-label"><label for="<%=customWelcome%>">Alternative site welcome page</label><%=helpPopup("Custom Welcome Page", customWelcomeHelp, false)%></td>
    <td><input type="text" id="<%=customWelcome%>" name="<%=customWelcome%>" size="50" value="<%= h(laf.getCustomWelcome()) %>"></td>
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
    <td><%=button("Save").submit(true).onClick("save();") %>&nbsp;
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
<script type="text/javascript" nonce="<%=getScriptNonce()%>">
    LABKEY.Utils.onReady(function() {
        if (<%=hasBadFormats%>)
        {
            // Show the date-time display format warning
            document.getElementById("dateFormatWarning").style.display='';
        }
    });

    const _form = new LABKEY.Form({ formElement: 'form-preferences'});

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

    function save()
    {
        // Concatenate the date and time parts for defaultDateTimeFormat
        const datePart = document.getElementById("dateSelect").value;
        const timePart = document.getElementById("timeSelect").value;
        const dateTimeElement = document.getElementById("defaultDateTimeFormat");
        dateTimeElement.value = datePart + " " + timePart;
        _form.setClean();
        return true;
    }
</script>
<%!
    private static final String NONE = "<none>";

    private boolean hasBadFormats = false;

    private void select(JspWriter out, DateDisplayFormatType type, String id, Set<String> options, String current, boolean addNone, boolean inherited) throws IOException
    {
        if (!NONE.equals(current) && !options.contains(current))
        {
            Set<String> set = new LinkedHashSet<>();
            set.add(current);
            set.addAll(options);
            options = set;
        }

        // Add a non-standard and an invalid format to every drop-down for testing purposes
        // TODO: Delete this block - just for testing
        Set<String> set = new LinkedHashSet<>(options);
        set.add(type == DateDisplayFormatType.Date ? "MM-dd-yyyy" : "kk:mm"); // non-standard
        set.add("xyz"); // invalid
        options = set;

        Date now = new Date();
        Map<String, String> map = options.stream()
            .collect(Collectors.toMap(option -> option, option -> {
                String formatted = "invalid format";

                try
                {
                    formatted = new SimpleDateFormat(option).format(now);
                }
                catch (IllegalArgumentException e)
                {
                    // use default value
                }
                return option + " (" + formatted + ")";
            }, (x, y) -> y, LinkedHashMap::new));

        if (addNone)
        {
            map.put("", NONE);
        }

        select()
            .disabled(inherited)
            .id(id)
            .name(id)
            .addOptions(map)
            .selected(NONE.equals(current) ? "" : current)
            .className(null)
            .addStyle("width:225px")
            .appendTo(out);

        if (!inherited && !NONE.equals(current) && !type.isStandardFormat(current))
        {
            out.print(HtmlString.unsafe("&nbsp;<i class=\"fa fa-exclamation-triangle has-warning\"></i>"));
            hasBadFormats = true;
        }
    }

    private HtmlString inheritCheckbox(Container c, boolean inherited, JspBase base, String name)
    {
        return inheritCheckbox(c, inherited, name, base, name);
    }

    private HtmlString inheritCheckbox(Container c, boolean inherited, String name, JspBase base, String... ids)
    {
        if (c.isRoot())
            return HtmlString.EMPTY_STRING;

        String checkBoxName = name + "Inherited";

        StringBuilder js = new StringBuilder("LABKEY.setDirty(true); ");
        Arrays.stream(ids)
            .forEach(id -> js.append("document.getElementById(\"").append(id).append("\").disabled = this.checked; "));
        base.addHandler(checkBoxName, "change", js.toString());

        HtmlStringBuilder builder = HtmlStringBuilder.of(HtmlString.unsafe("<td style=\"text-align: center;\"><input type=\"checkbox\" name=\""))
            .append(checkBoxName)
            .unsafeAppend("\" id=\"")
            .append(checkBoxName)
            .unsafeAppend("\"");

        if (inherited)
            builder.append(" checked");

        return builder.append(HtmlString.unsafe("></td>")).getHtmlString();
    }
%>
