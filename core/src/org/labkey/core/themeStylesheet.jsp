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
<%@ page import="org.apache.commons.lang.StringUtils" %>
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

.labkey-header.hover
{
    background: #<%= wpHeaderPanel %>;
}

.labkey-header-large.hover
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

span.labkey-help-pop-up
{
    font-size: <%= themeFont.getHeader_1Size() %>;
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
  border: 1px solid #<%= navBorder %>;
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
  border: 1px solid #<%=navBorder%>;
}

.gwt-TabBar .gwt-TabBarRest {
  border-left: 1px solid #<%=navBorder%>;
}

.gwt-TabBar .gwt-TabBarItem {
  border-top: 1px solid #<%=navBorder%>;
  border-left: 1px solid #<%=navBorder%>;
}

.gwt-TabBar .gwt-TabBarItem-selected {
  border-top: 1px solid #<%=navBorder%>;
  border-left: 1px solid #<%=navBorder%>;
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
