<%
/*
 * Copyright (c) 2005-2015 LabKey Corporation
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
<%@ page import="org.labkey.api.data.Container"%>
<%@ page import="org.labkey.api.security.SecurityManager" %>
<%@ page import="org.labkey.api.settings.DateParsingMode" %>
<%@ page import="org.labkey.api.settings.LookAndFeelProperties" %>
<%@ page import="org.labkey.api.util.DateUtil" %>
<%@ page import="org.labkey.api.util.FolderDisplayMode" %>
<%@ page import="org.labkey.api.util.Formats" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ThemeFont" %>
<%@ page import="org.labkey.api.view.WebTheme" %>
<%@ page import="org.labkey.core.admin.AdminController" %>
<%@ page import="org.labkey.core.admin.ProjectSettingsAction" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    ProjectSettingsAction.LookAndFeelPropertiesBean bean = ((JspView<ProjectSettingsAction.LookAndFeelPropertiesBean>)HttpView.currentView()).getModelBean();
    Container c = getContainer();
    boolean folder = !c.isRoot() && !c.isProject();
    String clearMessage = folder ? "the default format properties" : "all look & feel properties";
    LookAndFeelProperties laf = LookAndFeelProperties.getInstance(c);
%>
<%=formatMissedErrors("form")%>

<labkey:form name="preferences" method="post" id="form-preferences">

<table width="100%" cellpadding=0>

<tr>
    <td colspan=2>&nbsp;</td>
</tr>

<% if (c.isProject()) {%>
<tr>
    <td colspan=2>Security defaults</td>
</tr>
<tr><td colspan=3 class=labkey-title-area-line></td></tr>
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
    <td colspan=2>Customize the look and feel of <%=h(c.isRoot() ? "your LabKey Server installation" : "the '" + c.getProject().getName() + "' project")%> (<%=text(bean.helpLink)%>)</td>
</tr>
<tr><td colspan=3 class=labkey-title-area-line></td></tr>
<tr>
    <td class="labkey-form-label">System description (used in emails)</td>
    <td><input type="text" name="systemDescription" size="50" value="<%= h(laf.getDescription()) %>"></td>
</tr>
<tr>
    <td class="labkey-form-label">Header short name (appears in every page header and in emails)</td>
    <td><input type="text" name="systemShortName" size="50" value="<%= h(laf.getShortName()) %>"></td>
</tr>
<tr>
    <td class="labkey-form-label">Web theme (color scheme)</td>
    <td>
        <select name="themeName">
            <% for (WebTheme theme : bean.themes)
                {
                    boolean selected;

                    //if a new theme has just been defined
                    if (bean.newTheme != null)
                        selected = theme == bean.newTheme;
                    else
                        selected = theme == bean.currentTheme;
                    %>
                    <option value="<%=h(theme.toString())%>"<%=selected(selected)%>><%=h(theme.getFriendlyName())%></option>
                <%}
            %>
        </select><%

        if (c.isRoot())
        { %>

        <%=textLink("define web themes", AdminController.getDefineWebThemesURL(false))%><%

        }
        %>
    </td>
</tr>
<tr>
    <td class="labkey-form-label">Font size</td>
    <td>
        <select name="themeFont">
            <% for (ThemeFont themeFont : bean.themeFonts)
                {
                    %><option value="<%=h(themeFont.toString())%>"<%=selected(themeFont == bean.currentThemeFont)%>><%=h(themeFont.getFriendlyName())%></option><%
                }
            %>
        </select>
        Font Size Samples:
            <% for (ThemeFont themeFont : bean.themeFonts)
                {
                    %><span style="font-size:<%=h(themeFont.getNormalSize())%>">&nbsp;&nbsp;<%=h(themeFont.toString())%></span><%
                }
            %>
    </td>
</tr>
<tr>
    <td class="labkey-form-label">Show Navigation</td>
    <td><%
            FolderDisplayMode currentMode = laf.getFolderDisplayMode();
        %>
        <input type="radio" name="folderDisplayMode" value="<%=h(FolderDisplayMode.ALWAYS.toString())%>"<%=checked(currentMode == FolderDisplayMode.ALWAYS)%>> <%=h(FolderDisplayMode.ALWAYS.getDisplayString())%><br>
        <input type="radio" name="folderDisplayMode" value="<%=h(FolderDisplayMode.ADMIN.toString())%>"<%=checked(currentMode == FolderDisplayMode.ADMIN)%>> <%=h(FolderDisplayMode.ADMIN.getDisplayString())%><br>
    </td>
</tr>

<tr>
    <td class="labkey-form-label">Show LabKey Help menu item</td>
    <td><input type="checkbox" name="enableHelpMenu" size="50"<%=checked(laf.isHelpMenuEnabled())%>></td>
</tr>

<tr>
    <td class="labkey-form-label">Logo link (specifies page that header logo links to)</td>
    <td><input type="text" name="logoHref" size="50" value="<%= h(laf.getLogoHref()) %>"></td>
</tr>
<tr>
    <td class="labkey-form-label">Support link (specifies page where users can request support)</td>
    <td><input type="text" name="reportAProblemPath" size="50" value="<%= h(laf.getUnsubstitutedReportAProblemPath()) %>"></td>
</tr>
<tr>
    <td class="labkey-form-label">Support email (shown to users if they don't have permission to see a page, or are having trouble logging in)</td>
    <td><input type="text" name="supportEmail" size="50" value="<%= h(laf.getSupportEmail()) %>"></td>
</tr>
<tr>
    <td>&nbsp;</td>
</tr>

<tr>
    <td colspan=2>Customize settings used in system emails (<%=text(bean.helpLink)%>)</td>
</tr>
<tr><td colspan=3 class=labkey-title-area-line></td></tr>
<tr>
    <td class="labkey-form-label">System email address (<i>from</i> address for system notification emails)</td>
    <td><input type="text" name="systemEmailAddress" size="50" value="<%= h(laf.getSystemEmailAddress()) %>"></td>
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

    String decimalFormatHelp = "The format string for numbers must be compatible with the format that the java class " +
            "<code>DecimalFormat</code> understands. A valid <code>DecimalFormat</code> is a pattern " +
            "specifying a prefix, numeric part, and suffix. For more information see the " +
            "<a href=\"" + Formats.getDecimalFormatDocumentationURL() + "\" target=\"blank\">java&nbsp;documentation</a>. " +
            "The following table has an abbreviated guide to pattern symbols:<br/>" +
            "<table class=\"labkey-data-region labkey-show-borders\"><colgroup><col><col><col><col></colgroup>" +
            "<tr class=\"labkey-frame\"><th align=left>Symbol<th align=left>Location<th align=left>Localized?<th align=left>Meaning</tr>" +
            "<tr valign=top><td><code>0</code><td>Number<td>Yes<td>Digit</tr>" +
            "<tr valign=top class=\"labkey-alternate-row\"><td><code>#</code><td>Number<td>Yes<td>Digit, zero shows as absent</tr>" +
            "<tr valign=top><td><code>.</code><td>Number<td>Yes<td>Decimal separator or monetary decimal separator</tr>" +
            "<tr valign=top class=\"labkey-alternate-row\"><td><code>-</code><td>Number<td>Yes<td>Minus sign</tr>" +
            "<tr valign=top><td><code>,</code><td>Number<td>Yes<td>Grouping separator</tr></table>";
    String dateFormatHelp = "The format string for dates must be compatible with the format that the java class " +
            "<code>SimpleDateFormat</code> understands. For more information see the " +
            "<a href=\"" + DateUtil.getSimpleDateFormatDocumentationURL() + "\" target=\"blank\">java&nbsp;documentation</a>. " +
            "The following table has a partial guide to pattern symbols:<br/>" +
            "<table class=\"labkey-data-region labkey-show-borders\"><colgroup><col><col><col></colgroup>" +
            "<tr class=\"labkey-frame\"><th align=left>Letter<th align=left>Date or Time Component<th align=left>Examples</tr>" +
            "<tr><td><code>G</code><td>Era designator<td><code>AD</code></tr>" +
            "<tr class=\"labkey-alternate-row\"><td><code>y</code><td>Year<td><code>1996</code>; <code>96</code></tr>" +
            "<tr><td><code>M</code><td>Month in year<td><code>July</code>; <code>Jul</code>; <code>07</code></tr>" +
            "<tr class=\"labkey-alternate-row\"><td><code>w</code><td>Week in year<td><code>27</code></td></tr>" +
            "<tr><td><code>W</code><td>Week in month<td><code>2</code></td></tr>" +
            "<tr class=\"labkey-alternate-row\"><td><code>D</code><td>Day in year<td><code>189</code></td></tr>" +
            "<tr><td><code>d</code><td>Day in month<td><code>10</code></tr>" +
            "<tr class=\"labkey-alternate-row\"><td><code>F</code><td>Day of week in month<td><code>2</code></tr>" +
            "<tr><td><code>E</code><td>Day in week<td><code>Tuesday</code>; <code>Tue</code></tr>" +
            "<tr class=\"labkey-alternate-row\"><td><code>a</code><td>Am/pm marker<td><code>PM</code></tr>" +
            "<tr><td><code>H</code><td>Hour in day (0-23)<td><code>0</code></tr>" +
            "<tr class=\"labkey-alternate-row\"><td><code>k</code><td>Hour in day (1-24)<td><code>24</code></tr>" +
            "<tr><td><code>K</code><td>Hour in am/pm (0-11)<td><code>0</code></tr>" +
            "<tr class=\"labkey-alternate-row\"><td><code>h</code><td>Hour in am/pm (1-12)<td><code>12</code></tr></table>";
%>
<tr>
    <td colspan=2>Customize date and number formats (<%=text(bean.helpLink)%>)</td>
</tr>
<tr><td colspan=3 class=labkey-title-area-line></td></tr><%

    // TODO: This check is temporary and should switch to "if (!folder) {}" once the date parsing methods pass Container consistently
    if (c.isRoot())
    {
        DateParsingMode dateParsingMode = laf.getDateParsingMode();
        String dateParsingHelp = "LabKey needs to understand how to interpret (parse) dates that users enter into input forms. " +
                "For example, if a user enters the date \"10/4/2013\" does that person mean October 4, 2013 (typical interpretation " +
                "in the United States) or April 10, 2013 (typical interpretation in most other countries)? Choose the " +
                "parsing mode that matches your users' expectations.";
%>
<tr>
    <td class="labkey-form-label">Date parsing mode<%=PageFlowUtil.helpPopup("Date parsing", dateParsingHelp, false)%></td>
    <td>
        <input type="radio" name="dateParsingMode" value="<%=h(DateParsingMode.US.toString())%>"<%=checked(dateParsingMode == DateParsingMode.US)%>> <%=h(DateParsingMode.US.getDisplayString())%><br>
        <input type="radio" name="dateParsingMode" value="<%=h(DateParsingMode.NON_US.toString())%>"<%=checked(dateParsingMode == DateParsingMode.NON_US)%>> <%=h(DateParsingMode.NON_US.getDisplayString())%><br>
    </td>
</tr><%
    }
%>
<tr>
    <td class="labkey-form-label">Default display format for dates<%=PageFlowUtil.helpPopup("Date format", dateFormatHelp, true)%></td>
    <td><input type="text" name="defaultDateFormat" size="50" value="<%= h(laf.getDefaultDateFormat()) %>"></td>
</tr>
<tr>
    <td class="labkey-form-label">Default display format for numbers<%=PageFlowUtil.helpPopup("Number format", decimalFormatHelp, true)%></td>
    <td><input type="text" name="defaultNumberFormat" size="50" value="<%= h(laf.getDefaultNumberFormat()) %>"></td>
</tr>
<tr>
    <td>&nbsp;</td>
</tr>

<tr>
    <td colspan=2>Customize column restrictions (<%=text(bean.customColumnRestrictionHelpLink)%>)</td>
</tr>
<tr>
    <td class="labkey-form-label">Restrict charting columns by measure and dimension flags</td>
    <td><input type="checkbox" name="restrictedColumnsEnabled" size="50"<%=checked(laf.areRestrictedColumnsEnabled())%>></td>
</tr>
<tr>
    <td>&nbsp;</td>
</tr>

<%
    String customLoginHelp = "The custom login page is specified as a string composed of the controller name and an action name in" +
            " the format: <controller>-<action>.  For example the string 'myModule-customLogin' can be entered to enable a custom login provided as" +
            " an HTML page located at /labkey/build/deploy/modules/myModule/views/customLogin.html";
%>
<tr>
    <td colspan=2>Provide a custom login page (<%=text(bean.helpLink)%>)</td>
</tr>
<tr><td colspan=3 class=labkey-title-area-line></td></tr>
<tr>
    <td class="labkey-form-label">Alternative login action<%=PageFlowUtil.helpPopup("Custom Login Page", customLoginHelp, false)%></td>
    <td><input type="text" name="customLogin" size="50" value="<%= h(laf.getCustomLogin()) %>"></td>
</tr>
<tr>
    <td>&nbsp;</td>
</tr>

<tr>
    <td><%= button("Save").submit(true).onClick("_form.setClean();") %>&nbsp;<%= PageFlowUtil.button("Reset")
            .href(new AdminController.AdminUrlsImpl().getResetLookAndFeelPropertiesURL(c))
            .onClick("return confirmReset();") %>
    </td>
</tr>
<tr>
    <td>&nbsp;</td>
</tr>

</table>
</labkey:form>
<script type="text/javascript">
    var _form = new LABKEY.Form({ formElement: 'form-preferences'});

    function confirmReset()
    {
        if (confirm('Are you sure you want to clear <%=text(clearMessage)%>?'))
        {
            _form.setClean();
            return true;
        }
        else
            return false;
    }
</script>
