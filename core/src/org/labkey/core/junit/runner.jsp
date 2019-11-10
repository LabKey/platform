<%
/*
 * Copyright (c) 2019 LabKey Corporation
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
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.core.junit.JunitController" %>
<%@ page import="org.labkey.core.junit.JunitController.JUnitViewBean" %>
<%@ page import="org.labkey.core.junit.JunitController.RunAction" %>
<%@ page import="org.labkey.core.junit.JunitController.Run2Action" %>
<%@ page import="org.labkey.core.junit.JunitController.Run3Action" %>
<%@ page import="static org.labkey.api.util.DOM.*" %>
<%@ page import="static org.labkey.api.util.DOM.Attribute.*" %>
<%@ page import="static org.labkey.api.util.HtmlString.NBSP" %>
<%@ page import="org.junit.runner.Request" %>
<%@ page import="org.junit.runner.Runner" %>
<%@ page import="org.junit.runner.Description" %>
<%@ page import="org.w3c.dom.css.CSS2Properties" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<JUnitViewBean> me = (JspView<JUnitViewBean>) HttpView.currentView();
    JUnitViewBean bean = me.getModelBean();
    var testCases = bean.testCases;
    var showRunButtons = bean.showRunButtons;
%>
<style type="text/css">
    /* for firefox to display the expand/collapse triangle */
    details > summary {
        display: list-item;
    }
</style>
<%
    if (showRunButtons)
    {
        createHtmlFragment(
            DIV(
                    button("Show All").onClick("Array.from(document.getElementsByTagName('details')).forEach(function (d) { d.open = true; });").build(),
                    NBSP,
                    button("Hide All").onClick("Array.from(document.getElementsByTagName('details')).forEach(function (d) { d.open = false; });").build(),

                    NBSP, "\u22EE", NBSP,

                    button("Run All").href(new ActionURL(RunAction.class, getContainer())),
                    NBSP,
                    button("Run BVT").href(new ActionURL(RunAction.class, getContainer()).addParameter("when", "BVT")),
                    NBSP,
                    button("Run DRT").href(new ActionURL(RunAction.class, getContainer()).addParameter("when", "DRT")),

                    NBSP, "\u22EE", NBSP,

                    LK.FORM(at(style, "display:inline-block;", name, "run2", action, new ActionURL(Run2Action.class, getContainer()), method, "POST"),
                            button("Run In Background #1 (Experimental)").submit(true)),
                    NBSP,
                    button("Run In Background #2 (Experimental)").href(new ActionURL(Run3Action.class, getContainer()))
                ),
            HR()).appendTo(out);

    }

    DIV(testCases.keySet().stream().map(module ->
        DETAILS(at(open, true),
            SUMMARY(A(at(href, new ActionURL(RunAction.class, getContainer()).addParameter("module", module)), module)),
            DIV(at(style, "padding-bottom:1em;"), testCases.get(module).stream().map(clazz -> {
                Runner runner = Request.aClass(clazz).getRunner();
                Description desc = runner.getDescription();
                String displayName = clazz.getName();
                ActionURL testCaseURL = new ActionURL(RunAction.class, getContainer()).addParameter("testCase", clazz.getName());

                return DIV(cl("labkey-indented"),
                        DETAILS(
                                SUMMARY(
                                    A(at(href, testCaseURL.getLocalURIString()), displayName),
                                    showRunButtons ? SPAN(at(style,"font-size:66%; color:gray; vertical-align:top;"), NBSP, JunitController.getScope(clazz).name(), NBSP) : null,
                                    " (", String.valueOf(desc.testCount()), ")"),
                                UL(desc.getChildren().stream().map(
                                        child -> LI(child.getMethodName() != null
                                                ? A(at(href, testCaseURL.clone().addParameter("methodName", child.getMethodName())), child.getMethodName())
                                                : child.toString())))

                        ));
            }))
    ))).appendTo(out);

%>
