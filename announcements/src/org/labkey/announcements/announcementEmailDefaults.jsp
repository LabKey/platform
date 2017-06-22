<%
/*
 * Copyright (c) 2007-2017 LabKey Corporation
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
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page import="org.labkey.announcements.AnnouncementsController"%>
<%@ page import="org.labkey.announcements.AnnouncementsController.AnnouncementEmailDefaults.EmailDefaultsBean" %>
<%@ page import="org.labkey.api.message.settings.MessageConfigService" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    HttpView<EmailDefaultsBean> me = (HttpView<EmailDefaultsBean>) HttpView.currentView();
    EmailDefaultsBean bean = me.getModelBean();
%>
<labkey:form action="<%=h(buildURL(AnnouncementsController.SetDefaultEmailOptionsAction.class))%>">
<table width="50%">
<tr>
    <td align="left" style="padding-top:2px;padding-bottom:4px;"><b>Folder default settings</b></td>
</tr>
<tr>
    <td align="left" style="padding-top:2px;padding-bottom:4px;">Specify the default settings for email notifications for this folder. For any individual user, that user or an administrator can override the default setting.</td>
</tr>
<tr>
    <td>
        <select name='defaultEmailOption'>
        <%
            for (MessageConfigService.NotificationOption option : bean.emailOptionsList)
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
        <%=generateReturnUrlFormField(bean.returnURL)%>
        <%= button("Set").submit(true) %>
    </td>
</tr>
</table>
</labkey:form>
<br>