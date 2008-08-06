la<%
/*
 * Copyright (c) 2003-2008 Fred Hutchinson Cancer Research Center
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
<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page import="org.labkey.api.view.ThemeFont" %>
<%@ page import="org.labkey.api.view.WebTheme" %>
<%@ page import="java.util.List" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    // border-spacing hack since border-spacing is not recognized by IE
    private String cellSpacing(int i)
    {
        if (i == 0)
        {
            /* border-collapse: collapse not only collapses the borders, but also is the
             equivalent of setting border-spacing to 0 in IE.  Only IE uses the '*'
             attributes. The IE 'expression' function inserts cellspacing into the table tag,
             it has to be included to make sure precedence still works.
             */
            return "border-spacing: 0px; *border-collapse: collapse;" +
                "*border-spacing: expression(cellSpacing=0);";
        }
        else
        {
            /* separate border-collapse: separate and border-spacing are the css equivalent of
             cellspacing   */
            return "border-collapse: separate; border-spacing: " + i + "px; " +
                "*border-spacing: expression(cellSpacing=" + i + ");";
        }
    }
%>
<%
    WebTheme theme = org.labkey.api.view.WebTheme.getTheme();
    ThemeFont themeFont = org.labkey.api.view.ThemeFont.getThemeFont();
    List<ThemeFont> themeFonts = org.labkey.api.view.ThemeFont.getThemeFonts();
    response.setContentType("text/css");

    AppProps app = AppProps.getInstance();
    String navBarWidth = app.getNavigationBarWidth();
    String mapPath = request.getContextPath() + "/_yui/build/menu/assets/map.gif";

    //BLUE
    //Navigation Bar Color (left panel menu)    E1ECFC   light bluish-gray
    //Left Navigation Border Color              89A1B4  medium dark bluish-gray
    //Form Field Name Color                     FFDF8C  orangish
    //Full Screen Border Color                  336699  blue
    //Title Bar Background Color                EBF4FF   light bluish-gray
    //Title Bar Border Color                    89A1B4    medium dark bluish-gray

    String background          = "FFFFFF";
    String navBackground       = "E1ECFC";
    String navBorder           = "89A1B4";
    String linkAndHeaderText   = "003399";
    String formLabel           = "FFDF8C";
    String mainTitle           = "666666";
    String wpHeaderPanel       = "EBF4FF";
    String message             = "008000";
    String error               = "FF0000";
    String errorBG             = "FFC5AA";  
    String text                = "000000";
    String row                 = "FFFFFF";
    String alternateRow        = "EEEEEE";
    String highlightCell       = "AAAAAA";
    String gridBorder          = "DDDDDD";
    String gridBorderBold      = "AAAAAA";
    String fullScreenBorder    = "336699";
    String inactiveTab         = "444444";
    String linkRed             = "ff3300";
    String chartBorder         = "808080";
    String chartHeaderBold     = "CCCCCC";
    String peakWarningBG       = "FFF8DC"; // light orangish
    String statusBorder        = "FFAD6A";  // darker orangish

%>

<%-- index:

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

Deleted New Classes
Old Classes
--%>

<%-- defaults --%>

body, div, td, th, table, img, form
{
    font-family: verdana, arial, helvetica, sans-serif;
    font-size: <%= themeFont.getNormalSize() %>;
    border: 0px none;
    margin: 0px;
}

th
{
    font-weight: bold;
}

body, div, td, table, img, form
{
    font-weight: normal;
}

a, a:visited
{
    color: #<%=linkAndHeaderText%>;
    cursor: pointer;
    text-decoration: none;
}

a:hover, a:visited:hover
{
}

input, .gwt-TextBox
{
	font-size:<%= themeFont.getTextInputSize() %>;
}

select
{
	font-size:<%= themeFont.getTextInputSize() %>;
}

body, td, .gwt-Label
{
    color: #<%= text %>;
}

fieldset
{
    border: 1px solid #<%= navBorder %>;
    height: 5em;
    padding-left: 5px;
    padding-right: 5px;
    padding-bottom: 0;
}

legend
{
    border: 1px solid #<%= navBorder %>;
    padding: 2px 6px;
    background-color: #<%= wpHeaderPanel %>;
}

<%-- general --%>
td.labkey-header, .labkey-header
{
    color: #<%= linkAndHeaderText %>;
    text-align: left;
    text-decoration: none;
    vertical-align: top;
    padding: 1px;
    padding-right:4px;
}

td.labkey-header.hover, .labkey-header.hover
{
    background: #<%= wpHeaderPanel %>;
    cursor: pointer;
    cursor: hand;
}

td.labkey-header-large, .labkey-header-large
{
    color: #<%= text %>;
    text-align: left;
    text-decoration: none;
    vertical-align: top;
    padding: 1px;
    padding-right:4px;
    font-size: <%= themeFont.getHeader_1Size() %>;
    font-weight:bold;
}

td.labkey-header-large.hover, .labkey-header-large.hover
{
    background: #<%= wpHeaderPanel %>;
    cursor: pointer;
    cursor: hand;
}

td.labkey-announcement-title, .labkey-announcement-title
{
    padding-top: 14px;
    padding-bottom: 2px;
}

.labkey-announcement-title span, .labkey-announcement-title a
{
    font-weight: bold;
    color: #<%=linkAndHeaderText%>
}

td.labkey-title-area-line
{
    background-color: #<%= navBorder %>;
    height: 1px;
    padding: 0px;
}

.labkey-form-label, th.labkey-form-label, div.labkey-form-label, td.labkey-form-label
{
    background-color: #<%= formLabel %>;
    padding-right:4px;
    padding-left:4px;
    padding-top:1px;
    padding-bottom:1px;
}

td.labkey-form-label-nowrap {
	background-color: #<%= formLabel %>;
    padding: 4px;
    white-space: nowrap;
    vertical-align: top;
    text-align: right;
}

a.labkey-link, a.labkey-link:visited
{
}

a.labkey-link:hover, a.labkey-link:visited:hover
{
    color: #<%= linkRed %>;
    text-decoration: underline;
}

td.labkey-error, div.labkey-error, a.labkey-error, .labkey-error, .error
{
    color: #<%= error %>;
    padding: 5px;
}

td.labkey-message, div.labkey-message, .labkey-message
{
    font-size: <%= themeFont.getNormalSize() %>;
    color: #<%= message %>;
}

td.labkey-message-strong, div.labkey-message-strong, .labkey-message-strong
{
    font-size: <%= themeFont.getNormalSize() %>;
    font-weight: bold;
    color: #<%= message %>;
}

div.labkey-button-bar, table.labkey-button-bar
{
    white-space: nowrap;
    margin-top: 4px;
    margin-bottom: 2px;
}

.labkey-button-bar td
{
    padding-right: 5px;
}

span.labkey-button-bar-item
{
    margin-right: 5px;
}

.labkey-indented, td.labkey-indented
{
    padding-left: 2em;
}

li.labkey-indented
{
    padding-left: 1em;
    text-indent: -1em;
}

td.labkey-bordered, .labkey-bordered
{
   border: solid 1px #<%=chartBorder%>;
}

.labkey-disabled, .labkey-disabled a:link, .labkey-disabled a:visited
{
    color: #<%= alternateRow %>;
}

.labkey-strong
{
    font-weight: bold;
}

td.labkey-frame, tr.labkey-nav-frame, .labkey-nav-frame
{
    background: #<%= navBackground %>;
}

div.labkey-pagination
{
    white-space: nowrap;
    margin: 4px;
}

.labkey-pagination em
{
    font-weight: normal;
}

.labkey-selectors, td.labkey-selectors, th.labkey-selectors
{
    text-align: left;
}

table.labkey-tab-strip
{
    width: 100%;
    <%= cellSpacing(0) %>
}

.labkey-tab-strip td, .labkey-tab-strip th
{
    padding: 0px;
}

div.labkey-completion
{
    display:none;
    border: 1px solid #<%=gridBorderBold%>;
    padding:1px;
    position:absolute;
    background-color:#<%=background%>;
}

.labkey-completion table
{
    <%= cellSpacing(0) %>
}

.labkey-completion td
{
    padding: 0px;
}

table.labkey-completion-text
{
    width: 100%;
    <%= cellSpacing(0) %>
}

.labkey-completion-text td, .labkey-completion-text th
{
    padding: 0px;
}

.labkey-completion-highlight
{
    background-color: #<%= navBackground %>;
}

.labkey-completion-nohighlight
{
    background-color: #<%=background%>;
}

div.labkey-read-only, .labkey-read-only
{
    padding: 4px;
    vertical-align: top;
    text-align: left;
}

.labkey-help-pop-up
{
    font-weight: bold;
    font-size: <%= themeFont.getHeader_1Size() %>;
    text-decoration: none;
}

td.labkey-full-screen-table
{
    background-color: #<%= fullScreenBorder %>;
    padding: 30px;
    height: 100%;
    vertical-align: middle;
    text-align: center;
}

table.labkey-full-screen-table
{
    height: 100%;
    width: 100%;
    background-color: #<%=background%>;
    <%= cellSpacing(0) %>
}

td.labkey-full-screen-table-panel, .labkey-full-screen-table-panel
{
    background-color: #<%=gridBorder%>;
    height: 20px;
}

td.labkey-dialog-body, .labkey-dialog-body
{
    height: 100%;
    vertical-align: top;
    padding: 10px;
}

table.labkey-admin-console
{
    <%= cellSpacing(10) %>
}

.labkey-admin-console td
{
    vertical-align: top;
}

table.labkey-customize-view
{
    <%= cellSpacing(0) %>
    padding: 0px;
}

.labkey-customize-view table, .labkey-customize-view th
{
    padding: 0px;
}

.labkey-customize-view table
{
    <%= cellSpacing(0) %>
}

table.labkey-output
{
    width: 100%;
    <%= cellSpacing(0) %>
}

.labkey-output td, .labkey-output th
{
    padding: 1px;
}

<%-- data region --%>
table.labkey-data-region
{
    background-color: #<%=background%>;
    border-collapse: collapse;
    <%= cellSpacing(0) %>
    margin-bottom: 5px;
}

.labkey-data-region A
{
    color: #<%=linkAndHeaderText%>;
    text-decoration: none;
}

.labkey-data-region TH
{
    border-bottom: solid 1px #<%=gridBorderBold%>;
    vertical-align: top;
}

.labkey-data-region TD, .labkey-data-region TH
{
    padding: 1px;
    padding-right:4px;
}

table.labkey-show-borders
{
    border-right: solid 1px #<%=gridBorder%>;
    border-bottom: solid 1px #<%=gridBorder%>;
    *border: 0px none;
}

.labkey-show-borders td
{
    *border: solid 1px #<%=gridBorder%>;
}

.labkey-show-borders col
{
    border-left: solid 1px #<%=gridBorder%>;
}

.labkey-show-borders tr
{
    border-top: solid 1px #<%=gridBorder%>;
}

.labkey-show-borders th, .labkey-show-borders .labkey-col-total, .labkey-show-borders .labkey-row-total,
    .labkey-show-borders .labkey-col-header, .labkey-show-borders .labkey-row-header,
    .labkey-show-borders .labkey-col-total td, .labkey-show-borders .labkey-col-header th,
    .labkey-show-borders .labkey-stat-title
{
    border: solid 1px #<%=gridBorderBold%>;
}

<%-- These two classes are needed for IE if you have column or row totals so the border
     from the cells don't override the bottom row or last column.  IE doesn't allow borders
     to be set on rows or columns  --%>
.labkey-has-row-totals td
{
    *border-right: 0px none;
}

.labkey-has-col-totals td
{
    *border-bottom: 0px none;
}

.labkey-row td, .labkey-row th, .labkey-row
{
    background: #<%=row%>;
}

.labkey-alternate-row td, .labkey-alternate-row th, .labkey-alternate-row
{
    background: #<%=alternateRow%>;
}

th.labkey-col-header-filter, td.labkey-col-header-filter, .labkey-col-header-filter th
{
    color: #<%= linkAndHeaderText %>;
}

th.labkey-col-header-filter.hover, td.labkey-col-header-filter.hover, .labkey-col-header-filter th:hover
{
    background: #<%= wpHeaderPanel %>;
    cursor: pointer;
    cursor: hand;
}

.labkey-data-region .labkey-col-header, .labkey-data-region .labkey-row-header,
    .labkey-data-region .labkey-col-total, .labkey-data-region .labkey-row-total,
    .labkey-data-region .labkey-col-total td, .labkey-data-region .labkey-col-header th
{
    font-weight: bold;
    background-color: #<%=alternateRow%>;
}

.labkey-data-region .labkey-data-region-title, .labkey-data-region th.labkey-data-region-title
{
    background-color: #<%=background%>;
    border: 0px;
    border-top: hidden;
    border-left: hidden;
    font-weight: bold;
}

td.labkey-highlight-cell, th.labkey-highlight-cell
{
    background: #<%=highlightCell%>;
    font-weight:bold;
}

td.labkey-blank-cell, th.labkey-blank-cell
{
    background: #<%=background%>;
    border: 0px;
}

img.labkey-grid-filter-icon
{
    background-repeat: no-repeat;
    display: none;
    height: 8px;
    width: 11px;
    margin-left:2px;
    vertical-align: middle;
}

.labkey-filtered .labkey-grid-filter-icon {
    background-image: url(../_images/filter_on.gif);
    display: inline;
}

<%-- Home Template --%>

#bodyElement
{
    margin-top:0;
    margin-left:0;
    margin-right:0;
}

#helpDiv
{
    border:1px solid #<%=gridBorderBold%>;
    position:absolute;
    background-color:#<%=background%>;
}

#helpDiv table
{
    width: 100%;
    <%=cellSpacing(0)%>;
}

.labkey-main
{
    width: 100%;
    height: 100%;
    padding: 0px;
    <%= cellSpacing(0) %>
}

#labkey-header-panel
{
    height: 56px;
}

.labkey-header-panel table
{
    width: 100%;
    <%= cellSpacing(0) %>
}

.labkey-header-panel td, .labkey-header-panel th
{
    padding: 0px;
}

.labkey-site-nav-panel
{
    background: #<%= navBackground %>;
    border-top: 1px solid #<%= navBorder %>;
    border-right: 1px solid #<%= navBorder %>;
    <%= cellSpacing(2) %>
    vertical-align: top;
    padding: 0px;
}

.labkey-proj
{
    width: 100%;
    height: 100%;
    vertical-align: top;
    padding: 0px;
    <%= cellSpacing(0) %>
}

<%-- header-panel --%>
td.labkey-main-icon
{
    vertical-align: middle;
    height: 56px;
    margin-left: auto;
    margin-right: auto;
}

td.labkey-main-title-area
{
    vertical-align: bottom;
    padding: 5px;
    width: 100%;
}

.labkey-main-title-area span
{
    vertical-align: bottom;
}

#labkey-main-title
{
    color: #<%=mainTitle%>;
}

a.labkey-main-title
{
    font-family: arial, helvetica, sans-serif;
    font-size:<%= themeFont.getPageTitleSize() %>;
    vertical-align: bottom;
    color: #<%=mainTitle%>;
    text-decoration: none;
}

a.labkey-main-title:hover {
    text-decoration: underline;
}

td.labkey-main-nav
{
    padding: 5px;
    vertical-align: bottom;
    white-space: nowrap;
}

<%-- site-nav --%>

table.labkey-site-nav
{
    <%= cellSpacing(0) %>
}

.labkey-site-nav td, .labkey-site-nav th
{
    padding: 0px;
}

.labkey-expandable-nav-panel
{
    <%= cellSpacing(0) %>
}

.labkey-expandable-nav
{
    padding: 0px;
}

table.labkey-expandable-nav
{
    background-color: #<%=background%>;
    border-left: 0px;
    border-top: 1px solid #<%= navBorder %>;
    border-right: 1px solid #<%= navBorder %>;
    border-bottom: 1px solid #<%= navBorder %>;
    margin-top:5px;
    margin-right:5px;
    <%= cellSpacing(0) %>
    border-collapse: collapse;
    width: <%= navBarWidth %>px;
}

.labkey-expandable-nav tr
{
    width: 100%;
}

<%-- proj --%>

.labkey-proj-nav-panel
{
    width: 100%;
    vertical-align: top;
    padding: 0px;
}

.labkey-body-panel
{
    padding: 5px;
    width: 100%;
    height: 100%;
    vertical-align: top;
}

.labkey-side-panel
{
    padding: 5px;
    width: 240px;
    height: 100%;
    vertical-align: top;
}

<%-- proj-nav-panel --%>
.labkey-expandable-nav-body
{
    border-top: 1px solid #<%= navBorder %>;
    width: 100%;
    padding: 1px;
}

.labkey-expandable-nav-body table
{
    <%= cellSpacing(2) %>
}

.labkey-expandable-nav-body td, .labkey-expandable-nav-body th
{
    padding: 0px;
}

th.labkey-expand-collapse-area, .labkey-expand-collapse-area
{
    font-weight: bold;
    color: #<%=linkAndHeaderText%>;
    padding-right: 4px;
    padding-top: 2px;
    padding-bottom: 2px;
    white-space: nowrap;
    text-align: right;
}

<%-- nav-tree --%>

span.labkey-nav-tree-selected
{
    font-weight:bold;
}

tr.labkey-nav-tree-row
{
    color: #<%= linkAndHeaderText %>;
    text-decoration: none;
    vertical-align: top;
    padding-right:4px;
}

tr.labkey-nav-tree-row:hover
{
    background: #<%= wpHeaderPanel %>;
    cursor: pointer;
    cursor: hand;
}

td.labkey-nav-tree-node
{
    padding-top: 0.5em;
}

td.labkey-nav-tree-text
{
    padding: 0.3em;
    width: 100%;
}

td.labkey-nav-tree-total
{
    padding-top: 0.3em;
}

table.labkey-nav-tree-child
{
    border-collapse: collapse;
    width: 100%;
    <%= cellSpacing(0) %>
}

.labkey-nav-tree-child a
{
    padding: 0.1em;
}

.labkey-nav-tree-indenter
{
    width: 9px;
    src: <%= AppProps.getInstance().getContextPath()%>/_.gif;
}

<%--  proj-nav-panel --%>
table.labkey-nav-bar, .labkey-nav-bar
{
    width: 100%;
    <%= cellSpacing(0) %>
}

.labkey-nav-bar table
{
    <%= cellSpacing(0) %>
}

.labkey-nav-bar td, .labkey-nav-bar th
{
    padding: 0px;
}

td.labkey-nav-tab-selected, .labkey-nav-tab-selected
{
    padding-top:0.1em;
    padding-bottom:0.1em;
    padding-left:0.5em;
    padding-right:0.5em;
    border-top:solid 1px #<%= navBorder %>;
    border-left:solid 1px #<%= navBorder %>;
    border-right:solid 1px #<%= navBorder %>;
    border-bottom:solid 1px #ffffff;
    color: #<%=linkAndHeaderText%>;
    text-decoration: none;
    font-weight: bold;
}

td.labkey-nav-tab-inactive, .labkey-nav-tab-inactive
{
    padding-top:0.1em;
    padding-bottom:0.1em;
    padding-left:0.5em;
    padding-right:0.5em;
    border-top:solid 1px #<%= navBorder %>;
    border-left:solid 1px #<%= navBorder %>;
    border-right:solid 1px #<%= navBorder %>;
    border-bottom:solid 1px #<%= navBorder %>;
    text-decoration: none;
    color: #<%=inactiveTab%>;
}

td.labkey-nav-tab, .labkey-nav-tab
{
    padding-top:0.1em;
    padding-bottom:0.1em;
    padding-left:0.5em;
    padding-right:0.5em;
    border-top:solid 1px #<%= navBorder %>;
    border-left:solid 1px #<%= navBorder %>;
    border-right:solid 1px #<%= navBorder %>;
    border-bottom:solid 1px #<%= navBorder %>;
    text-decoration: none;
    vertical-align:bottom;
    color: #<%=linkAndHeaderText%>;
}

td.labkey-nav-tab-space, .labkey-nav-tab-space
{
    border-top: 0px;
    border-left: 0px;
    border-right: 0px;
    border-bottom: 1px solid #<%= navBorder %>;
}

#labkey-end-nav-tab-space
{
    padding-right: 5px;
    white-space: nowrap;
}

td.labkey-crumb-trail, .labkey-crumb-trail
{
    padding-top:5px;
    padding-left:5px;
}

td.labkey-nav-page-header
{
    padding-left: 12px;
}

span.labkey-nav-page-header, .labkey-nav-page-header
{
	font-weight: bold;
	font-size: <%= themeFont.getPageHeaderSize() %>;
	color: #<%=linkAndHeaderText%>;
}

<%-- body-panel --%>
table.labkey-wp
{
    width: 100%;
    <%= cellSpacing(0) %>
}

td.labkey-wp-body
{
    width: 100%;
    padding-top: 2px;
    background: #<%=background%>;
}

tr.labkey-wp-header
{
    background-color: #<%= wpHeaderPanel %>;
    font-weight: bold;
}

.labkey-wp-header th, .labkey-wp-header td
{
    border: 1px solid #<%= navBorder %>;
}

.labkey-wp-header th, .labkey-wp-header td, .labkey-wp-title, th.labkey-wp-title,
    td.labkey-wp-title, div.labkey-wp-title, .labkey-wp-title span
{
    font-weight: bold;
    color: #<%=linkAndHeaderText%>;
    padding-left: 6px;
    padding-right: 4px;
    padding-top: 2px;
    padding-bottom: 2px;
    text-align: left;
    white-space:nowrap;
}

.labkey-wp-header a:link, .labkey-wp-header a:visited
{
    color:#<%=linkAndHeaderText%>;
    text-decoration:none;
    cursor:pointer;
}

.labkey-wp-header a:hover
{
    color:#<%=linkRed%>;
    text-decoration:underline;
    cursor:pointer;
}

th.labkey-wp-title-left, td.labkey-wp-title-left
{
    border-right: 0px none;
}

th.labkey-wp-title-right, td.labkey-wp-title-right
{
    text-align:  right;
    border-left:  0px none;
}

table.labkey-wp-link-panel
{
    width: 100%;
}

img.labkey-wp-header
{
    vertical-align: center;
    opacity: 0.6;
    filter: alpha(opacity=60);
    -moz-opacity: 0.6;
    padding-top: 2px;
}

<%-- module specific css --%>

<%-- Announcement --%>

table.labkey-announcement-thread
{
    <%=cellSpacing(0)%>
}

.labkey-announcement-thread td
{
    padding: 3px;
}

<%-- Elispot --%>

<%-- Experiment --%>

table.labkey-protocol-applications
{
   <%= cellSpacing(5) %>
}

<%-- Flow --%>

<%-- Issue --%>

<%-- MS1 --%>

.labkey-peak-warning td, .labkey-peak-warning th
{
    padding: 2px;
    background-color:#<%=peakWarningBG%>;
    border:1px solid #<%=chartBorder%>;
}

td.labkey-ms1-filter
{
    background: #<%= navBackground %>;
    text-align: center;
    border-top: 1px solid #<%=gridBorderBold%>;
    border-bottom: 1px solid #<%=gridBorderBold%>;
}

<%-- MS2 --%>-

table.labkey-prot-annots
{
    <%= cellSpacing(10) %>
}

<%-- Nab --%>

<%-- Pipeline --%>

<%-- Query --%>

table.labkey-design-query
{
    <%= cellSpacing(0) %>
}

.labkey-design-query td, .labkey-design-query th
{
    padding: 0px;
}

<%-- Study --%>
table.labkey-study-expandable-nav
{
    <%= cellSpacing(0) %>
    width: 100%;
}

.labkey-study-expandable-nav td, .labkey-study-expandable-nav th
{
    padding-top: 0em;
    padding-bottom: 0em;
}

.labkey-study-expandable-nav .labkey-nav-tree-node
{
    padding-top: 0.3em;
}

table.labkey-request-warnings
{
    <%= cellSpacing(10) %>
}

table.labkey-manage-display
{
    <%= cellSpacing(5) %>
}

table.labkey-manage-default-reqs
{
    <%= cellSpacing(10) %>
}

.labkey-data-region .labkey-participant-view-header
{
    font-weight: bold;
    border: solid 1px #<%=chartBorder%>;
}

.labkey-data-region .labkey-expandable-row-header
{
    text-align: left;
    background-color: #<%=chartHeaderBold%>;
    border: solid 1px #<%=chartBorder%>;
    padding-left: 10px;
}

<%-- Wiki --%>

div.labkey-status-info, .labkey-status-info
{
    width: 99%;
    text-align: center;
    background-color: #<%=formLabel%>;
    border: 1px solid #<%=statusBorder%>;
    padding: 2px;
    font-weight: bold;
}

div.labkey-status-error, .labkey-status-error
{
    width: 99%;
    text-align: center;
    background-color: #<%=errorBG%>;
    border: 1px solid #<%=error%>;
    color: #<%=error%>;
    font-weight: bold;
}

table.labkey-tab-container
{
    <%= cellSpacing(0) %>
}

td.labkey-wiki-tab-active
{
    border-left: 1px solid #<%=navBorder%>;
    border-right: 1px solid #<%=navBorder%>;
    border-top: 1px solid #<%=navBorder%>;
    font-weight: bold;
    padding: 4px 8px 4px 8px;
    border-bottom: none;
    background-color: #<%=alternateRow%>;
    cursor: pointer;
}

td.labkey-wiki-tab-inactive
{
    border: 1px solid #<%=navBorder%>;
    font-weight: normal;
    background-color: #<%=navBackground%>;
    padding: 4px 8px 4px 8px;
    cursor: pointer;
}

td.labkey-wiki-tab-blank
{
    border-bottom: 1px solid #<%=navBorder%>;
    padding: 4px 8px 4px 8px;
}

td.labkey-wiki-tab-content
{
    border-left: 1px solid #<%=navBorder%>;
    border-right: 1px solid #<%=navBorder%>;
    border-bottom: 1px solid #<%=navBorder%>;
}

<%-- GWT --%>
.gwt-ToolTip {
	background-color: #<%= formLabel %>;
	font-family: verdana;
	font-size: <%= themeFont.getNormalSize() %>;
    padding-left: 4px;
    padding-right: 4px;
    padding-top: 2px;
    padding-bottom: 2px;
    border: solid 1px #<%=gridBorderBold%>;
}

.gwt-BorderedPanel {
}

.gwt-Button {
}

.gwt-Canvas {
}

.gwt-CheckBox {
  font-size: <%=themeFont.getNormalSize()%>;
}

.gwt-DialogBox {
  sborder: 8px solid #<%=navBorder%>;
  border: 2px outset;
  background-color: #<%=background%>;
}

.gwt-DialogBox .Caption {
  background-color: #<%=wpHeaderPanel%>;
  border: 1px solid #<%= navBorder %>;
  padding: 3px;
  margin: 2px;
  font-weight: bold;
  cursor: default;
}

.gwt-FileUpload {
}

.gwt-Frame {
}

.gwt-HorizontalSplitter .Bar {
  width: 8px;
  background-color: #<%=navBorder%>;
}

.gwt-VerticalSplitter .Bar {
  height: 8px;
  background-color: #<%=navBorder%>;
}

.gwt-MenuBar {
  background-color: #<%=navBackground%>;
  border: 1px solid #<%=navBorder%>;
  cursor: default;
}

.gwt-MenuBar .gwt-MenuItem {
  padding: 1px 4px 1px 4px;
  font-size: <%=themeFont.getNormalSize()%>;
  cursor: default;
}

.gwt-MenuBar .gwt-MenuItem-selected {
  background-color: #<%=navBorder%>;
}

.gwt-PushButton-up-disabled
{
	filter:alpha(opacity=50);
	-moz-opacity:0.5;
	-khtml-opacity: 0.5;
	opacity: 0.5;
}

.gwt-TabPanelBottom {
  border: 1px solid #<%=navBorder%>;
}

.gwt-TabBar {
  font-size: <%=themeFont.getNormalSize()%>;
}

.gwt-TabBar .gwt-TabBarFirst {
  padding-left: 3px;
}

.gwt-TabBar .gwt-TabBarRest {
  border-left: 1px solid #<%=navBorder%>;
  padding-right: 3px;
}

.gwt-TabBar .gwt-TabBarItem {
  border-top: 1px solid #<%=navBorder%>;
  border-left: 1px solid #<%=navBorder%>;
  padding: 2px;
  cursor: pointer;
  cursor: hand;
}

.gwt-TabBar .gwt-TabBarItem-selected {
  font-weight: bold;
  border-top: 1px solid #<%=navBorder%>;
  border-left: 1px solid #<%=navBorder%>;
  padding: 2px;
  cursor: default;
}

.gwt-Tree {
}

.gwt-Tree .gwt-TreeItem {
  font-size: <%=themeFont.getNormalSize()%>;
}

.gwt-Tree .gwt-TreeItem-selected {
  font-weight:bold;
}

.gwt-StackPanel {
}

.gwt-StackPanel .gwt-StackPanelItem {
  background-color: #<%=navBackground%>;
  cursor: pointer;
  cursor: hand;
}

.gwt-StackPanel .gwt-StackPanelItem-selected {
}

<%-- yui --%>

/*
Copyright (c) 2007, Yahoo! Inc. All rights reserved.
Code licensed under the BSD License:
http://developer.yahoo.net/yui/license.txt
version: 2.2.2
*/
/* Menu styles */

div.yuimenu {

    background-color:#<%=background%>;
    border:solid 1px #<%=navBorder%>;
    padding:1px;

}

/* Submenus are positioned absolute and hidden by default */

div.yuimenu div.yuimenu,
div.yuimenubar div.yuimenu {

    position:absolute;
    visibility:hidden;

}

/* MenuBar Styles */

div.yuimenubar {

    background-color:<%=alternateRow%>;

}

/*
    Applying a width triggers "haslayout" in IE so that the module's
    body clears its floated elements
*/
div.clear, div.yuimenubar div.bd {

    width:100%;

}

/*
    Clear the module body for other browsers
*/
div.clear:after, div.yuimenubar div.bd:after {

    content:'.';
    display:block;
    clear:both;
    visibility:hidden;
    height:0;

}

/* Matches the group title (H6) inside a Menu or MenuBar instance */

div.yuimenu h6,
div.yuimenubar h6 {

    font-size:100%;
    font-weight:normal;
    margin:0;
    border:solid 1px #<%=navBorder%>;
    color:#<%=linkAndHeaderText%>;

}

div.yuimenubar h6 {

    float:left;
    display:inline; /* Prevent margin doubling in IE */
    padding:4px 12px;
    border-width:0 1px 0 0;

}

div.yuimenu h6 {

    float:none;
    display:block;
    border-width:1px 0 0 0;
    padding:5px 10px 0 10px;

}

/* Matches the UL inside a Menu or MenuBar instance */

div.yuimenubar ul {

    list-style-type:none;
    margin:0;
    padding:0;

}

div.yuimenu ul {

    list-style-type:none;
    border:solid 1px #<%=navBorder%>;
    border-width:1px 0 0 0;
    margin:0;
    padding:4px 0;

}

div.yuimenu ul.first-of-type,
div.yuimenu ul.hastitle,
div.yuimenu h6.first-of-type {

    border-width:0;

}

/*
    Styles for the menu's header and footer elements that are used as controls
    to scroll the menu's body element when the menu's height exceeds the
    value of the "maxheight" configuration property.
*/

div.yuimenu div.topscrollbar,
div.yuimenu div.bottomscrollbar {

    height:16px;
    background-image:url(<%=mapPath%>);
    background-repeat:no-repeat;

}


div.yuimenu div.topscrollbar {

    background-image:url(<%=mapPath%>);
    background-position:center -72px;

}


div.yuimenu div.topscrollbar_disabled {

    background-image:url(<%=mapPath%>);
    background-position:center -88px;

}


div.yuimenu div.bottomscrollbar {

    background-image:url(<%=mapPath%>);
    background-position:center -104px;

}


div.yuimenu div.bottomscrollbar_disabled {

    background-image:url(<%=mapPath%>);
    background-position:center -120px;

}


/* MenuItem and MenuBarItem styles */

div.yuimenu li,
div.yuimenubar li {

//    font-size:85%;
    cursor:pointer;
    cursor:hand;
    white-space:nowrap;
    text-align:left;

}

div.yuimenu li.yuimenuitem {

    padding:2px 20px 2px 2px;

}

div.yuimenu li li,
div.yuimenubar li li {

    font-size:100%;

}


/* Matches the help text for a menu item */

div.yuimenu li.hashelptext em.helptext {

    font-style:normal;
    margin:0 0 0 40px;

}

div.yuimenu li a,
div.yuimenubar li a {

    /*
        "zoom:1" triggers "haslayout" in IE to ensure that the mouseover and
        mouseout events bubble to the parent LI in IE.
    */
    zoom:1;
    color:#<%=linkAndHeaderText%>;
    text-decoration:none;

}

div.yuimenu li.hassubmenu,
div.yuimenu li.hashelptext {

    text-align:right;

}

div.yuimenu li.hassubmenu a.hassubmenu,
div.yuimenu li.hashelptext a.hashelptext {

    /*
        Need to apply float immediately for IE or help text will jump to the
        next line
    */

    *float:left;
    *display:inline; /* Prevent margin doubling in IE */
    text-align:left;

}

div.yuimenu.visible li.hassubmenu a.hassubmenu,
div.yuimenu.visible li.hashelptext a.hashelptext {

    /*
        Apply the float only when the menu is visible to prevent the help
        text from wrapping to the next line in Opera.
    */

    float:left;

}


/* Matches selected menu items */

div.yuimenu li.selected,
div.yuimenubar li.selected {

    background-color:#<%=navBackground%>;

}

div.yuimenu li.selected a.selected,
div.yuimenubar li.selected a.selected {

    text-decoration:underline;

}

div.yuimenu li.selected a.selected,
div.yuimenu li.selected em.selected,
div.yuimenubar li.selected a.selected {

    color:#<%=linkAndHeaderText%>;

}


/* Matches disabled menu items */

div.yuimenu li.disabled,
div.yuimenubar li.disabled {

    cursor:default;

}

div.yuimenu li.disabled a.disabled,
div.yuimenu li.disabled em.disabled,
div.yuimenubar li.disabled a.disabled {

    color:#b9b9b9;
    cursor:default;

}

div.yuimenubar li.yuimenubaritem {

    float:left;
    display:inline; /* Prevent margin doubling in IE */
    border-width:0 0 0 1px;
    border-style:solid;
    border-color:#<%=navBorder%>;
    padding:4px 24px;
    margin:0;

}

div.yuimenubar li.yuimenubaritem.first-of-type {

    border-width:0;

}


/* Styles for the the submenu indicator for menu items */

div.yuimenu li.hassubmenu em.submenuindicator,
div.yuimenubar li.hassubmenu em.submenuindicator {

    display:-moz-inline-box; /* Mozilla */
    display:inline-block; /* IE, Opera and Safari */
    vertical-align:middle;
    height:8px;
    width:8px;
    text-indent:9px;
    font:0/0 arial;
    overflow:hidden;
    background-image:url(<%=mapPath%>);
    background-repeat:no-repeat;

}

div.yuimenubar li.hassubmenu em.submenuindicator {

    background-position:0 -24px;
    margin:0 0 0 10px;

}

div.yuimenubar li.hassubmenu em.submenuindicator.selected {

    background-position:0 -32px;

}

div.yuimenubar li.hassubmenu em.submenuindicator.disabled {

    background-position:0 -40px;

}

div.yuimenu li.hassubmenu em.submenuindicator {

    background-position:0 0;
    margin:0 -16px 0 10px;

}

div.yuimenu li.hassubmenu em.submenuindicator.selected {

    background-position:0 -8px;

}

div.yuimenu li.hassubmenu em.submenuindicator.disabled {

    background-position:0 -16px;

}


/* Styles for a menu item's "checked" state */

div.yuimenu li.checked {

    position:relative;

}

div.yuimenu li.checked em.checkedindicator {

    height:8px;
    width:8px;
    text-indent:9px;
    overflow:hidden;
    background-image:url(<%=mapPath%>);
    background-position:0 -48px;
    background-repeat:no-repeat;
    position:absolute;
    left:6px;
    _left:-16px; /* Underscore hack b/c this is for IE 6 only */
    top:.5em;

}

div.yuimenu li.checked em.checkedindicator.selected {

    background-position:0 -56px;

}

div.yuimenu li.checked em.checkedindicator.disabled {

    background-position:0 -64px;

}







<%--  Deleted classes

table.labkey-grid
{
    border: 1px solid #<%=gridBorderBold%>;
    border-collapse: collapse;
    <%= cellSpacing(0) %>
}

table.labkey-grid-read-only {
}
.labkey-grid td {
    padding-left: .5em;
    padding-right: .5em;
    padding-top: .1em;
    padding-bottom: .1em;
    border-left: 1px solid #<%=gridBorder%>;
    border-right: 1px solid #<%=gridBorder%>;
    border-bottom: 1px solid #<%=gridBorder%>;
    width: auto;
}

.labkey-grid input {
}

.labkey-grid-read-only td {
    padding-left: .5em;
    padding-right: .5em;
    padding-top: .1em;
    padding-bottom: .1em;
}

<%-- alternating grid
table.labkey-alternating-grid
{
    border-bottom: solid 2px #<%=chartBorder%>;
    <%= cellSpacing(3) %>
    border-collapse: collapse;
}

.labkey-alternating-grid td, .labkey-alternating-grid th
{
    padding: 3px;
}

th.labkey-alternating-grid-header, td.labkey-alternating-grid-header, .labkey-alternating-grid-header
{
    font-weight: bold;
    border-right:solid 1px #<%=chartBorder%>;
    border-left:solid 1px #<%=chartBorder%>;
    border-top:solid 2px #<%=chartBorder%>;
}

td.labkey-alternating-grid-cell, th.labkey-alternating-grid-cell
{
    border-right:solid 1px #<%=chartBorder%>;
    border-left:solid 1px #<%=chartBorder%>;
}
--%>

<%--
td.labkey-show-column-separators
{
    border: solid 1px #<%=gridBorderBold%>;
}

th.labkey-show-column-separators
{
    border-right: solid 1px #<%=gridBorderBold%>;
    border-bottom: solid 1px #<%=gridBorderBold%>;
}

table.labkey-show-column-separators
{
    border-top: solid 1px #<%=gridBorderBold%>;
    border-left: solid 1px #<%=gridBorderBold%>;
    border-bottom: solid 1px #<%=gridBorderBold%>;
}

td.labkey-show-header-separator, table.labkey-show-header-separator, th.labkey-show-header-separator,
    .labkey-show-header-separator
{
    border-bottom: solid 1px #<%=gridBorderBold%>;
}

table.labkey-show-header-separator
{
    <%= cellSpacing(0) %>
    border: 0px;
}
--%>

<%-- Announcement -%>

table.labkey-announcements
{
    width: 100%;
}

.labkey-announcements td, .labkey-announcements th
{
    padding: 0px;
}

table.labkey-admin-broadcast-checkbox-area
{
    width: 100%;
    <%= cellSpacing(0) %>
}

.labkey-admin-broadcast-checkbox-area td, .labkey-admin-broadcast-checkbox-area th
{
    padding: 0px;
}

table.labkey-file-picker
{
    width: 100%;
    <%= cellSpacing(0) %>
}

.labkey-file-picker td, .labkey-file-picker th
{
    padding: 0px;
}

td.labkey-announcement-title
{
    padding-top: 14px;
    padding-bottom: 2px;
}

.labkey-announcement-title span, .labkey-announcement-title a {
    font-weight: bold;
    color: #<%=linkAndHeaderText%>
}

table.labkey-announcement-thread
{
    width: 100%;
    <%= cellSpacing(0) %>
}

.labkey-announcement-thread td, .labkey-announcement-thread th
{
    padding: 2px;
}

table.labkey-bulk-edit
{
    border:solid 1px #<%=gridBorderBold%>;
    <%= cellSpacing(0) %>
}

.labkey-bulk-edit th
{
    border-bottom:solid 1px #<%=gridBorderBold%>;
    color: #<%= linkAndHeaderText %>;
    text-align: left;
    text-decoration: none;
    vertical-align: top;
    padding: 1px;
    padding-right:4px;
}

.labkey-bulk-edit a:hover
{
    background: #<%= wpHeaderPanel %>;
    cursor: pointer;
    cursor: hand;
}

.labkey-bulk-edit td, .labkey-bulk-edit th
{
    border-right:solid 1px #<%=gridBorderBold%>;
}

table.labkey-daily-digest
{
    width: 100%;
    <%= cellSpacing(0) %>
}

.labkey-daily-digest td, .labkey-daily-digest th
{
    padding: 4px;
}

table.labkey-email-notification
{
    width: 100%;
    <%= cellSpacing(0) %>
}

.labkey-email-notification td, .labkey-email-notification th
{
    padding: 4px;
}

<%-- Elispot -%>

.labkey-plate-summary td, .labkey-plate-summary th
{
    padding: 2px;
}

<%-- Experiment -%>

table.labkey-protocol-applications
{
   <%= cellSpacing(5) %>
}

<%-- Flow -%>

table.labkey-show-compensation
{
    <%= cellSpacing(0) %>
}

.labkey-show-compensation td, .labkey-show-compensation th
{
    padding: 2px;
}

<%-- Issue -%>

table.labkey-issue-keyword-view
{
    <%= cellSpacing(4) %>
}

.labkey-issue-keyword-view td, .labkey-issue-keyword-view th
{
    padding: 0px;
}

table.labkey-issue-required-view
{
}

.labkey-issue-required-view td, .labkey-issue-required-view th
{
    padding: 0px;
}

table.labkey-customize-columns
{
    <%= cellSpacing(4) %>
}

.labkey-customize-columns td, .labkey-customize-columns th
{
    padding: 0px;
}

.labkey-issue-jump td, .labkey-issue-jump th
{
    padding: 2px;
}

table.labkey-issue-keyword-picker
{
    <%= cellSpacing(4) %>
}

.labkey-issue-keyword-picker td, .labkey-issue-keyword-picker th
{
    padding: 0px;
}

<%-- MS1 -%>

table.labkey-peak-warning
{
    width: 100%;
}

.labkey-peak-warning td, .labkey-peak-warning th
{
    padding: 2px;
    background-color:<%=peakWarningBG%>;
    border:1px solid <%=chartBorder%>;
}

table.labkey-feature-detail
{
    <%= cellSpacing(0) %>
}

.labkey-feature-detail td, .labkey-feature-detail th
{
    padding: 4px;
}

table.labkey-feature-data
{
    <%= cellSpacing(0) %>
}

.labkey-feature-data td, .labkey-feature-data th
{
    padding: 2px;
}

td.labkey-feature-caption
{
    background-color: #<%=alternateRow%>;
}

td.labkey-mz-filter
{
    background: #<%= navBackground %>;
    text-align: center;
    border-top: 1px solid #<%=gridBorderBold%>;
    border-bottom: 1px solid #<%=gridBorderBold%>;
}

table.labkey-mz-filter
{
    <%= cellSpacing(0) %>
    width: 100%;
}

.labkey-mz-filter td, .labkey-mz-filter th
{
    padding: 4px;
}

td.labkey-scan-filter
{
    background: #<%= navBackground %>;
    display: none;
    text-align: center;
    vertical-align: top
}

table.labkey-scan-filter
{
    <%= cellSpacing(0) %>
}

.labkey-scan-filter td, .labkey-scan-filter th
{
    padding: 2px;
}

table.labkey-file-detail
{
    <%= cellSpacing(0) %>
}

.labkey-file-detail td, .labkey-file-detail th
{
    padding: 2px;
}

table.labkey-ms1-search
{
    <%= cellSpacing(4) %>
}

<%-- MS2 -%>



td.labkey-list-box, .labkey-list-box
{
    font-weight: bold;
}

table.labkey-prot-annots
{
    <%= cellSpacing(10) %>
}

table.labkey-protein-search
{
    <%= cellSpacing(4) %>
}

table.labkey-filter-header
{
    <%= cellSpacing(0) %>
}

table.labkey-show-peptide
{
    <%= cellSpacing(1) %>
    width: 230;
}

.labkey-show-peptide td, .labkey-show-peptide th
{
    padding: 1px;
}

<%-- Nab -%>

table.labkey-nab-run-label
{
    background-color: <%=nabLabel%>;
}

table.labkey-nab-run
{
    <%= cellSpacing(0) %>
}

.labkey-nab-run td, .labkey-nab-run th
{
    padding: 3px;
}

<%-- Pipeline -%>

table.labkey-pipeline-setup
{
    <%= cellSpacing(0) %>
    width: 100%;
    height: 120px;
}

.labkey-pipeline-setup td, .labkey-pipeline-setup th
{
    padding: 0px;
}

table.labkey-directory-tree
{
    <%= cellSpacing(0) %>
    width: 240px;
}

.labkey-directory-tree td, .labkey-directory-tree th
{
    padding: 2px;
}

table.labkey-current-directory
{
    <%= cellSpacing(0) %>
    width: 100%;
}

.labkey-current-directory td, .labkey-current-directory th
{
    padding: 2px;
}

table.labkey-analyze
{
    <%= cellSpacing(0) %>
    width: 100%;
}

.labkey-current-directory td, .labkey-current-directory th
{
    padding: 2px;
}


<%-- Query -%>

.labkey-manage-view td, .labkey-manage-view th
{
    padding: 0px;
}

table.labkey-design-query
{
    <%= cellSpacing(0) %>
}

.labkey-design-query td, .labkey-design-query th
{
    padding: 0px;
}


<%-- Study -%>

.labkey-manage-report td, .labkey-manage-report th
{
    padding: 0px;
}

table.labkey-specimen-visit-report
{
    <%= cellSpacing(0) %>
}

.labkey-specimen-visit-report td, .labkey-specimen-visit-report th
{
    padding: 2px;
}

table.labkey-request-warnings
{
    <%= cellSpacing(10) %>
}

table.labkey-manage-display
{
    <%= cellSpacing(5) %>
}

table.labkey-manage-default-reqs
{
    <%= cellSpacing(10) %>
}

table.labkey-requirements
{
    <%= cellSpacing(0) %>
}

.labkey-requirements td, .labkey-requirements th
{
    padding: 3px;
}

td.labkey-requirement-cell, th.labkey-requirement-cell
{
    border-bottom: solid 1px <%=chartBorder%>;
}

tr.labkey-requirement-row
{
    border-bottom:solid 1px <%=chartBorder%>;
    border-top:solid 1px <%=chartBorder%>;
}

table.labkey-lab-specimen-list
{
    <%= cellSpacing(0) %>
}

.labkey-lab-specimen-list td, .labkey-lab-specimen-list th
{
    padding: 4px;
}

table.labkey-import-specimens
{
    <%= cellSpacing(0) %>
}

table.labkey-report-parameters
{
    <%= cellSpacing(0) %>
}

.labkey-report-parameters td, .labkey-report-parameters th
{
    padding: 2px;
}

table.labkey-auto-report-list
{
    <%= cellSpacing(0) %>
}

.labkey-auto-report-list td, .labkey-auto-report-list th
{
    padding: 3px;
}

table.labkey-save-report
{
    <%= cellSpacing(0) %>
}

.labkey-save-report td, .labkey-save-report th
{
    padding: 2px;
}

table.labkey-participant-view
{
    <%= cellSpacing(0) %>
    border-bottom: solid 2px <%=chartBorder%>;
}

.labkey-participant-view td, .labkey-participant-view th
{
    padding: 2px;
}

table.labkey-manage-study
{
    <%= cellSpacing(3) %>
}

table.labkey-type-def
{
    <%= cellSpacing(1) %>
}

.labkey-type-def table
{
    <%= cellSpacing(0) %>
    width: 100%;
}

.labkey-type-def td, .labkey-type-def th
{
    padding: 2px;
}

table.labkey-dataset-charts
{
    <%= cellSpacing(0) %>
}

.labkey-dataset-charts td, .labkey-dataset-charts th
{
    padding: 2px;
}

table.labkey-create-study
{
    <%= cellSpacing(3) %>
}

table.labkey-bulk-import-data
{
    <%= cellSpacing(0) %>
}

.labkey-bulk-import-data td, .labkey-bulk-import-data th,
.labkey-bulk-import-data-header td, .labkey-bulk-import-data-header th
{
    padding: 2px;
}

table.labkey-crosstab-report
{
    <%= cellSpacing(0) %>
    border-collapse:collapse;
}

table.labkey-dataset-security
{
    <%= cellSpacing(0) %>
}

table.labkey-plate-template
{
    <%= cellSpacing(2) %>
}

table.labkey-plate-template-colors
{
    <%= cellSpacing(0) %>
}

.labkey-plate-template-colors td, .labkey-plate-template-colors th
{
    padding: 3px;
}

table.labkey-simple-html
{
    <%= cellSpacing(0) %>
}

.labkey-simple-html td, .labkey-simple-html th
{
    padding: 2px;
}

table.labkey-type-list-inner-html
{
    <%= cellSpacing(0) %>
    width: 100%;
}

.labkey-type-list-inner-html td, .labkey-type-list-inner-html th
{
    padding: 0px;
}

table.labkey-dataset-security
{
    <%= cellSpacing(0) %>
}

.labkey-study-overview
{
}

table.labkey-study-datasets
{
    width: 100%;
    <%= cellSpacing(3) %>
}

.labkey-study-datasets td
{
    padding: 2px;
}

.labkey-study-datasets th
{
    background-color: <%=gridBorderBold%>;
    text-align: left;
}

<%-- Wiki -%>



table.labkey-wiki-version
{
    width: 100%;
}

.labkey-wiki-version td, .labkey-wiki-version th
{
    padding: 0px;
}

table.labkey-wiki-search
{
    <%= cellSpacing(0) %>
    width: 100%;
}

.labkey-wiki-search td, .labkey-wiki-search th
{
    padding: 2px;
}

table.labkey-wiki-edit
{
    width: 99%;
}

div.labkey-status-info, .labkey-status-info
{
    width: 99%;
    text-align: center;
    background-color: <%=formLabel%>;
    border: 1px solid <%=statusBorder%>;
    padding: 2px;
    font-weight: bold;
}

div.labkey-status-error, .labkey-status-error
{
    width: 99%;
    text-align: center;
    background-color: <%=statusErrorBG%>;
    border: 1px solid <%=statusErrorBorder%>;
    color: white;
    font-weight: bold;
}

table.labkey-form-layout
{
    width: 99%;
}

textarea.labkey-stretch-input, .labkey-stretch-input
{
    width: 100%;
}

table.labkey-tab-container
{
    width: 99%;
    <%= cellSpacing(0) %>
}

td.labkey-wiki-tab-active
{
    border-left: 1px solid <%=navBorder%>;
    border-right: 1px solid <%=navBorder%>;
    border-top: 1px solid <%=navBorder%>;
    font-weight: bold;
    padding: 4px 8px 4px 8px;
    border-bottom: none;
    background-color: <%=lightBlue%>;
    cursor: pointer;
}

td.labkey-wiki-tab-inactive
{
    border: 1px solid <%=navBorder%>;
    font-weight: normal;
    background-color: <%=darkerLightBlue%>;
    padding: 4px 8px 4px 8px;
    cursor: pointer;
}

td.labkey-wiki-tab-blank
{
    border-bottom: 1px solid <%=navBorder%>;
    padding: 4px 8px 4px 8px;
}

td.labkey-wiki-tab-content
{
    border-left: 1px solid <%=navBorder%>;
    border-right: 1px solid <%=navBorder%>;
    border-bottom: 1px solid <%=navBorder%>;
}

td.labkey-wiki-field-content
{
    width: 99%;
}

span.labkey-command-link
{
    cursor: pointer;
    color: #<%=WebTheme.toRGB(org.labkey.api.view.WebTheme.getTheme().getTitleColor())%>;
    text-decoration:none;
}

table.labkey-wiki-front-page
{
    width: 100%;
}

.labkey-wiki-front-page td, .labkey-wiki-front-page th
{
    padding: 0px;
}

table.labkey-wiki-nav-tree
{
    width: 100%;
}

.labkey-wiki-nav-tree td, .labkey-wiki-nav-tree th
{
    padding: 0px;
}

.labkey-wiki-page-format td, .labkey-wiki-page-format th
{
    padding: 2px;
}

.labkey-wiki-customize td, .labkey-wiki-customize th
{
    padding: 2px;
}

--%>





<%--

OLD STYLES




body, td, .gwt-Label
    {
    font-family: verdana, arial, helvetica, sans-serif;
    color: black;
    }


reuse
.normal, .normal td, .normal th, .wiki, .wiki-table td, .wiki-table th
{
    font-family: verdana, arial, helvetica, sans-serif;
    font-size: <%= themeFont.getNormalSize() %>;
}

.ms-nav {
    font-size: <%= themeFont.getNormalSize() %>;
}
.ms-nav td, .ms-nav .ms-input {
    background: #<%= navBackground %>;
    font-family: Verdana, sans-serif;
    font-size: <%= themeFont.getTextInputSize() %>;
}
.ms-nav th {
    font-size: <%= themeFont.getNormalSize() %>;
    font-family: Verdana, sans-serif;
    font-weight: normal;
    text-align: left;
    color: black;
    background: #<%= navBackground %>;
}
.ms-nav a {
    text-decoration: none;
    font-family: Verdana, sans-serif;
    font-size: <%= themeFont.getNormalSize() %>;
    font-weight: normal;
    color: #<%=linkAndHeaderText%>;
}
.ms-nav a:link {
}
.ms-nav a:hover {
    text-decoration: underline;
    color: #798073;
}
.ms-nav a:visited {
   color: #<%=linkAndHeaderText%>;
}


.leftNavBox
{
    background-color: white;
    border-collapse: collapse;
    border-left: 0px;
    border-top: 1px solid #<%= navBorder %>;
    border-right: 1px solid #<%= navBorder %>;
    border-bottom: 1px solid #<%= navBorder %>;
    padding: 1px;
    margin-top:5px;
    margin-right:5px;
    padding: 0px;
    width: <%= navBarWidth %>px;
}

.leftNavBoxBody
{
    border-top: 1px solid #<%= navBorder %>;
}

.ms-titlearealine{
    background-color: #<%= navBorder %>;
}
.ms-nav {
    font-size: <%= themeFont.getNormalSize() %>;
}
.ms-nav td, .ms-nav .ms-input {
    background: #<%= navBackground %>;
    font-family: Verdana, sans-serif;
    font-size: <%= themeFont.getTextInputSize() %>;
}
.ms-nav th {
    font-size: <%= themeFont.getNormalSize() %>;
    font-family: Verdana, sans-serif;
    font-weight: normal;
    text-align: left;
    color: black;
    background: #<%= navBackground %>;
}
.ms-navheader, .ms-navheader A, .ms-navheader A:link, .ms-navheader A:visited {
    font-weight: bold;
	color: #<%=linkAndHeaderText%>;
}
.ms-navframe {
    background: #<%= navBackground %>;
    border-right: none;
    border-left: 0px;
}
.ms-searchform {
	background-color: #<%= formLabel %>;
	font-family: verdana;
	font-size: <%= themeFont.getNormalSize() %>;
    padding-right:4px;
    padding-left:4px;
}
.ms-strong
{
    font-weight: bold;
}
.ms-searchform-nowrap {
	background-color: #<%= formLabel %>;
	font-family: verdana;
	font-size: <%= themeFont.getNormalSize() %>;
    padding-right:4px;
    padding-left:4px;
    padding-top:4px;
    padding-bottom:4px;
    white-space: nowrap;
    vertical-align: top;
    text-align: right;
}
.ms-readonly {
	font-family: verdana;
	font-size: <%= themeFont.getNormalSize() %>;
    padding-right:4px;
    padding-left:4px;
    padding-top:4px;
    padding-bottom:4px;
    vertical-align: top;
    text-align: left;
}
.ms-top {
    font-size: <%= themeFont.getNormalSize() %>;
    font-family: verdana;
    vertical-align: top;
}

.ms-top-color {
    background-color: #<%= formLabel %>;
    font-size: <%= themeFont.getNormalSize() %>;
    font-family: verdana;
    vertical-align: top;
    text-align: right;
}
.ms-nav a {
    text-decoration: none;
    font-family: Verdana, sans-serif;
    font-size: <%= themeFont.getNormalSize() %>;
    font-weight: normal;
    color: #<%=linkAndHeaderText%>;
}
.ms-nav a:link {
}
<%--.ms-nav a:hover {
    text-decoration: underline;
    color: #798073;
}
.ms-nav a:visited {
   color: #<%=linkAndHeaderText%>;
} -%>
.ms-announcementtitle, .ms-announcementtitle a {
    font-weight: bold;
    color: #<%=linkAndHeaderText%>
}
.ms-pagetitle {
    color: black;
    font-family: arial;
    font-size: <%= themeFont.getPageTitleSize() %>;
    font-weight: normal;
}
.ms-pagetitle a {
    text-decoration:none;
    color: black;
}
.ms-pagetitle a:hover {
    text-decoration: underline;
}
.ms-tabselected
{
   font-family: verdana;
   background-color: #ffd275;
   border-color: #ffd275;
   border-width: 2px;
   border-style: solid;
   font-weight: bold;
   padding-top: 3px;
   padding-bottom: 3px;
   padding-left: 10px;
   padding-right: 10px;
   color: #<%=linkAndHeaderText%>;
}
.ms-tabinactive
{
   font-family: verdana;
   background-color: #<%= navBackground %>;
   border-color: #<%= navBackground %>;
   border-width: 2px;
   border-style: solid;
   padding-top: 3px;
   padding-bottom: 3px;
   padding-right: 10px;
   padding-left: 10px;
   color: #<%=linkAndHeaderText%>;
}
.ms-tabinactive a:link
{
   color: #<%=linkAndHeaderText%>;
   text-decoration: none;
}
.ms-tabinactive a:hover
{
   color: #<%=linkAndHeaderText%>;
   text-decoration: none;
}
.ms-tabinactive a:visited
{
   color: #<%=linkAndHeaderText%>;
   text-decoration: none;
}

<%--
//
// CPAS styles, migrate away from ms-
//
-%>

img
    {
    border: 0;
    }

td.fullScreenTable
    {
    background-color: #<%= theme.getFullScreenBorderColor() %>;
    padding: 30px;
    height: 100%;
    vertical-align: middle;
    text-align: center;
    }

body, form
    {
    margin: 0;
    }


body, td, .gwt-Label
    {
    font-family: verdana, arial, helvetica, sans-serif;
    color: black;
    }

.cpas-error, .labkey-error, .error
    {
	font-size: <%= themeFont.getNormalSize() %>;
    color: red;
    }

.cpas-message, .labkey-message
    {
    font-size: <%= themeFont.getNormalSize() %>;
    color: <%= message %>;
    }

.cpas-message-strong
    {
    font-size: <%= themeFont.getNormalSize() %>;
    font-weight: bold;
    color: <%= message %>;
    }

.cpas-completion-highlight, .labkey-completion-highlight
    {
    background-color: #<%= navBackground %>;
    }

.cpas-completion-nohighlight, .labkey-completion-nohighlight
    {
    background-color: #FFFFFF;
    }

.cpas-navtree-selected, .labkey-navtree-selected
    {
    font-weight:bold;
    }

INPUT, .gwt-TextBox
	{
	font-size:<%= themeFont.getTextInputSize() %>;
	}

SELECT
	{
	font-size:<%= themeFont.getTextInputSize() %>;
	}

<% for (ThemeFont themeFontIter : themeFonts)
	{
	out.println("." + themeFontIter.getId());
	out.println("\t{");
	out.println("\tfont-size:"+themeFontIter.getNormalSize());
	out.println("\t}");
	}
%>

TEXTAREA, .gwt-TextArea
	{
	font-size:<%= themeFont.getTextInputSize() %>;
	}

.dataRegion TD
    {
    font-family: verdana;
	font-size: <%= themeFont.getTextInputSize() %>;
    vertical-align: top;
    padding-right:4px;
    }

.dataRegion A
    {
    color: #<%=linkAndHeaderText%>;
    text-decoration: none;
    }

.pagination
{
    white-space: nowrap;
    margin: 4px;
}

.pagination em
{
    font-weight: normal;
}

.button-bar
{
    white-space: nowrap;
    margin-top: 4px;
    margin-bottom: 2px;
}

.button-bar-item
{
    margin-right: 5px;
}

.overview .step
{
    padding-bottom: 0.5em;
    padding-left: 1em;
    text-indent: -1em;
}

.overview .step-disabled, .overview .step-disabled a:link, .overview .step-disabled a:visited
{
    color: silver;
}

<%--
//
// used by wiki (TODO: combine with cpas-webPart styles)
//
*/
-%>



<%-- for <th>, but why different than dataregion? -%>
.header
    {
    font-family: verdana;
    font-size: <%= themeFont.getNormalSize() %>;
    color: #<%= linkAndHeaderText %>;
    text-align: left;
    text-decoration: none;
    font-weight: normal;
    vertical-align: top;
    padding-right:4px;
    }

.header.hover
{
    background: #<%= wpHeaderPanel %>;
    cursor: pointer;
    cursor: hand;
}

.grid-filter-icon
{
    background-repeat: no-repeat;
    display: none;
    height: 8px;
    width: 11px;
    margin-left:2px;
    vertical-align: middle;
}

.filtered .grid-filter-icon {
    background-image: url(../_images/filter_on.gif);
    display: inline;
}



.wiki ul
    {
    list-style-image : url(../_images/square.gif);
    }

.wiki-table td, .wiki-table th
    {
    vertical-align: top;
    padding-right:4px;
    }

a.link
    {
    color: #<%=linkAndHeaderText%>;
    text-decoration: none;
    }


a.link:hover
    {
    color: #ff3300;
    text-decoration: underline;
    }


a.link:visited
    {
    color: #<%=linkAndHeaderText%>;
    text-decoration: none;
    }


a.link:visited:hover
    {
    color: #ff3300;
    text-decoration: underline;
    }


.heading-1
    {
    font-weight:bold;
    font-size: <%= themeFont.getHeader_1Size() %>;
    }


.heading-1-1
    {
    font-weight: normal;
    font-size: <%= themeFont.getHeader_1_1Size() %>;
    }


.code
    {
    font-family: courier;
    padding-left: 0.25in;
    }

<%--
//
// used by wiki TOC
//
-%>
table.wikitoc
    {
    border-style: none;
    border-collapse: collapse;
    }

td.wikitopic
    {
    vertical-align: top;
    padding: 0;
    }

.studyShaded
{
    vertical-align: top;
    background-color: #eeeeee;
    border-right:solid 1px #808080;
}

.studyCell
{
    vertical-align: top;
    background-color: #ffffff;
    border-right:solid 1px #808080;
}

.grid {
    border: 1px solid #aaaaaa;
    border-collapse: collapse;
}

.grid-ReadOnly {
}

.grid .colHeader {
    font-weight: bold;
    background-color: #E0E0E0;
    border: 1px solid black;
    padding-left: .5em;
    padding-right: .5em;
}

.grid .rowHeader {
    font-weight: bold;
    background-color: #E0E0E0;
    border: 1px solid black;
    padding-left: .5em;
    padding-right: .5em;
}

.empty {
    color: #808080;
}

.grid td {
    padding-left: .5em;
    padding-right: .5em;
    border: 1px solid #E0E0E0;
}

.grid input {
    border: 0;
}

.gridReadOnly .colHeader {
    font-weight: bold;
    border-bottom: 1px solid black;
    padding-left: .5em;
    padding-right: .5em;
}

.gridReadOnly .rowHeader {
    font-weight: bold;
    padding-left: .5em;
    padding-right: .5em;
}

.gridReadOnly td {
    padding-left: .5em;
    padding-right: .5em;
}


--%>