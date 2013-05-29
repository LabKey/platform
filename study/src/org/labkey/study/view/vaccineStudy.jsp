<%
/*
 * Copyright (c) 2011-2013 LabKey Corporation
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
<%@ page import="org.labkey.api.security.permissions.UpdatePermission" %>
<%@ page import="org.labkey.api.settings.LookAndFeelProperties" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.NotFoundException" %>
<%@ page import="org.labkey.api.view.ThemeFont" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.view.WebTheme" %>
<%@ page import="org.labkey.api.view.WebThemeManager" %>
<%@ page import="org.labkey.study.designer.StudyDesignManager" %>
<%@ page import="org.labkey.study.view.StudyGWTView" %>
<%@ page import="org.labkey.study.view.VaccineStudyWebPart" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.study.model.StudyImpl" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="org.labkey.api.study.Visit" %>
<%@ page extends="org.labkey.api.jsp.JspBase"%>

<%
    HttpView me = HttpView.currentView();
    ViewContext currentContext = HttpView.currentContext();
    VaccineStudyWebPart.Model bean = (VaccineStudyWebPart.Model) me.getModelBean();

    Map<String, String> params = new HashMap<>();
    params.put("studyId", Integer.toString(bean.getStudyId()));

    //In the web part we always show the latest revision
    Integer revInteger = StudyDesignManager.get().getLatestRevisionNumber(currentContext.getContainer(), bean.getStudyId());
    if (revInteger == null)
        throw new NotFoundException("No revision found for Study ID: " + bean.getStudyId());

    params.put("revision", Integer.toString(revInteger));
    params.put("edit", getViewContext().hasPermission(UpdatePermission.class) && bean.isEditMode() ? "true" : "false");
    boolean canEdit = getViewContext().hasPermission(UpdatePermission.class);
    params.put("canEdit",  Boolean.toString(canEdit));
    //Can't create repository from web part
    params.put("canCreateRepository", Boolean.FALSE.toString());

    StudyImpl study = StudyManager.getInstance().getStudy(getViewContext().getContainer());
    params.put("canAdmin", Boolean.toString(getViewContext().hasPermission(AdminPermission.class) && null != study));
    params.put("canCreateTimepoints", Boolean.toString(getViewContext().hasPermission(AdminPermission.class) && null != study && (study.getVisits(Visit.Order.CHRONOLOGICAL).size() < 1)));

    params.put("panel", bean.getPanel());  //bean.getPanel());
    if (null != bean.getFinishURL())
        params.put("finishURL", bean.getFinishURL());

    StudyGWTView innerView = new StudyGWTView(gwt.client.org.labkey.study.designer.client.Designer.class, params);

    Container c = currentContext.getContainer();
    WebTheme theme = WebThemeManager.getTheme(c);
    response.setContentType("text/css");

    ThemeFont themeFont = ThemeFont.getThemeFont(c);
    LookAndFeelProperties laf = LookAndFeelProperties.getInstance(c);

    String link        = theme.getLinkColor();
    String grid        = theme.getGridColor();
    String webpart     = theme.getWebPartColor();
    
%>
<style type="text/css">
table.cavd-study {
	font-family: Verdana, Arial, Helvetica, sans serif;
	border: none;
}

table.cavd-study h2 {
	font-family: Arial, Helvetica, sans serif;
	margin-top: 20px;
}

table.cavd-study td {
	padding: 0 5px;
	border: none; /* clears borders before it */
}

table.cavd-study input[type="text"]  {
	border: 1px solid #999;
	background-color: #fff;
	padding: 3px;
	font-size: 99%;
}

table.cavd-study select {
	border: 1px solid #999;
	background-color: #fff;
	padding: 2px;
	font-family: Verdana, Arial, Helvetica, sans serif;
	font-size: 99%;
}

table.cavd-study .gwt-Label {
	font-weight: normal;
}

table.cavd-study .labkey-col-header {
	border: none; /* clears borders before it */
	border-left: 1px solid #fff; /* now lets put in some new ones */
	border-bottom: 1px solid #fff;
	background-color: #<%= grid %>;
	padding: 2px 5px 6px 5px;
}

table.cavd-study .labkey-col-header-active {
	border: none; /* clears borders before it */
	border-left: 1px solid #fff; /* now lets put in some new ones */
	border-bottom: 1px solid #fff;
	background-color: #<%= grid %>;
	padding: 2px 5px 6px 5px;  
}

table.cavd-study .labkey-col-header-active .gwt-Label {
	color: #<%= link %>;
	font-weight: bold;
}

table.cavd-study table {
    margin-top:8px;
}

table.cavd-study td.cavd-row-padded table,  table.cavd-study td.cavd-row-padded-view table {
    margin-top:0;
}

table.cavd-study li {
    display: list-item;
    margin-left: 30px;
}
table.cavd-study ul {
    list-style-type: disc;
}
table.cavd-study .labkey-col-header-active .gwt-Label:hover {
	color: #<%= link %>;
	text-decoration: underline;
	cursor: pointer;
}

.cavd-schedule-header {
	font-weight: bold;
    text-align: center;
}

.cavd-schedule-header .gwt-label {
	font-weight: bold;
}

table.cavd-study .labkey-row-header {
	border: none;
	border-right: 1px solid #fff; /* now lets put in some new ones */
	border-bottom: 1px solid #fff;
	background-color: #<%= grid %>;
}

table.cavd-study .labkey-row {
	border-bottom: 1px solid #fff;
	border-right: 1px solid #fff;
	background-color: #fafafa; 
}

table.cavd-study .labkey-row-active {
	border-bottom: 1px solid #fff;
	border-right: 1px solid #fff;
	padding: 3px 20px 3px 5px;
	background-color: #<%= webpart %>;
}

table.cavd-study .labkey-row-active .gwt-Label, table.cavd-study .labkey-row-header .gwt-Label {
	color: #<%= link %>;
    cursor: pointer;
}

table.cavd-study .labkey-row-active .gwt-Label:hover, table.cavd-study .labkey-row-header .gwt-Label:hover {
	color: #<%= link %>;
	text-decoration: underline;
	cursor: pointer;
}

table.cavd-study .cavd-row-bottom {
	background-color: #fafafa;
}

table.cavd-study .cavd-corner {
	border: none;
	background-color: #<%= grid %>;
	border-bottom: 1px solid #fff;
}

table.cavd-study .cavd-row table, table.cavd-study .cavd-row-bottom table {
	margin: 4px 0;
}

table.cavd-study .cavd-row-padded {
	border-bottom: 1px solid #fff;
	border-right: 1px solid #fff;
	padding-top: 3px;
	padding-bottom: 3px;
	background-color: #fafafa;
}

table.cavd-study .cavd-row-padded-view {
	border-bottom: 1px solid #fff;
	border-right: 1px solid #fff;
	padding-top: 3px;
	padding-bottom: 3px;
	background-color: #fafafa;
}

a.labkey-button,
a.labkey-button:visited,
a.gwt-Anchor {
    padding: 2px 5px;
	margin-right: 5px;
    border: 1px solid #ddd;
    background-color: #FFFFFF;
    color: #<%= link %>;
    text-transform: uppercase;
    font-size: 10px;
    font-family: Verdana, Arial, Helvetica, sans serif;
    font-weight: bold;
    white-space: nowrap;

    -moz-border-radius: 5px;
    -webkit-border-radius: 5px;
	
	background: -moz-linear-gradient(center top , #FFFFFF, #E5E5E5);

    *padding: 1px 5px 0px 5px;
}

a.labkey-button:hover,
a.gwt-Anchor:hover
{
    padding: 2px 5px;
    border: 1px solid #676767;
    background-color: #ffffff;
    color: #000000;
    text-transform: uppercase;
    font-size: 10px;
    font-family: Verdana, Arial, Helvetica, sans serif;
    font-weight: bold;
    cursor: pointer;

    border-radius: 5px;
    -moz-border-radius: 5px;
    -webkit-border-radius: 5px;
	
	background: -moz-linear-gradient(center top , #FFFFFF, #E5E5E5);
	background: -moz-linear-gradient(center top , #FFFFFF, #E5E5E5);

    *padding: 1px 5px 0px 5px;
}

table.assay-buttons .empty, table.assay-buttons .gwt-Label {
	padding: 10px;
	border: 1px solid #ddd;
	margin-bottom: 10px;
}
</style>
<%
include(innerView, out);
%>
