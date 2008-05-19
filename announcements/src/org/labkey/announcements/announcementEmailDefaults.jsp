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
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.announcements.AnnouncementsController.AnnouncementEmailDefaults.EmailDefaultsBean" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.announcements.model.AnnouncementManager.EmailOption" %>
<%@ page import="org.labkey.api.action.ReturnUrlForm" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    HttpView<EmailDefaultsBean> me = (HttpView<EmailDefaultsBean>) HttpView.currentView();
    EmailDefaultsBean bean = me.getModelBean();
%>
<form action="setDefaultEmailOptions.view">
<table width="50%">
<tr>
    <td align="left" class="normal" style="padding-top:2px;padding-bottom:4px;"><b>Folder default settings</b></td>
</tr>
<tr>
    <td align="left" class="normal" style="padding-top:2px;padding-bottom:4px;">Specify the default settings for email notifications for this folder. For any individual user, that user or an administrator can override the default setting.</td>
</tr>
<tr>
    <td>
        <select name='defaultEmailOption'>
        <%
            for (EmailOption option : bean.emailOptionsList)
            {
                if (option.getEmailOptionId() == bean.defaultEmailOption)
                {%>
                <option selected value=<%=option.getEmailOptionId()%>><%=option.getEmailOption()%></option>
            <%}
            else
            {%>
                <option value=<%=option.getEmailOptionId()%>><%=option.getEmailOption()%></option>
            <%}
        }%>
        </select>
    </td>
</tr>
<tr>
    <td>
        <input type="hidden" name="<%=ReturnUrlForm.Params.returnUrl%>" value="<%=h(bean.returnURL)%>">
        <input type="image" src="<%=PageFlowUtil.buttonSrc("Set")%>">
    </td>
</tr>
</table>
</form>
<br>