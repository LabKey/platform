<%
/*
 * Copyright (c) 2007-2014 LabKey Corporation
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
<%@ page import="org.labkey.api.security.permissions.InsertPermission" %>
<%@ page import="org.labkey.api.study.Study" %>
<%@ page import="org.labkey.api.study.actions.PublishStartAction" %>
<%@ page import="org.labkey.api.study.assay.AssayPublishService" %>
<%@ page import="org.labkey.api.study.assay.AssayUrls" %>
<%@ page import="org.labkey.api.util.Pair" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="java.util.Iterator" %>
<%@ page import="java.util.Set" %>
<%@ page import="java.util.List" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
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

    ActionURL postURL = urlProvider(AssayUrls.class).getCopyToStudyConfirmURL(getContainer(), bean.getProtocol());
    List<Pair<String, String>> parameters = postURL.getParameters();
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
<span class="labkey-error"><h4>WARNING: You do not have permissions to copy to one or more of the selected run's associated studies.</h4></span>
<%
    }
%>
<labkey:form action="<%= h(postURL.getLocalURIString()) %>" method="POST">
<%
    for (Pair<String, String> parameter : parameters)
    {
%>
    <input type="hidden" name="<%= h(parameter.getKey()) %>" value="<%= h(parameter.getValue()) %>">
<%
    }
    for (Integer id : bean.getIds())
    {
%>
    <input type="hidden" name="<%= h(DataRegion.SELECT_CHECKBOX_NAME) %>" value="<%= id %>">
<%
    }
%>
<input type="hidden" name="<%= ActionURL.Param.returnUrl %>" value="<%= h(bean.getReturnURL()) %>">
<input type="hidden" name="containerFilterName" value="<%= h(bean.getContainerFilterName()) %>">
<input type="hidden" name="<%= h(DataRegionSelection.DATA_REGION_SELECTION_KEY) %>" value="<%=h(bean.getDataRegionSelectionKey())%>">
<table>
    <%
        if (unambiguous)
        {
    %>
        <tr>
            <td colspan="2">
                All data is marked for copying to study <b><%= h(firstStudy.getLabel()) %></b>
                in folder <b><%= h(firstStudy.getContainer().getPath()) %></b>.<br>
                <input type="checkbox"
                       onclick="getElementById('targetStudyTitle').style.display = (this.checked ? 'block' : 'none');
                                getElementById('targetStudyPicker').style.display = (this.checked ? 'block' : 'none');">
                Copy to a different study
            </td>
        </tr>
    <%
        }
    %>
    <tr>
        <td>
            <span id="targetStudyTitle" style="display:<%= text(unambiguous ? "none" : "block") %>">Choose target study:</span>
        </td>
        <td>
            <span id="targetStudyPicker" style="display:<%= text(unambiguous ? "none" : "block") %>">
                <select name="targetStudy">
                <%

                    Set<Study> studies = AssayPublishService.get().getValidPublishTargets(getUser(), InsertPermission.class);
                    for (Study study : studies)
                    {
                        String path = study.getContainer().getPath();
                        boolean selected = firstStudyContainer != null && firstStudyContainer.getPath().equals(path);
                %>
                    <option value="<%= h(study.getContainer().getId()) %>"<%=selected(selected)%>><%= h(path)%> (<%= h(study.getLabel()) %>)</option>
                <%
                    }
                %>
                </select>
            </span>
        </td>
    </tr>
    <tr>
        <td colspan="2">
            <%= button("Next").submit(true) %>
            <%= button("Cancel").href(bean.getReturnURL()) %>
        </td>
    </tr>
</table>
</labkey:form>
