<%
/*
 * Copyright (c) 2008-2011 LabKey Corporation
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
    response.setContentType("text/css");

    ThemeFont themeFont = ThemeFont.getThemeFont(c);
    LookAndFeelProperties laf = LookAndFeelProperties.getInstance(c);
    String navBarWidth = laf.getNavigationBarWidth();

    String link        = theme.getLinkColor();
    String text        = theme.getTextColor();
    String grid        = theme.getGridColor();
    String primary     = theme.getPrimaryBackgroundColor();
    String second      = theme.getSecondaryBackgroundColor();
    String borderTitle = theme.getBorderTitleColor();
    String webpart     = theme.getWebPartColor();
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

/* defaults, general attributes */
body, div, td, th, table, img, form
{
    color: #<%= text %>;
}

body, div, td, th, table, img, form,
.x-form-item, .x-panel-header, .x-btn button
{
    font-size: <%=themeFont.getNormalSize()%>;
}

a, a:visited, .labkey-link, .labkey-link:visited
{
    color: #<%= link %>;
}

table#header
{
    border-bottom: 10px solid #<%= link %>;
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

.labkey-announcement-title a
{
	color:#<%= link %>;
	font-size: 14px;
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

a.labkey-button, a.labkey-button:visited
{
    background-color: #<%= second %>;
    color: #<%= link %>;
}

a.labkey-button:hover
{
    background-color: #<%= second %>;
    color: #<%= text %>;
}

a.labkey-button:active
{
    background-color: #<%= second %>;
}

span.css-arrow-right
{
    border-left: 6px solid #<%= link %>;
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
{
    background-color:#<%= second %>;
}

table.labkey-app-bar td
{
    background-color:#<%= second %>;
}

td.labkey-app-button-bar-button a
{
    color:#<%= link %>;
}

.labkey-completion-highlight
{
    background-color: #<%= grid %>;
}

/* Used for full screen pages such as for login or upgrade */
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

/* There are some odd styles that are used so that the data
regions look the same in IE, Safari, and Firefox */
table.labkey-data-region
{
    background-color: #<%= second %>;
}

/* Used for the normal rows in data regions, sometimes these rows are not given a class */
.labkey-row td, .labkey-row th, tr.labkey-row
{
    background: #<%= second %>;
}

/* This is used for the headers on data regions that highlight on mouseover and give a drop down
of filter options when clicked */
th.labkey-col-header-filter, td.labkey-col-header-filter, tr.labkey-col-header-filter th
{
    color: #<%= link %>;
}

th.labkey-col-header-filter:hover, td.labkey-col-header-filter:hover, tr.labkey-col-header-filter th:hover
{
    background-color: #<%= grid %>;
}

/* Used for the unbordered empty or title cells sometimes put on tables */
.labkey-data-region .labkey-data-region-title
{
    background-color: #<%= second %>;
}

.labkey-data-region-header-container
{
    background-color: #<%= grid %>;
}

/* Used for blank cells in the data region such as when the main row headers have sub row headers */
td.labkey-blank-cell, th.labkey-blank-cell
{
    background: #<%= second %>;
}

.labkey-customview-item.x-view-over
{
    border-top: 1px solid #<%= link %>;
    border-bottom: 1px solid #<%= link %>;
}

/* Help pop ups */
#helpDiv
{
    background-color:#<%= second %>;
}

/* The table that is the entire page */
table.labkey-main
{
    background-color: #<%= primary %>;
}

/* The top panel with the title and logo */
#headerpanel
{
    background-color: #<%= second %>;
}

/* The side panel with the navigation and admin expandable menus */
td.labkey-site-nav-panel
{
    background-color: #<%= primary %>;
    border-right: 1px solid #<%= webpart %>;
}

td.labkey-main-menu
{
    background-color: #<%= grid %>;
}

td.labkey-main-menu a.selected
{
    background-color:#<%= second %>;
    color:#<%= text %>;
    border-bottom-color:#<%= second %>;
}

.labkey-webpart-menu
{
    background-color:#<%= second %>;
}

.labkey-search-filter A, .labkey-search-filter A:visited
{
    color: #<%= borderTitle %>;
}

.labkey-search-navresults H3
{
    color: #<%= borderTitle %>;
}

.labkey-search-results TD
{
    color: #<%= borderTitle %>;
}

/* The table that contains the expandable nav panels */
table.labkey-expandable-nav
{
    background-color: #<%= primary %>;
}

/* Each line of the expandable nav menus */
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

/* This is the table that contains each web part in the portal view */
table.labkey-wp
{
    width: 100%;
    border-spacing: 0; *border-collapse: collapse;
    border: 5px solid #<%= webpart %>;
}

/* This is the body of the web part */
td.labkey-wp-body
{
    width: 100%;
    padding: 10px;
    background: #<%= second %>;
}

/* This is used for the top strip on web parts, but is also used for headers elsewhere such as the
help pop up */
tr.labkey-wp-header
{
    background-color: #<%= webpart %>;
}

.labkey-wp-header th, .labkey-wp-header td, .labkey-wp-title
{
    color:#<%= text %>;
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

a.labkey-text-link, a.labkey-text-link:visited
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
    background: transparent;
}

/* YUI */
div.yuimenu h6, div.yuimenubar h6
{
    color:#<%= link %>;
}

div.yuimenu li a, div.yuimenubar li a
{
    color:#<%= link %>;
}

div.yuimenu li.selected, div.yuimenubar li.selected
{
    background-color:#<%= grid %>;
}

div.yuimenu li.selected a.selected,
div.yuimenu li.selected em.selected,
div.yuimenubar li.selected a.selected
{
    color:#<%= link %>;
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

/* This tab has a bottom border so it looks like it is back */
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
