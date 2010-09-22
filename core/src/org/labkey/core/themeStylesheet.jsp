<%
/*
 * Copyright (c) 2008-2010 LabKey Corporation
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
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.settings.LookAndFeelProperties" %>
<%@ page import="org.labkey.api.view.ThemeFont" %>
<%@ page import="org.labkey.api.view.WebTheme" %>
<%@ page import="org.labkey.api.view.WebThemeManager" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%

/* NOTE - The ONLY styles that should be set in this file are ones pulled from the look and feel settings.
   That means colors and font sizes only. All other properties should be set in stylesheet.css. This file represents
   the minimal set of overrides to respect the user's look and feel settings. */

   
    Container c = getViewContext().getContainer();
    WebTheme theme = WebThemeManager.getTheme(c);

    if (!theme.isCustom()) // This jsp should not be used if the WebTheme is Custom. Shouldn't even get here.
    {
        response.setContentType("text/css");
        ThemeFont themeFont = ThemeFont.getThemeFont(c);
        LookAndFeelProperties laf = LookAndFeelProperties.getInstance(c);
        String navBarWidth = laf.getNavigationBarWidth();

        String formLabel           = theme.getEditFormColor();
        String fullScreenBorder    = theme.getFullScreenBorderColor();
        String wpHeaderPanel       = theme.getTitleBarBackgroundString();
        String wpHeaderPanelBorder = theme.getTitleBarBorderString();
        String navBorder           = theme.getHeaderLineColor();
        String navBackground       = theme.getNavBarColor();
        String linkAndHeaderText   = theme.getTitleColorString();

        boolean testBorders = false;
        if (testBorders)
        {
            wpHeaderPanelBorder = "ff0000";
            navBorder           = "00ff00";
        }

    /*

    index:

    defaults
    general
        -various
        -data region
    home template
        -main
            -header-panel
                -main-title
            -site-nav-panel
                -expandable-nav
                -nav-tree
            -proj
                -proj-nav-panel
                -body-panel
                -side-panel
    module specific (alphabetical)
    GWT
    yui

    Ext

    */

%>

/* defaults */
body, div, td, th, table, img, form,
.x-form-item, .x-panel-header, .x-btn button
{
    font-size: <%= themeFont.getNormalSize() %>;
}

input, .gwt-TextBox, .x-form-field
{
	font-size:<%= themeFont.getTextInputSize() %>;
}

a, a:visited
{
    color: #<%=linkAndHeaderText%>;
}

select
{
	font-size:<%= themeFont.getTextInputSize() %>;
}

legend
{
    border-color: #<%= navBorder %>;
    background-color: #<%= wpHeaderPanel %>;
}

fieldset
{
    border-color: #<%= navBorder %>;
}

/* general */

.labkey-header:hover
{
    background: #<%= wpHeaderPanel %>;
}

.labkey-header-large:hover
{
    background: #<%= wpHeaderPanel %>;
}

.labkey-header-large
{
    font-size: <%= themeFont.getHeader_1Size() %>;
}

.labkey-message
{
    font-size: <%= themeFont.getNormalSize() %>;
}

.labkey-message-strong
{
    font-size: <%= themeFont.getNormalSize() %>;
}

.labkey-form-label, tr td.labkey-form-label-nowrap
{
    background-color: #<%= formLabel %>;
}

.labkey-form-label-nowrap, tr td.labkey-form-label-nowrap
{
	background-color: #<%= formLabel %>;
}

.labkey-full-screen-background
{
    background-color: #<%= fullScreenBorder %>;
}

th.labkey-col-header-filter.hover, td.labkey-col-header-filter.hover, .labkey-col-header-filter th.hover
{
    background: #<%= wpHeaderPanel %>;
}

.labkey-title-area-line
{
    background-color: #<%= navBorder %>;
}

.labkey-frame
{
    background: #<%= navBackground %>;
}

.labkey-nav-bordered
{
   border-color: #<%= navBorder %>;
}

.labkey-completion-highlight
{
    background-color: #<%= navBackground %>;
}

.labkey-header
{
    color: #<%= linkAndHeaderText %>;
}

.labkey-announcement-title span, .labkey-announcement-title a
{
    color: #<%=linkAndHeaderText%>
}

.labkey-data-region A
{
    color: #<%=linkAndHeaderText%>;
}

th.labkey-col-header-filter, td.labkey-col-header-filter, .labkey-col-header-filter th
{
    color: #<%= linkAndHeaderText %>;
}

th.labkey-expand-collapse-area
{
    color: #<%=linkAndHeaderText%>;
}

/* home template */

.labkey-main-title
{
    font-size:<%= themeFont.getPageTitleSize() %>;
}

.labkey-site-nav-panel
{
    background-color: #<%= navBackground %>;
    border-top-color: #<%= navBorder %>;
    border-right-color: #<%= navBorder %>;
}

table.labkey-expandable-nav
{
    border-top-color: #<%= navBorder %>;
    border-right-color: #<%= navBorder %>;
    border-bottom-color: #<%= navBorder %>;
    width: <%= navBarWidth %>px;
}

.labkey-expandable-nav-body
{
    border-top-color: #<%= navBorder %>;
}

tr.labkey-nav-tree-row
{
    color: #<%= linkAndHeaderText %>;
}

tr.labkey-nav-tree-row:hover
{
    background-color: #<%= wpHeaderPanel %>;
}

.labkey-nav-page-header
{
	font-size: <%= themeFont.getPageHeaderSize() %>;
	color: #<%=linkAndHeaderText%>;
}

.labkey-wp-header th, .labkey-wp-header td, .labkey-wp-title
{
    color: #<%=linkAndHeaderText%>;
}

.labkey-wp-header a:link, .labkey-wp-header a:visited
{
    color:#<%=linkAndHeaderText%>;
}

tr.labkey-wp-header
{
    background-color: #<%= wpHeaderPanel %>;
}

.labkey-wp-header th, .labkey-wp-header td
{
    border-color: #<%= wpHeaderPanelBorder %>;
}

/* module specific */

/* MS1 */

td.labkey-ms1-filter
{
    background: #<%= navBackground %>;
}

/* Wiki */

div.labkey-status-info, .labkey-status-info
{
    background-color: #<%=navBackground%>;
    border-color: #<%=navBorder%>;
}


td.labkey-main-menu
{
    background-color:#<%=navBackground%>;
    border-top-color: #<%=navBorder%>;
    border-bottom-color: #<%=navBorder%>
}

/* GWT */

.gwt-CheckBox {
  font-size: <%=themeFont.getNormalSize()%>;
}

.gwt-MenuBar .gwt-MenuItem {
  font-size: <%=themeFont.getNormalSize()%>;
}

.gwt-Tree .gwt-TreeItem {
  font-size: <%=themeFont.getNormalSize()%>;
}

.gwt-ToolTip {
	background-color: #<%= formLabel %>;
	font-size: <%= themeFont.getNormalSize() %>;
}

.gwt-DialogBox .Caption {
    background-color: #<%=wpHeaderPanel%>;
    border-color: #<%= wpHeaderPanelBorder %>;
}

.gwt-DialogBox {
  border-color: #<%=navBorder%>;
}

.gwt-HorizontalSplitter .Bar {
  background-color: #<%=navBorder%>;
}

.gwt-VerticalSplitter .Bar {
  background-color: #<%=navBorder%>;
}

.gwt-MenuBar {
  background-color: #<%=navBackground%>;
  border-color: #<%=navBorder%>;
}

.gwt-MenuBar .gwt-MenuItem-selected {
  background-color: #<%=navBorder%>;
}

.gwt-StackPanel .gwt-StackPanelItem {
  background-color: #<%=navBackground%>;
}

/* yui */

div.yuimenu {
    border-color: #<%=navBorder%>;
}

div.yuimenu h6,
div.yuimenubar h6 {
    border-color: #<%=navBorder%>;
    color: #<%=linkAndHeaderText%>;
}

div.yuimenu ul {
    border-color: #<%=navBorder%>;
}

div.yuimenubar li.yuimenubaritem {
    border-color:#<%=navBorder%>;
}

div.yuimenu li.selected,
div.yuimenubar li.selected {
    background-color:#<%=navBackground%>;
}

div.yuimenu li a,
div.yuimenubar li a {
    color:#<%=linkAndHeaderText%>;
}

div.yuimenu li.selected a.selected,
div.yuimenu li.selected em.selected,
div.yuimenubar li.selected a.selected {
    color:#<%=linkAndHeaderText%>;
}



/*
 * Ext JS Library 2.2
 * Copyright(c) 2006-2008, Ext JS, LLC.
 * licensing@extjs.com
 *
 * http://extjs.com/license
 *
 * These overrides were formerly in gtheme.css
 */

.x-panel-header {
	background-color: #<%= wpHeaderPanel %>;
	border-color: #<%= wpHeaderPanelBorder %>;
}

.x-grid3-hd-inner {
font-weight:normal;
    color:#<%= linkAndHeaderText %>%>;
}

.x-grid-group-hd{
    border-color:#<%= fullScreenBorder %> ;
}

.x-menu {
    border-color: #<%= wpHeaderPanelBorder %>;
}

.extContainer .x-menu-item-active {
    border-color: #<%= wpHeaderPanelBorder %>;
}

/*********** X-panel *******/
.x-panel-tc {
	background-color: #<%= navBackground %>;
}
.x-panel-tl {
	background-color: #<%= navBackground %>;
}
.x-panel-tr {
	background-color: #<%= navBackground %>;
}
.x-panel-bc {
	background-color: #<%= navBackground %>;
}
.x-panel-bl {
	background-color: #<%= navBackground %>;
}
.x-panel-br {
	background-color: #<%= navBackground %>
}
.x-panel-ml {
	background-color: #<%= navBackground %>;
}
.x-panel-mr{
	background-color: #<%= navBackground %>;
}

/*********** X-tree ******/

.x-tree-node .x-tree-selected {
    background-color: <%= navBackground %>;
}

/*********** X-window ******/
.x-window-tc {
	background-color: #<%= fullScreenBorder %> ;
}
.x-window-tl {
	background-color: #<%= fullScreenBorder %>;
}
.x-window-tr {
	background-color: #<%= fullScreenBorder %>;
}
.x-window-bl {
	background-color: #<%= fullScreenBorder %>;
}
.x-window-br {
	background-color: #<%= fullScreenBorder %>;
}
.x-window-footer {
	border-bottom-color: #<%= fullScreenBorder %>;
}

.x-window-ml {
	background-color: #<%= fullScreenBorder %>;
}
.x-window-mr {
	background-color: #<%= fullScreenBorder %>;
}

.x-window-maximized .x-window-tc {
	background-color: #<%= fullScreenBorder %>;
}

.x-panel-nofooter .x-panel-bc, .x-panel-nofooter .x-window-bc {
	background-color: #<%= fullScreenBorder %>;
}

.x-panel-body {
    border-color: #<%= wpHeaderPanelBorder %>;
}

body .x-grid3 .x-grid3-row-selected {
    border-color: #<%= fullScreenBorder %>;
    background: #<%= navBackground %> !important;
}



/*****     TAB STRIPS     *****/

<%
String tabBorderColor = wpHeaderPanelBorder;
String tabSelectedColor = wpHeaderPanel;
String tabFontColor = linkAndHeaderText;
String tabFontSize = themeFont.getNormalSize();
boolean shadeSelectedTab = true;
%>

/* labkey */

/* for navbar area */
.labkey-proj-nav-panel td.labkey-tab-selected
{
    color: #<%=tabFontColor%>;
    background-color:#ffffff;
    border-top-color: #<%= navBorder %>;
    border-left-color: #<%= navBorder %>;
    border-right-color: #<%= navBorder %>;
    border-bottom-color: #ffffff;
}
.labkey-proj-nav-panel .labkey-tab-strip,
.labkey-proj-nav-panel .labkey-tab-strip .labkey-tab-space,
.labkey-proj-nav-panel .labkey-tab-strip .labkey-tab,
.labkey-proj-nav-panel .labkey-tab-strip a
{
    border-color: #<%= navBorder %>;
}

/* everwhere else */
.labkey-tab-selected, td.labkey-tab-selected, ul.labkey-tab-strip li.labkey-tab-active a
{
    color: #<%=tabFontColor%>;
    background-color: #<%=tabSelectedColor%>;
    border-bottom-color: #<%=tabSelectedColor%>;
}

.labkey-tab-strip,
ul.labkey-tab-strip,
.labkey-tab-strip .labkey-tab,
.labkey-tab-strip-content,
ul.labkey-tab-strip a,
.labkey-tab-strip a,
.labkey-tab-strip li,
.labkey-tab-strip .labkey-tab-space
{
   border-color: #<%= tabBorderColor %>;
   color: #<%=tabFontColor%>;
}
.labkey-tab-strip-spacer
{
    background-color:#<%=tabSelectedColor%>;
	border-color: #<%=tabBorderColor%>;
}
td.labkey-tab-content
{
   border-left-color: #<%= tabBorderColor %>;
   border-right-color: #<%= tabBorderColor %>;
   border-bottom-color: #<%= tabBorderColor %>;
}

/* gwt */

.gwt-TabBar {
  font-size: <%=tabFontSize%>;
}

.gwt-TabBar .gwt-TabBarRest {
  border-color: #<%=tabBorderColor%>;
}

.gwt-TabBar .gwt-TabBarItem, .gwt-TabBar .gwt-TabBarItem-selected {
  border-color: #<%=tabBorderColor%>;
  color: #<%= tabFontColor %>;
}

.gwt-TabBar .gwt-TabBarItem-selected {
  border-color: #<%=tabBorderColor%>;
  background-color:#<%= tabSelectedColor %>;
  color: #<%= tabFontColor %>;
}

.gwt-TabBar .gwt-TabBarItem-selected {
  border-bottom:0;
}

.gwt-TabBar .gwt-TabBarItem .gwt-Label, .gwt-TabBar .gwt-TabBarItem-selected .gwt-Label {
  color: #<%= tabFontColor %>;
  font-size: <%= tabFontSize %>;
}

.gwt-TabPanelBottom { border: solid 1px #<%= tabBorderColor %>; }

/* ext */

.x-tab-panel-body {
    border-color: #<%= tabBorderColor %>;
}

.x-tab-panel-header, .x-tab-panel-footer {
	background-color: #<%= tabSelectedColor %>;
	border-color: #<%= tabBorderColor %>;
}

ul.x-tab-strip span.x-tab-strip-text { color: #<%= tabFontColor %>; font-size: <%= tabFontSize %>; }

ul.x-tab-strip-top .x-tab-strip-active .x-tab-right, ul.x-tab-strip-top .x-tab-strip-active .x-tab-close {
    background-color:#<%= tabSelectedColor%>;
    border-bottom-color:#<%=tabSelectedColor%>;
}

ul.x-tab-strip-top { border-bottom-color:#<%=tabBorderColor%>;}

ul.x-tab-strip-top li { border-color:<%=tabBorderColor%>; }

.x-tab-panel-header-plain .x-tab-strip-spacer, .x-tab-panel-header .x-tab-strip-spacer {
    border-color:#<%=tabBorderColor%>;
    background-color:#<%=tabSelectedColor%>
}
<% } %>
