<%--
/*
 * Copyright (c) 2016 LabKey Corporation
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
--%>
<%@ page buffer="none" %>
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.template.PageConfig" %>
<%@ page import="org.labkey.api.view.template.PrintTemplate" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    PrintTemplate me = (PrintTemplate) HttpView.currentView();
    PageConfig model = me.getModelBean();
    ActionURL url = getActionURL();

    if (model.getFrameOption() != PageConfig.FrameOption.ALLOW)
        response.setHeader("X-FRAME-OPTIONS", model.getFrameOption().name());

    String onLoad = "";
    if (StringUtils.isNotEmpty(model.getFocus()))
        onLoad += "(document." + model.getFocus() + "?document." + model.getFocus() + ".focus():null);";
    if (model.getShowPrintDialog())
        onLoad += "window.print(); ";
%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="utf-8" />
    <meta http-equiv="X-UA-Compatible" content="IE=edge" />
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <%= text(model.getMetaTags(url)) %>
    <title><%= h(model.getTitle()) %></title>
    <%= PageFlowUtil.getStandardIncludes(getViewContext(), model.getClientDependencies()) %>
</head>
<body onload="<%=h(onLoad)%>">
<%
    if (me.includeGWT()) {
%>
<iframe id="__gwt_historyFrame" style="width:0;height:0;border:0"></iframe>
<%
    }
%>
<%
    if (model.showHeader() != PageConfig.TrueFalse.False)
    {
        me.include(me.getView("header"), out);

        if (null != me.getView("navigation"))
            me.include(me.getView("navigation"),out);
    }
%>
<% me.include(me.getView("bodyTemplate"), out); %>
<script type="text/javascript">LABKEY.loadScripts(); LABKEY.showNavTrail();</script>
<!-- <%= h(request.getHeader("User-Agent")) %> -->
<a href="<%= me.getPermaLink() %>" id="permalink" style="display: none;"></a>
</body>