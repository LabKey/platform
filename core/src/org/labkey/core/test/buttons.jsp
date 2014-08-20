<%
/*
 * Copyright (c) 2014 LabKey Corporation
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
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.core.test.TestController.ButtonForm" %>
<%@ page import="org.labkey.api.util.Button" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.core.test.TestController" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<ButtonForm> me = (JspView<ButtonForm>) HttpView.currentView();
    ButtonForm form = me.getModelBean();
    ActionURL formURL = new ActionURL(TestController.ButtonAction.class, getContainer());
%>
<labkey:form method="POST" action="button.view?">
    <table>
        <tr>
            <td><label for="buttontext">Text</label></td>
            <td><input id="buttontext" name="text" type="text" value="<%= text(form.getText()) %>"></td>
        </tr>
        <tr>
            <td><label for="buttonhtml">Text as HTML</label></td>
            <td><input id="buttonhtml" name="html" type="checkbox" value="true" <%= form.isHtml() ? "checked=\"checked\"" : ""%>></td>
        </tr>
        <tr>
            <td><label for="buttonhref">HREF</label></td>
            <td><input id="buttonhref" name="href" type="text" style="width: 400px;" value="<%= form.getHref() != null ? form.getHref() : formURL %>"></td>
        </tr>
        <tr>
            <td><label for="buttonenabled">Enabled</label></td>
            <td><input id="buttonenabled" name="enabled" type="checkbox" value="true" <%= form.isEnabled() ? "checked=\"checked\"" : ""%>></td>
        </tr>
        <tr>
            <td><label for="buttondoc">Disable on Click</label></td>
            <td><input id="buttondoc" name="disableonclick" type="checkbox" value="true" <%= form.isDisableonclick() ? "checked=\"checked\"" : ""%>></td>
        </tr>
        <tr>
            <td><label for="buttonsubmit">Submit</label></td>
            <td><input id="buttonsubmit" name="buttonsubmit" type="checkbox" value="true" <%= form.isButtonsubmit() ? "checked=\"checked\"" : ""%>></td>
        </tr>
        <tr>
            <td><label for="buttonhref">onClick</label></td>
            <td><textarea rows="15" cols="50" name="onclick"><%= text(form.getOnclick()) %></textarea></td>
        </tr>
        <tr>
            <td><label>Attributes</label></td>
            <td>
                <input name="attrkey1" type="text" placeholder="key" value="<%= text(form.getAttrkey1()) %>">
                <input name="attrvalue1" type="text" placeholder="value" value="<%= text(form.getAttrvalue1()) %>">
            </td>
        </tr>
        <tr>
            <td></td>
            <td>
                <input name="attrkey2" type="text" placeholder="key" value="<%= text(form.getAttrkey2()) %>">
                <input name="attrvalue2" type="text" placeholder="value" value="<%= text(form.getAttrvalue2()) %>">
            </td>
        </tr>
    </table>
    <%= button("Generate Button").submit(true) %>
</labkey:form>
<%
    Button.ButtonBuilder button = form.getBuiltButton();
    if (button != null)
    {
%>
<div style="margin-top: 30px;">Generated Button:<%= button %></div>
<%
    }
%>