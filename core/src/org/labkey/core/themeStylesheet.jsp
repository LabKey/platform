<%
/*
 * Copyright (c) 2008-2015 LabKey Corporation
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
<%@ page import="org.labkey.api.view.ThemeFont" %>
<%@ page import="org.labkey.api.view.WebTheme" %>
<%@ page import="org.labkey.api.view.WebThemeManager" %>
<%@ page import="java.awt.*" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%

/*
   NOTE - The ONLY styles that should be set in this file are ones pulled from the look and feel settings.
   That means colors and font sizes only. All other properties should be set in stylesheet.css. This file represents
   the minimal set of overrides to respect the user's look and feel settings.
*/
    Container c = getContainer();
    WebTheme theme = WebThemeManager.getTheme(c);
    response.setContentType("text/css");

    String link        = theme.getLinkColor();
    String text        = theme.getTextColor();
    String grid        = theme.getGridColor();
    String primary     = theme.getPrimaryBackgroundColor();
    String second      = theme.getSecondaryBackgroundColor();
    String borderTitle = theme.getBorderTitleColor();
    String webpart     = theme.getWebPartColor();
    String fontSize    = ThemeFont.getThemeFont(c).getNormalSize();

    // TODO: Generate a different stylesheet for font changes
    String liWidth = "11em";    // MEDIUM
    String menuPadding = "6px";
    if (fontSize.equalsIgnoreCase("14px"))
    {
        liWidth = "10em";       // LARGE
        menuPadding = "5px";
    }
    else if (fontSize.equalsIgnoreCase("12px"))
    {
        liWidth = "10em";       // SMALL
        menuPadding = "7px";
    }
    else if (fontSize.equalsIgnoreCase("11px"))
    {
        liWidth = "10em";       // SMALLEST
        menuPadding = "7px";
    }

    Color toolIconBackgroundColor = WebTheme.parseColor(link);
    int r = toolIconBackgroundColor.getRed();
    int g = toolIconBackgroundColor.getGreen();
    int b = toolIconBackgroundColor.getBlue();
%>

/* defaults, general attributes */
body, div, td, th, table, img, form
{
    color: #<%= text %>;
}

body
{
    background-color: #<%= primary %>;
}

body, div, td, th, table, img, form,
.x-form-item, .x-panel-header, .x-btn button
{
    font-size: <%= fontSize %>;
}

.x4-tree-node-text
{
    font-size: <%= fontSize %>;
}

a, a:visited, .labkey-link, .labkey-link:visited
{
    color: #<%= link %>;
}

table#header
{
    border-color: #<%= link %>;
    background-color:#<%= second %>;
}

/* General */
.labkey-header
{
    color: #<%= link %>;
}

.labkey-header:hover
{
    background-color: #<%= grid %>;
}

th.labkey-header
{
    color: #<%= text %>;
}

th.labkey-header a:hover
{
    color: #<%= link %>;
}

.labkey-header-large
{
    color: #<%= text %>;
}

.labkey-header-large:hover
{
    background-color: #<%= grid %>;
}

.labkey-announcement-title span, .labkey-announcement-title a
{
    color: #<%= text %>;
}

.labkey-announcement-title a.announcement-title-link
{
	color:#<%= link %>;
}

/*  Used across the site whenever there is a label with a colored background */
.labkey-form-label, tr td.labkey-form-label
{
    background-color: #<%= webpart %>;
}

.labkey-form-label-nowrap, tr td.labkey-form-label-nowrap
{
	background-color: #<%= webpart %>;
}

/* Used for button bars across the site */
div.labkey-button-bar, table.labkey-button-bar
{
    background-color: #<%= grid %>;
}

/* Used for the new css menu buttons, which consist of a link
    or a span wrapped around an input for submit buttons */
.labkey-button-bar .labkey-button,
.labkey-button-bar .labkey-menu-button,
.labkey-button-bar .labkey-button:visited,
.labkey-button-bar .labkey-menu-button:visited
{
    background-color: #<%= grid %>;
    border: 1px solid #<%= grid %>;
    color: #<%= link %>;
}

.labkey-button-bar .labkey-button:hover,
.labkey-button-bar .labkey-menu-button:hover
{
    background-color: #<%= second %>;
    border: 1px solid #<%= borderTitle %>;
    color: #<%= text %>;
}

/* menu icons */
span.button-icon {
    background-color: #<%= link %>;
}

/* this keeps the enabled button text blue */
a.labkey-menu-button
{
    color: #<%= link %>;
}

/* this keeps the hover state style for enabled buttons */
a.labkey-menu-button:hover
a.labkey-menu-button:hover span
{
    border: 1px solid #<%= borderTitle %>;
    background-color: #<%= second %>;
	color: #<%= text %>;
}

/* ********* Standalone Buttons ******** */

a.labkey-button, a.labkey-button:visited,
span.labkey-button
{
    background-color: #<%= second %>;
    color: #<%= link %>;
}

a.labkey-button:hover,
span.labkey-button:hover
{
    background-color: #<%= second %>;
    color: #<%= text %>;
}

a.labkey-button:active,
span.labkey-button:active
{
    background-color: #<%= second %>;
}

/* This is used in simple cases to give an area the same background as the
 side panel */
.labkey-frame
{
    background-color: #<%= grid %>;
}

/* Used for the "App Bar" containing the main title and the application button bar */
/* Used for tabs */

.labkey-app-bar
{  /* TODO: this should be replaced by adding a new color to the theme.  */
    background-color:#<%= webpart %>;
}

.labkey-app-bar ul.labkey-tab-strip { /* map bottom border to Primary background color */
	border-bottom: 1px solid #<%= primary %>;
}

.labkey-app-bar li.labkey-tab-inactive { /* map text color to Link color */
    color: #<%= link %>;
}

.labkey-app-bar ul li.tab-nav-active { /* map bottom border and background color to Primary background color */
    background-color: #<%= primary %>;
	border-bottom: 1px solid #<%= primary %>;
}

.labkey-app-bar ul.labkey-tab-strip li:hover { /* map bottom border and background color to Primary background color */
    background-color: #<%= primary %>;
    border-bottom: 1px solid #<%= primary %>;
}

.labkey-completion-highlight
{
    background-color: #<%= grid %>;
}

.labkey-full-screen-background
{
    background-color: #<%= primary %>;
}

.labkey-full-screen-table
{
    background-color: #<%= second %>;
    border: 5px solid #<%= webpart %>;
}

table.labkey-customize-view td,
table.labkey-customize-view a,
table.labkey-customize-view .labkey-tab a:hover
{
    background: #<%= second %>;
}

table.labkey-data-region
{
    background-color: #<%= second %>;
}

.labkey-data-region .labkey-data-region-title
{
    background-color: #<%= second %>;
}

.labkey-data-region-header-container
{
    background-color: #<%= grid %>;
}

div.facet_header {
    background-color: #<%= grid %>;
}

div.facet_header .x4-panel-header-text {
    color: #<%= link %>;
}

th.labkey-col-header-filter, td.labkey-col-header-filter, tr.labkey-col-header-filter th
{
    color: #<%= link %>;
}

th.labkey-col-header-filter:hover, td.labkey-col-header-filter:hover, tr.labkey-col-header-filter th:hover
{
    background-color: #<%= grid %>;
}

td.labkey-blank-cell, th.labkey-blank-cell
{
    background: #<%= second %>;
}

.labkey-customview-item.x-view-over,
.labkey-customview-item.x4-view-over
{
    border-top: 1px solid #<%= link %>;
    border-bottom: 1px solid #<%= link %>;
}

#helpDiv
{
    background-color:#<%= second %>;
}

.labkey-main
{
    background-color: #<%= primary %>;
}

#headerpanel
{
    background-color: #<%= second %>;
}

.labkey-webpart-menu
{
    background-color:#<%= second %>;
}

.labkey-search-filter A, .labkey-search-filter A:visited
{
    color: #<%= borderTitle %>;
}

.labkey-search-navresults h3
{
    color: #<%= borderTitle %>;
}

.labkey-search-results td
{
    color: #<%= borderTitle %>;
}

table.labkey-expandable-nav
{
    background-color: #<%= primary %>;
}

tr.labkey-nav-tree-row
{
    color: #<%= link %>;
}

tr.labkey-nav-tree-row:hover
{
    background-color: #<%= grid %>;
}

table#navBar
{
    background-color:#<%= primary %>;
}

table.labkey-wp
{
    border: 5px solid #<%= webpart %>;
}

td.labkey-wp-body
{
    background: #<%= second %>;
}

tr.labkey-wp-header
{
    background-color: #<%= webpart %>;
}

.labkey-wp-header th, .labkey-wp-header td, .labkey-wp-title
{
    color:#<%= text %>;
}

.labkey-wp-text-buttons a, .labkey-wp-text-buttons a:visited
{
    color:#<%= link %>;
}

.labkey-wp-header a:link, .labkey-wp-header a:visited
{
    color:#<%= link %>;
}

.labkey-wp-header a:hover
{
    color:#<%= text %>;
}

th.labkey-wp-title-left
{
    color: #<%= text %>;
}

th.labkey-wp-title-left a:link, th.labkey-wp-title-left a:visited
{
    color: #<%= text %>;
}

th.labkey-wp-title-left a:hover
{
    color: #<%= link %>;
}

a.labkey-text-link, a.labkey-text-link:visited, a.labkey-text-link-noarrow, a.labkey-text-link-noarrow:visited
{
    color: #<%= link %>;
}

a.labkey-text-link:hover
{
	color: #<%= text %>;
}

td.labkey-ms1-filter
{
    background: #<%= grid %>;
}

/* GWT */
.gwt-DialogBox .Caption
{
    background-color: #<%= grid %>;
}

.gwt-MenuBar
{
    background-color: #<%= grid %>;
}

.gwt-StackPanel .gwt-StackPanelItem
{
    background-color: #<%= grid %>;
}

.gwt-TabBar .gwt-TabBarItem
{
    background-color: #<%= grid %>;
}

.gwt-TabBar .gwt-TabBarItem-selected
{
    background-color: #<%= second %>;
}

/* ExtJS */
.x-panel-header
{
    background: #<%= grid %>;
}

li.x-menu-item-active
{
	background:#<%= grid %> none repeat scroll 0 0;
}

/* Buttons */
.x-btn button
{
    color: #<%= link %>;
}

.x-btn-over button
{
    color: #<%= text %>;
}

.x-toolbar .x-btn-over button
{
	color: #<%= text %>;
}

/*********** X-tree ******/

.x-tree-node .x-tree-selected
{
    background-color: #<%= grid %>;
}

/*********** X-window ******/
.x-window-tc
{
	background: #<%= link %>;
}

.x-window-tl
{
	background: #<%= link %>;
}

.x-window-tr
{
	background: #<%= link %>;
}

.x-window-bl
{
	background: #<%= link %>;
}

.x-window-br
{
	background: #<%= link %>;
}

.x-window-footer
{
	border-bottom-color: #<%= link %>;
}

.x-window-ml
{
	background: #<%= link %>;
}

.x-window-mr
{
	background: #<%= link %>;
}

.x-window-maximized .x-window-tc
{
	background: #<%= link %>;
}

.x-panel-nofooter .x-panel-bc, .x-panel-nofooter .x-window-bc
{
	background: #<%= link %>;
}

body .x-grid3 .x-grid3-row-selected
{
    border: 1px solid #<%= link %>;
    background: #<%= grid %> !important;
}

.labkey-error-body
{
    background-color: #<%= webpart %>;
}

td.labkey-tab, .labkey-tab
{
    background-color: #<%= grid %>;
    color: #<%= link %>;
}

.labkey-tab a
{
    background-color: #<%= grid %>;
    color: #<%= link %>;
}

.labkey-tab a:hover
{
    background-color: #<%= grid %>;
}

ul.x-tab-strip-top li
{
	background-color: #<%= grid %>;
}

.x-tab-strip span.x-tab-strip-text
{
	color: #<%= link %>;
}

li.labkey-tab-inactive a
{
    background-color: #<%= webpart %>;
}

.x-grid3-hd-inner
{
    color: #<%= link %>;
}

.x-toolbar
{
    background: #<%= grid %>;
}

.labkey-fullscreen-wizard-steps
{
    background-color: #<%= webpart %>;
}

.labkey-fullscreen-wizard-background
{
    background-color: #<%= webpart %>;
}

.labkey-ancillary-wizard-background
{
    background-color: #<%= grid %>;
}

.tool-icon a,
.tool-icon a:visited {
    color : #<%= link %>
}

.tool-icon img {
    background: rgba(<%= r %>, <%= g %>, <%= b %>, 0.7); /* FF, Safari, Chrome, IE9 */
    filter:progid:DXImageTransform.Microsoft.gradient(startColorstr=#b2<%= link %>, endColorstr=#b2<%= link %>); /* IE 5.5 - 7 */
    -ms-filter: "progid:DXImageTransform.Microsoft.gradient(startColorstr=#b2<%= link %>, endColorstr=#b2<%= link %>)"; /* IE 8 */
}

.tool-icon:hover img {
    background: rgba(<%= r %>, <%= g %>, <%= b %>, 1.0); /* FF, Safari, Chrome, IE9 */
    filter:progid:DXImageTransform.Microsoft.gradient(startColorstr=#b2<%= link %>, endColorstr=#b2<%= link %>); /* IE 5.5 - 7 */
    -ms-filter: "progid:DXImageTransform.Microsoft.gradient(startColorstr=#b2<%= link %>, endColorstr=#b2<%= link %>)"; /* IE 8 */
}

.study-schedule-container .x4-grid-header-ct {
    background-color: #<%= grid %>;
}

.themed-panel div.x4-panel-header {
    background-color: #<%= webpart %> !important;
    background-image: none;
}

.themed-panel .x4-panel-header-default-top {
    -webkit-box-shadow: #<%= webpart %> 0 1px 0px 0 inset;
    -moz-box-shadow: #<%= webpart %> 0 1px 0px 0 inset;
    box-shadow: #<%= webpart %> 0 1px 0px 0 inset;
}

.themed-panel2 .x4-panel-header {
    background: #<%= grid %>;
    padding: 5px;
}

.themed-panel2 .x4-panel-header-text-container-default {
    font-size: <%= fontSize %>;
    font-family: tahoma,arial,verdana,sans-serif;
    color: #000000;
}

div.x4-splitter-vertical, div.x4-splitter-active, div.x4-resizable-overlay {
    background-color: #<%= webpart %> !important;
}

.ext-el-mask-msg,
.ext-el-mask-msg div {
    border-color: #<%= webpart %>;
}

.ext-el-mask-msg {
    background-color: #<%= webpart %>;
    background-image: none;
}

li.tab-nav-active {
    background-color: #<%= primary %>;
}

li.tab-nav-inactive:hover {
    background-color: #<%= primary %>;
}

li.tab-nav-inactive a {
    color: #<%= link %>;
}

.labkey-main-menu
{
    background-color: #<%= link %>;
}

.labkey-main-menu-item:hover,
.labkey-main-menu-item.selected
{
    background-color: #<%= second %>;
}

.labkey-main-menu-item:hover a,
.labkey-main-menu-item.selected a.labkey-main-menu-link
{
    color: #<%= text %>;
}

div.headermenu {
    background-color: #<%= link %>;
}

.labkey-main-menu li {
    padding: <%=menuPadding%> 9px;
}

.labkey-main-menu li.menu-projects {
    padding: <%=menuPadding%> 18px <%=menuPadding%> 23px;
}

.project-nav ul li {
    width: <%=liWidth%>;
}

.project-nav ul li a:hover {
    background-color: #<%= webpart %>
}

.folder-nav ul li a:hover {
    white-space: nowrap;
    background-color: #<%= webpart %>;
}

/* See betanav.css for the rest of the styles. */
.project-list ul li:hover {
    background-color: #<%= link %>;
}