<%
/*
 * Copyright (c) 2008-2009 LabKey Corporation
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
<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    Container c = getViewContext().getContainer();
    response.setContentType("text/css");
    WebTheme theme = WebThemeManager.getTheme(c);
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

    String contextPath = AppProps.getInstance().getContextPath();

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
body, div, td, th, table, img, form
{
    font-size: <%= themeFont.getNormalSize() %>;
}

input, .gwt-TextBox
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
    border: 1px solid #<%= navBorder %>;
    background-color: #<%= wpHeaderPanel %>;
}

fieldset
{
    border: 1px solid #<%= navBorder %>;
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

.labkey-form-label
{
    background-color: #<%= formLabel %>;
}

.labkey-form-label-nowrap
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
   border: solid 1px #<%= navBorder %>;
}

.labkey-bordered-heavy
{
   border-style: solid;
   border-width: 2px;
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

a.labkey-main-title
{
    font-size:<%= themeFont.getPageTitleSize() %>;
}

.labkey-site-nav-panel
{
    background: #<%= navBackground %>;
    border-top: 1px solid #<%= navBorder %>;
    border-right: 1px solid #<%= navBorder %>;
}

table.labkey-expandable-nav
{
    border-top: 1px solid #<%= navBorder %>;
    border-right: 1px solid #<%= navBorder %>;
    border-bottom: 1px solid #<%= navBorder %>;
    width: <%= navBarWidth %>px;
}

.labkey-expandable-nav-body
{
    border-top: 1px solid #<%= navBorder %>;
}

tr.labkey-nav-tree-row
{
    color: #<%= linkAndHeaderText %>;
}

tr.labkey-nav-tree-row:hover
{
    background: #<%= wpHeaderPanel %>;
}

td.labkey-nav-page-header
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
    border: 1px solid #<%= wpHeaderPanelBorder %>;
}

th.labkey-wp-title-left, td.labkey-wp-title-left
{
    border-right: 0px none;
}

th.labkey-wp-title-right, td.labkey-wp-title-right
{
    border-left:  0px none;
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

td.labkey-tab-selected
{
    border-top:solid 1px #<%= navBorder %>;
    border-left:solid 1px #<%= navBorder %>;
    border-right:solid 1px #<%= navBorder %>;
    color: #<%=linkAndHeaderText%>;
}

td.labkey-tab
{
    border-top:solid 1px #<%= navBorder %>;
    border-left:solid 1px #<%= navBorder %>;
    border-right:solid 1px #<%= navBorder %>;
    border-bottom:solid 1px #<%= navBorder %>;
    color: #<%=linkAndHeaderText%>;
}

td.labkey-tab-inactive
{
    border-top:solid 1px #<%= navBorder %>;
    border-left:solid 1px #<%= navBorder %>;
    border-right:solid 1px #<%= navBorder %>;
    border-bottom:solid 1px #<%= navBorder %>;
}

td.labkey-tab-space
{
    border-bottom: 1px solid #<%= navBorder %>;
}

td.labkey-tab-shaded
{
    background-color: #<%= navBackground %>;
}

td.labkey-tab-content
{
    border-left:solid 1px #<%= navBorder %>;
    border-right:solid 1px #<%= navBorder %>;
    border-bottom:solid 1px #<%= navBorder %>;
}

td.labkey-wiki-tab
{
    color: #000000;
}

td.labkey-wiki-tab-content
{
    border-left: 1px solid #<%=navBorder%>;
    border-right: 1px solid #<%=navBorder%>;
    border-bottom: 1px solid #<%=navBorder%>;
}

td.labkey-main-menu
{
    background-color:#<%=navBackground%>;
    border-top: 1px solid #<%=navBorder%>
    border-bottom: 1px solid #<%=navBorder%>
}

/* GWT */

.gwt-CheckBox {
  font-size: <%=themeFont.getNormalSize()%>;
}

.gwt-MenuBar .gwt-MenuItem {
  font-size: <%=themeFont.getNormalSize()%>;
}

.gwt-TabBar {
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
    border: 1px solid #<%= wpHeaderPanelBorder %>;
}

.gwt-DialogBox {
  sborder: 8px solid #<%=navBorder%>;
}

.gwt-HorizontalSplitter .Bar {
  background-color: #<%=navBorder%>;
}

.gwt-VerticalSplitter .Bar {
  background-color: #<%=navBorder%>;
}

.gwt-MenuBar {
  background-color: #<%=navBackground%>;
  border: 1px solid #<%=navBorder%>;
}

.gwt-MenuBar .gwt-MenuItem-selected {
  background-color: #<%=navBorder%>;
}

.gwt-TabPanelBottom {
  border-color: #<%=navBorder%>;
}

.gwt-TabBar .gwt-TabBarRest {
  border-color: #<%=navBorder%>;
}

.gwt-TabBar .gwt-TabBarItem {
  border-color: #<%=navBorder%>;
}

.gwt-TabBar .gwt-TabBarItem-selected {
  border-color: #<%=navBorder%>;
}

.gwt-StackPanel .gwt-StackPanelItem {
  background-color: #<%=navBackground%>;
}

/* yui */

div.yuimenu {
    border:solid 1px #<%=navBorder%>;
}

div.yuimenu h6,
div.yuimenubar h6 {
    border:solid 1px #<%=navBorder%>;
    color:#<%=linkAndHeaderText%>;

}

div.yuimenu ul {
    border:solid 1px #<%=navBorder%>;
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

.x-panel {
    border-style: solid;
    border-color: #d0d0d0;
}
.x-panel-header {
    color:#333;
	border:1px solid #99BBE8;
    background-image:none;
	background-color: #<%= wpHeaderPanel %>;
}

.x-panel-body {
    border-color:#d0d0d0;
}

.x-panel-bbar .x-toolbar {
    border-color:#d0d0d0;
}

.x-panel-tbar .x-toolbar {
    border-color:#d0d0d0;
}

.x-panel-tbar-noheader .x-toolbar, .x-panel-mc .x-panel-tbar .x-toolbar {
	border:none;
	border-bottom:1px solid #d0d0d0;
}
.x-panel-body-noheader, .x-panel-mc .x-panel-body {
    border-color:#d0d0d0;
}
.x-panel-tl .x-panel-header {
    color:#333;
}
.x-panel-tc {
	background-image:none;
}
.x-panel-tl {
	/*background-image:none;*/
    /*border-color:#d0d0d0;*/
	border:none;
}
.x-panel-tr {
	background-image:none;
}
.x-panel-bc {
	background-image:none;
}
.x-panel-bl {
	background-image:none;
}
.x-panel-br {
	background-image:none;
}
.x-panel-mc {
    background:#f1f1f1;
	padding:0;
}
.x-panel-mc .x-panel-body {
    background:transparent;
    border: 0 none;
}
.x-panel-ml {
	background-image:none;
}
.x-panel-mr {
	background-image:none;
}

/* grid */
.x-grid3-header {
	background-color: #ffffff;
	background-image:none;
	padding:0;
}

.x-grid3 table {
    border-collapse:collapse;
    border-spacing:0;
}

.x-grid3-header-inner {
	border-bottom:1px solid #D0D0D0;
}

.x-grid3-hd-inner {
font-weight:normal;
    color:#<%= linkAndHeaderText %>%>;
}

td.sort-desc, td.sort-asc, td.x-grid3-hd-menu-open {
	text-decoration: underline;
}

.x-grid-group-hd { border-color:#<%= fullScreenBorder %> ; }

.x-grid3-hd-row td {
	border-right:1px solid #AAAAAA;
}

td.x-grid3-hd-over, td.sort-desc, td.sort-asc, td.x-grid3-hd-menu-open {
	/*border-left:1px solid #EEE;*/
	border-left: none;
	border-right:1px solid #D0D0D0;
}

td.sort-desc .x-grid3-hd-inner, td.sort-asc .x-grid3-hd-inner {
	background-color: white;
	background-image:none;
}

td.x-grid3-hd-over .x-grid3-hd-inner, td.x-grid3-hd-menu-open .x-grid3-hd-inner {
	background-color:#D0D0D0;
	background-image:none;
}

.x-grid3-hd-btn {
	background:transparent url(<%= contextPath %>/gtheme/img/rdr.png) 0 -41px no-repeat;
	width:12px;
	margin-right:3px;
}

td a.x-grid3-hd-btn:hover {
	background-position: -12px -41px;
}

.x-menu {
    padding:0;
    background: white none;
    border-color: #<%= wpHeaderPanelBorder %>;
}
.x-menu-sep {margin:1px 1px 0 1px;}

li.x-menu-item-active {
	background:#E0ECFF none repeat scroll 0 0;
	border: none;
}

li.x-menu-list-item, li.x-menu-item-active { padding:0; }

li.x-menu-item-active a.x-menu-item {
	color:black;
}

.extContainer .x-menu-item-active {
    background:#EAEAEA repeat-x left bottom;
    border:1px solid #<%= wpHeaderPanelBorder %>;
    padding:0;
}

/* Tools */
.x-tool {
    background-image:url(<%= contextPath %>/gtheme/img/icons7.png);
}

.x-tool-close {			background-position: -40px -80px; }
.x-tool-close-over {		background-position: -40px -100px; }
.x-tool-maximize {		background-position:-120px -80px; }
.x-tool-maximize-over {	background-position:-120px -100px; }
.x-tool-minimize {		background-position:-20px -80px; }
.x-tool-minimize-over {	background-position:-20px -100px; }
.x-tool-restore {		background-position:0px -80px; }
.x-tool-restore-over {	background-position:0px -100px; }
.x-tool-toggle {			background-position:-70px -30px; height:10px; margin-top:1px; width:10px; }
.x-tool-toggle-over {	background-position:-70px -30px; }
.x-panel-collapsed .x-tool-toggle {	background-position:-60px -20px; }
.x-panel-collapsed .x-tool-toggle-over {	background-position:-60px -20px; }

.x-tab-scroller-left {
	background:white url(<%= contextPath %>/gtheme/img/icons7.png) no-repeat scroll -160px -76px;	border-bottom:1px solid white; }
.x-tab-scroller-left-over { background-position: -160px -96px; }
.x-tab-scroller-left-disabled {background:white;opacity:1;}

.x-tab-scroller-right {
	background:white url(<%= contextPath %>/gtheme/img/icons7.png) no-repeat scroll -139px -76px;	border-bottom:1px solid white;}
.x-tab-scroller-right-over { background-position: -139px -96px; }
.x-tab-scroller-right-disabled {background:white;opacity:1;}

/* Ghosting */
.x-panel-ghost {
    background:#e0e0e0;
}

.x-panel-ghost ul {
    border-color:#b0b0b0;
}

.x-grid-panel .x-panel-mc .x-panel-body {
    border:none;/*#d0d0d0;*/
}

/* Buttons */
/*
.x-btn {
	background:#ccc;
	border:1px solid #999;
}

.x-btn-left{
	background-image:none;
}
.x-btn-right{
	background-image:none;
}
.x-btn-center{
	background-image:none;
}
*/

.x-btn-with-menu .x-btn-center em {
    background:transparent url(<%= contextPath %>/gtheme/img/btn-arrow.gif) no-repeat scroll right 0
}
/* Layout classes */

.x-border-layout-ct {
    background:#f0f0f0;
}

.x-accordion-hd {
	background-image:url(../images/gray/panel/light-hd.gif);
}

.x-layout-collapsed{
    background-color:#eee;
    border-color:#e0e0e0;
}
.x-layout-collapsed-over{
	 background-color:#fbfbfb;
}


/* Toolbars */

.x-toolbar{
	border-color:#d0d0d0;
    background: #FFFFFF;
    border-width: 0;
}
.x-toolbar .x-btn {
	margin-right:2px;
}

.x-toolbar .x-btn-over button {
	color: black;
}

.x-toolbar .x-btn-center, .x-btn-center {
	background:transparent url(<%= contextPath %>/gtheme/img/btn.gif) repeat-x scroll center 0;}
.x-toolbar .x-btn-over .x-btn-center
, .x-panel-btns-ct .x-btn-over .x-btn-center
, .x-btn-over .x-btn-center {
	background:transparent url(<%= contextPath %>/gtheme/img/btn.gif) repeat-x scroll center -22px;}
.x-toolbar .x-btn-focus .x-btn-center
, .x-panel-btns-ct .x-btn-focus .x-btn-center
, .x-btn-focus .x-btn-center {
	background:transparent url(<%= contextPath %>/gtheme/img/btn.gif) repeat-x scroll center -44px;}
.x-toolbar .x-btn-pressed .x-btn-center
, .x-panel-btns-ct .x-btn-pressed .x-btn-center
, .x-btn-pressed .x-btn-center
, .x-btn-menu-active .x-btn-center {
	background:transparent url(<%= contextPath %>/gtheme/img/btn.gif) repeat-x scroll center -66px;}

.x-toolbar .x-btn-right, .x-btn-right {
	background:transparent url(<%= contextPath %>/gtheme/img/btn.gif) no-repeat scroll right 0;}
.x-toolbar .x-btn-over .x-btn-right
, .x-panel-btns-ct .x-btn-over .x-btn-right
, .x-btn-over .x-btn-right {
	background:transparent url(<%= contextPath %>/gtheme/img/btn.gif) no-repeat scroll right -22px;}
.x-toolbar .x-btn-focus .x-btn-right
, .x-panel-btns-ct .x-btn-focus .x-btn-right
, .x-btn-focus .x-btn-right {
	background:transparent url(<%= contextPath %>/gtheme/img/btn.gif) no-repeat scroll right -44px;}
.x-toolbar .x-btn-pressed .x-btn-right
, .x-panel-btns-ct .x-btn-pressed .x-btn-right
, .x-btn-pressed .x-btn-right
, .x-btn-menu-active .x-btn-right  {
	background:transparent url(<%= contextPath %>/gtheme/img/btn.gif) no-repeat scroll right -66px;}

.x-toolbar .x-btn-left, .x-btn-left {
	background:transparent url(<%= contextPath %>/gtheme/img/btn.gif) no-repeat scroll left 0;}
.x-toolbar .x-btn-over .x-btn-left
, .x-panel-btns-ct .x-btn-over .x-btn-left
, .x-btn-over .x-btn-left {
	background:transparent url(<%= contextPath %>/gtheme/img/btn.gif) no-repeat scroll left -22px;}
.x-toolbar .x-btn-focus .x-btn-left
, .x-panel-btns-ct .x-btn-focus .x-btn-left
, .x-btn-focus.x-btn-left {
	background:transparent url(<%= contextPath %>/gtheme/img/btn.gif) no-repeat scroll left -44px;}
.x-toolbar .x-btn-pressed .x-btn-left
, .x-panel-btns-ct .x-btn-pressed .x-btn-left
, .x-btn-pressed.x-btn-left
, .x-btn-menu-active .x-btn-left  {
	background:transparent url(<%= contextPath %>/gtheme/img/btn.gif) no-repeat scroll left -66px;}

/* Window */

.x-window-proxy {
    background:#e0e0e0;
    border-color:#b0b0b0;
}

body .x-window .x-window-header-text, body .x-panel-ghost .x-window-header-text {
    color:#fff;
    font-weight: normal;
    font-size: 13px;
}

/*********** X-panel *******/
.x-panel-tc {
	background: #<%= navBackground %>;
}
.x-panel-tl {
	background: #<%= navBackground %>;
}
.x-panel-tr {
	background: #<%= navBackground %>;
}
.x-panel-bc {
	background: #<%= navBackground %>;
}
.x-panel-bl {
	background: #<%= navBackground %>;
	padding-left:4px;
}
.x-panel-br {
	background: #<%= navBackground %>
	padding-right:4px;
}
.x-panel-mc {
    border:0px solid #d0d0d0;
    background:white;
}

.x-panel-ml {
	background: #<%= navBackground %>;
	padding-left:4px;
}
.x-panel-mr{
	background: #<%= navBackground %>;
	padding-right:4px;
}

/*********** X-tree ******/

.x-tree-node .x-tree-selected {
    background-color: <%= navBackground %>;
}

/*********** X-window ******/
.x-window-tc {
	background: #<%= fullScreenBorder %> ;
}
.x-window-tl {
	background: #<%= fullScreenBorder %>;
	padding-left: 4px !important;
}
.x-window-tr {
	background: #<%= fullScreenBorder %>;
	padding-right: 4px !important;
}
.x-window-bc{
	background: white;
}
.x-window-bl {
	background: #<%= fullScreenBorder %>;
	padding-left:4px !important;
}
.x-window-br {
	background: #<%= fullScreenBorder %>;
	padding-right:4px !important;
}
.x-window-mc {
    border:0px solid #d0d0d0;
    background:#e8e8e8;
}

.x-window-footer {
	border-bottom: #<%= fullScreenBorder %> solid 4px !important;
}

.x-window-ml {
	background: #<%= fullScreenBorder %> ;
	padding-left:4px !important;
}
.x-window-mr {
	background: #<%= fullScreenBorder %> ;
	padding-right:4px !important;
}

/* MBOH */

.x-panel-ghost .x-window-tl {
    border-color:#d0d0d0;
}
.x-panel-collapsed .x-window-tl {
    border-color:#d0d0d0;
}

.x-window-plain .x-window-mc {
    background: #e8e8e8;
    border-right:1px solid #eee;
    border-bottom:1px solid #eee;
    border-top:1px solid #d0d0d0;
    border-left:1px solid #d0d0d0;
	border:none;
}

.x-window-plain .x-window-body {
    border-left:1px solid #eee;
    border-top:1px solid #eee;
    border-bottom:1px solid #d0d0d0;
    border-right:1px solid #d0d0d0;
    background:transparent !important;
}

body.x-body-masked .x-window-mc, body.x-body-masked .x-window-plain .x-window-mc {
    background-color: white;
}

.x-window-maximized .x-window-tc {
	background: #<%= fullScreenBorder %>;
}


.x-panel-nofooter .x-panel-bc, .x-panel-nofooter .x-window-bc {
	height:4px;
	background: #<%= fullScreenBorder %>;
}

body .x-window .x-window-tl .x-window-header {
	padding: 3px 0;
}

/* misc */
.x-html-editor-wrap {
    border-color:#d0d0d0;
}

/* Borders go last for specificity */
.x-panel-noborder .x-panel-body-noborder {
    border-width:0;
}

.x-panel-noborder .x-panel-header-noborder {
    border-width:0;
    border-bottom:1px solid #d0d0d0;
}

.x-panel-noborder .x-panel-tbar-noborder .x-toolbar {
    border-width:0;
    border-bottom:1px solid #d0d0d0;
}

.x-panel-noborder .x-panel-bbar-noborder .x-toolbar {
    border-width:0;
    border-top:1px solid #d0d0d0;
}

.x-window-noborder .x-window-mc {
    border-width:0;
}
.x-window-plain .x-window-body-noborder {
    border-width:0;
}

.x-tab-panel-noborder .x-tab-panel-body-noborder {
	border-width:0;
}

.x-tab-panel-noborder .x-tab-panel-header-noborder {
	border-top-width:0;
	border-left-width:0;
	border-right-width:0;
}

.x-tab-panel-noborder .x-tab-panel-footer-noborder {
	border-bottom-width:0;
	border-left-width:0;
	border-right-width:0;
}

.x-tab-panel-body {
    border-color: #<%= wpHeaderPanelBorder %>;
}

.x-tab-panel-bbar-noborder .x-toolbar {
    border-width:0;
    border-top:1px solid #d0d0d0;
}

.x-tab-panel-tbar-noborder .x-toolbar {
    border-width:0;
    border-bottom:1px solid #d0d0d0;
}

.x-tab-panel-header, .x-tab-panel-footer {
	background-color: #<%= navBackground %>;
	border-color: #<%= wpHeaderPanelBorder %>;
	border-top: none;
	border-left: none;
	border-right: none;
	padding-bottom: 3px;
}

ul.x-tab-strip-top { background: #FFF none; border-bottom:none; padding-top:3px; }
ul.x-tab-strip li {margin-left:3px; }

.x-tab-strip span.x-tab-strip-text { color: #<%= linkAndHeaderText %>; }

.x-tab-strip-top .x-tab-right { background:#<%= navBackground %>  none;}
.x-tab-strip-top .x-tab-left {background:transparent none;}
.x-tab-strip-top .x-tab-strip-inner {background:transparent none;}

.x-tab-strip-top .x-tab-strip-active  .x-tab-right { background:#<%= navBackground %> none; color: white;}
.x-tab-strip-active span.x-tab-strip-text {color:black;}

/* SHADOWS */
/*
.x-shadow * {
	display:none;
	background: none;
	visibility: hidden;
}*/

/* GRID ROWS */

body .x-grid3 .x-grid3-row {
	border-top:0;
	padding-bottom: 0px;
	padding-top:1px;
}

body .x-grid3 .x-grid3-row-selected {
	padding:0;
    border: 1px solid #<%= fullScreenBorder %>;
    background: #<%= navBackground %>  !important;
}

body .x-grid3 .x-grid3-row-alt {
	background: #EEE;
}
/*
body {
	background: #ddd !important;
}*/

/* FORM */

.x-combo-list .x-combo-selected { border:none !important;}
.x-combo-list-item { border:none; }