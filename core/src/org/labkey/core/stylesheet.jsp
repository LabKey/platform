<%@ page import="org.labkey.api.util.AppProps" %>
<%@ page import="org.labkey.api.view.ThemeFont" %>
<%@ page import="org.labkey.api.view.WebTheme" %>
<%@ page import="java.util.List" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    WebTheme theme = org.labkey.api.view.WebTheme.getTheme();
    ThemeFont themeFont = org.labkey.api.view.ThemeFont.getThemeFont();
    List<ThemeFont> themeFonts = org.labkey.api.view.ThemeFont.getThemeFonts();
    response.setContentType("text/css");

    AppProps app = AppProps.getInstance();
    String titleColor = WebTheme.toRGB(theme.getTitleColor());
    String navBarWidth = app.getNavigationBarWidth();
    String mapPath = request.getContextPath() + "/_yui/build/menu/assets/map.gif";
//
// MS styles we still use
//
%>

<%-- web part --%>
.wpHeader
{
    background-color: #<%= theme.getGradientLightString() %>;
}

.wpHeader td, .wpHeader th
{
    border: 1px solid #<%= theme.getGradientDarkString() %>;
}

.wpTitle
{
    font-weight: bold;
    font-family: verdana, arial, helvetica, sans-serif;
    color: #<%=titleColor%>;
    padding-left: 6px;
    padding-right: 4px;
    padding-top: 2px;
    padding-bottom: 2px;
    font-size: <%= themeFont.getNormalSize() %>;
    text-align: left;
    white-space:nowrap;
}
.wpTitle A:link, .wpTitle A:visited
{
    color:#<%=titleColor%>;
    text-decoration:none;
    cursor:pointer;
}
.wpTitle A:hover
{
    color:red;
    text-decoration:underline;
    cursor:pointer;
}

#headerpanel
{
    height: 56px;
}

.leftNavBox
{
    background-color: white;
    border-collapse: collapse;
    border-left: 0px;
    border-top: 1px solid #<%= theme.getHeaderLineColor() %>;
    border-right: 1px solid #<%= theme.getHeaderLineColor() %>;
    border-bottom: 1px solid #<%= theme.getHeaderLineColor() %>;
    padding: 1px;
    margin-top:5px;
    margin-right:5px;
    padding: 0px;
    width: <%= navBarWidth %>px;
}

.leftNavBoxBody
{
    border-top: 1px solid #<%= theme.getHeaderLineColor() %>;
}

.ms-titlearealine{
    background-color: #<%= theme.getHeaderLineColor() %>;
}
.ms-nav {
    font-size: <%= themeFont.getNormalSize() %>;
}
.ms-nav td, .ms-nav .ms-input {
    background: #<%= theme.getNavBarColor() %>;
    font-family: Verdana, sans-serif;
    font-size: <%= themeFont.getTextInputSize() %>;
}
.ms-nav th {
    font-size: <%= themeFont.getNormalSize() %>;
    font-family: Verdana, sans-serif;
    font-weight: normal;
    text-align: left;
    color: black;
    background: #<%= theme.getNavBarColor() %>;
}
.ms-navheader, .ms-navheader A, .ms-navheader A:link, .ms-navheader A:visited {
    font-weight: bold;
	color: #<%=titleColor%>;
}
.ms-navframe {
    background: #<%= theme.getNavBarColor() %>;
    border-right: none;
    border-left: 0px;
}
.ms-searchform {
	background-color: #<%= theme.getEditFormColor() %>;
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
	background-color: #<%= theme.getEditFormColor() %>;
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
    background-color: #<%= theme.getEditFormColor() %>;
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
    color: #<%=titleColor%>;
}
.ms-nav a:link {
}
<%--.ms-nav a:hover {
    text-decoration: underline;
    color: #798073;
}
.ms-nav a:visited {
   color: #<%=titleColor%>;
} --%>
.ms-announcementtitle, .ms-announcementtitle a {
    font-weight: bold;
    color: #<%=titleColor%>
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
   color: #<%=titleColor%>;
}
.ms-tabinactive
{
   font-family: verdana;
   background-color: #<%= theme.getNavBarColor() %>;
   border-color: #<%= theme.getNavBarColor() %>;
   border-width: 2px;
   border-style: solid;
   padding-top: 3px;
   padding-bottom: 3px;
   padding-right: 10px;
   padding-left: 10px;
   color: #<%=titleColor%>;
}
.ms-tabinactive a:link
{
   color: #<%=titleColor%>;
   text-decoration: none;
}
.ms-tabinactive a:hover
{
   color: #<%=titleColor%>;
   text-decoration: none;
}
.ms-tabinactive a:visited
{
   color: #<%=titleColor%>;
   text-decoration: none;
}

<%--
//
// CPAS styles, migrate away from ms-
//
--%>

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

.navTab-selected
    {
    padding-top:0.1em;
    padding-bottom:0.1em;
    padding-left:0.5em;
    padding-right:0.5em;
    border-top:solid 1px #<%= theme.getHeaderLineColor() %>;
    border-left:solid 1px #<%= theme.getHeaderLineColor() %>;
    border-right:solid 1px #<%= theme.getHeaderLineColor() %>;
    border-bottom:solid 1px #ffffff;
    color: #<%=titleColor%>;
    text-decoration: none;
    text-align: center;
    font-weight: bold;
    font-family: verdana, arial, helvetica, sans-serif;
    font-size: <%= themeFont.getNormalSize() %>;
    }

.navTab-inactive
    {
    padding-top:0.1em;
    padding-bottom:0.1em;
    padding-left:0.5em;
    padding-right:0.5em;
    border-top:solid 1px #<%= theme.getHeaderLineColor() %>;
    border-left:solid 1px #<%= theme.getHeaderLineColor() %>;
    border-right:solid 1px #<%= theme.getHeaderLineColor() %>;
    border-bottom:solid 1px #<%= theme.getHeaderLineColor() %>;
    text-decoration: none;
    text-align: center;
    font-family: verdana, arial, helvetica, sans-serif;
    font-size: <%= themeFont.getNormalSize() %>;
    color: #444444;
    }

.navTab
    {
    padding-top:0.1em;
    padding-bottom:0.1em;
    padding-left:0.5em;
    padding-right:0.5em;
    border-top:solid 1px #<%= theme.getHeaderLineColor() %>;
    border-left:solid 1px #<%= theme.getHeaderLineColor() %>;
    border-right:solid 1px #<%= theme.getHeaderLineColor() %>;
    border-bottom:solid 1px #<%= theme.getHeaderLineColor() %>;
    text-decoration: none;
    text-align: center;
    vertical-align:bottom;
    font-family: verdana, arial, helvetica, sans-serif;
    font-size: <%= themeFont.getNormalSize() %>;
    color: #<%=titleColor%>;
    }

.navPageHeader
	{
    font-family: verdana, arial, helvetica, sans-serif;
	font-weight: bold;
	font-size: <%= themeFont.getPageHeaderSize() %>;
	color: #<%=titleColor%>;
	}

.cpas-error, .labkey-error, .error
    {
	font-size: <%= themeFont.getNormalSize() %>;
    color: red;
    }

.cpas-message
    {
    font-size: <%= themeFont.getNormalSize() %>;
    color: green;
    }

.cpas-message-strong
    {
    font-size: <%= themeFont.getNormalSize() %>;
    font-weight: bold;
    color: green;
    }

.cpas-completion-highlight, .labkey-completion-highlight
    {
    background-color: #<%= theme.getNavBarColor() %>;
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
    color: #<%=titleColor%>;
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
--%>

BODY, DIV, TD, .normal, .normal td, .normal th, .wiki, .wiki-table td, .wiki-table th
    {
    font-family: verdana, arial, helvetica, sans-serif;
    font-size: <%= themeFont.getNormalSize() %>;
    }

.normal A
    {
    color: #<%=titleColor%>;
    text-decoration: none;
    }

<%-- for <th>, but why different than dataregion? --%>
.header
    {
    font-family: verdana;
    font-size: <%= themeFont.getNormalSize() %>;
    color: #808080;
    text-align: left;
    text-decoration: none;
    font-weight: normal;
    vertical-align: top;
    padding-right:4px;
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
    color: #<%=titleColor%>;
    text-decoration: none;
    }


a.link:hover
    {
    color: #ff3300;
    text-decoration: underline;
    }


a.link:visited
    {
    color: #<%=titleColor%>;
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
--%>
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



.gwt-ToolTip {
	background-color: #<%= theme.getEditFormColor() %>;
	font-family: verdana;
	font-size: <%= themeFont.getNormalSize() %>;
    padding-left: 4px;
    padding-right: 4px;
    padding-top: 2px;
    padding-bottom: 2px;
    border: solid 1px black;
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
  sborder: 8px solid #<%=theme.getHeaderLineColor()%>;
  border: 2px outset;
  background-color: white;
}

.gwt-DialogBox .Caption {
  background-color: #<%=theme.getGradientLightString()%>;
  border: 1px solid #<%= theme.getGradientDarkString() %>;
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
  background-color: #<%=theme.getGradientDarkString()%>;
}

.gwt-VerticalSplitter .Bar {
  height: 8px;
  background-color: #<%=theme.getGradientDarkString()%>;
}

.gwt-MenuBar {
  background-color: #<%=theme.getNavBarColor()%>;
  border: 1px solid #<%=theme.getHeaderLineColor()%>;
  cursor: default;
}

.gwt-MenuBar .gwt-MenuItem {
  padding: 1px 4px 1px 4px;
  font-size: <%=themeFont.getNormalSize()%>;
  cursor: default;
}

.gwt-MenuBar .gwt-MenuItem-selected {
  background-color: #<%=theme.getHeaderLineColor()%>;
}

.gwt-TabPanelBottom {
  border: 1px solid #<%=theme.getHeaderLineColor()%>;
}

.gwt-TabBar {
  font-size: <%=themeFont.getNormalSize()%>;
}

.gwt-TabBar .gwt-TabBarFirst {
  padding-left: 3px;
}

.gwt-TabBar .gwt-TabBarRest {
  border-left: 1px solid #<%=theme.getHeaderLineColor()%>;
  padding-right: 3px;
}

.gwt-TabBar .gwt-TabBarItem {
  border-top: 1px solid #<%=theme.getHeaderLineColor()%>;
  border-left: 1px solid #<%=theme.getHeaderLineColor()%>;
  padding: 2px;
  cursor: pointer;
  cursor: hand;
}

.gwt-TabBar .gwt-TabBarItem-selected {
  font-weight: bold;
  border-top: 1px solid #<%=theme.getHeaderLineColor()%>;
  border-left: 1px solid #<%=theme.getHeaderLineColor()%>;
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
  background-color: #<%=theme.getNavBarColor()%>;
  cursor: pointer;
  cursor: hand;
}

.gwt-StackPanel .gwt-StackPanelItem-selected {
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



fieldset
{
    border: 1px solid #<%= theme.getGradientDarkString() %>;
    height: 5em;
    padding-left: 5px;
    padding-right: 5px;
    padding-bottom: 0;
}

legend
{
    border: 1px solid #<%= theme.getGradientDarkString() %>;
    padding: 2px 6px;
    background-color: #<%= theme.getGradientLightString() %>;
}

/*
Copyright (c) 2007, Yahoo! Inc. All rights reserved.
Code licensed under the BSD License:
http://developer.yahoo.net/yui/license.txt
version: 2.2.2
*/
/* Menu styles */

div.yuimenu {

    background-color:white;
    border:solid 1px #<%=theme.getHeaderLineColor()%>;
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

    background-color:#f6f7ee;

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
    border:solid 1px #<%=theme.getHeaderLineColor()%>;
    color:#<%=titleColor%>;

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
    border:solid 1px #<%=theme.getHeaderLineColor()%>;
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
    color:#<%=titleColor%>;
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

    background-color:#<%=theme.getNavBarColor()%>;

}

div.yuimenu li.selected a.selected,
div.yuimenubar li.selected a.selected {

    text-decoration:underline;

}

div.yuimenu li.selected a.selected,
div.yuimenu li.selected em.selected,
div.yuimenubar li.selected a.selected {

    color:#<%=titleColor%>;

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
    border-color:#<%=theme.getHeaderLineColor()%>;
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