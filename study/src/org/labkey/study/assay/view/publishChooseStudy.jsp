<%
/*
 * Copyright (c) 2007-2008 LabKey Corporation
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
<%@ page import="org.labkey.api.data.Container"%>
<%@ page import="org.labkey.api.data.DataRegion" %>
<%@ page import="org.labkey.api.data.DataRegionSelection" %>
<%@ page import="org.labkey.api.security.ACL" %>
<%@ page import="org.labkey.api.study.actions.PublishStartAction" %>
<%@ page import="org.labkey.api.study.assay.AssayPublishService" %>
<%@ page import="org.labkey.api.study.assay.AssayService" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.common.util.Pair" %>
<%@ page import="org.labkey.study.model.Study" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="java.util.Iterator" %>
<%@ page import="java.util.Map" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<PublishStartAction.PublishBean> me = (JspView<PublishStartAction.PublishBean>) HttpView.currentView();
    PublishStartAction.PublishBean bean = me.getModelBean();
    boolean unambiguous = !bean.isInsufficientPermissions() && !bean.isNullStudies() && bean.getStudies().size() == 1;
    Study firstStudy = null;
    Container firstStudyContainer = null;
    if (unambiguous)
    {
        Iterator<Container> studyIt = bean.getStudies().iterator();
        firstStudyContainer = studyIt.next();
        firstStudy = StudyManager.getInstance().getStudy(firstStudyContainer);
        if (firstStudy == null)
            unambiguous = false;
    }

    ActionURL postURL = AssayService.get().getPublishConfirmURL(getViewContext().getContainer(), bean.getProtocol());
    Pair<String, String>[] parameters = postURL.getParameters();
    postURL.deleteParameters();
%>

<%
    if (bean.getStudies().size() > 1)
    {
%>
<span class="labkey-error"><h4>WARNING: The selected runs were initially associated with different studies.</h4></span>
<%
    }
    if (bean.isInsufficientPermissions())
    {
%>
<span class="labkey-error"><h4>WARNING: You do not have permissions to publish to one or more of the selected run's associated studies.</h4></span>
<%
    }
%>
<form action="<%= postURL.getLocalURIString() %>" method="POST">
<%
    for (Pair<String, String> parameter : parameters)
    {
%>
    <input type="hidden" name="<%= parameter.getKey() %>" value="<%= h(parameter.getValue()) %>">
    <input type="hidden" name="returnURL" value="<%= h(bean.getReturnURL()) %>">
    <input type="hidden" name="containerFilter" value="<%= h(bean.getContainerFilter()) %>"
<%
    }
    for (Integer id : bean.getIds())
    {
%>
    <input type="hidden" name="<%= DataRegion.SELECT_CHECKBOX_NAME %>" value="<%= id %>">
<%
    }
%>
<input type="hidden" name="<%= DataRegionSelection.DATA_REGION_SELECTION_KEY %>" value="<%= bean.getDataRegionSelectionKey() %>">
<table>
    <%
        if (unambiguous)
        {
    %>
        <tr>
            <td colspan="2">
                All data is marked for publication to study <b><%= h(firstStudy.getLabel()) %></b>
                in folder <b><%= h(firstStudy.getContainer().getPath()) %></b>.<br>
                <input type="checkbox"
                       onclick="getElementById('targetStudyTitle').style.display = (this.checked ? 'block' : 'none');
                                getElementById('targetStudyPicker').style.display = (this.checked ? 'block' : 'none')">
                Publish to a different study
            </td>
        </tr>
    <%
        }
    %>
    <tr>
        <td>
            <span id="targetStudyTitle" style="display:<%= unambiguous ? "none" : "block" %>">Choose target study:</span>
        </td>
        <td>
            <span id="targetStudyPicker" style="display:<%= unambiguous ? "none" : "block" %>">
                <select name="targetStudy">
                <%

                    Map<Container, String> targets = AssayPublishService.get().getValidPublishTargets(getViewContext().getUser(), ACL.PERM_INSERT);
                    for (Map.Entry<Container, String> target : targets.entrySet())
                    {
                        String path = target.getKey().getPath();
                        boolean selected = firstStudyContainer != null && firstStudyContainer.getPath().equals(path);
                %>
                    <option value="<%= h(target.getKey().getId()) %>" <%= selected ? "SELECTED" : "" %>><%= h(path)%> (<%= h(target.getValue()) %>)</option>
                <%
                    }
                %>
                </select>
            </span>
        </td>
    </tr>
    <tr>
        <td colspan="2">
            <%= generateSubmitButton("Next") %>
            <%= generateButton("Cancel", bean.getReturnURL())%>
        </td>
    </tr>
</table>
</form>