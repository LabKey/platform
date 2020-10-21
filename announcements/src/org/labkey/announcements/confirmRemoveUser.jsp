<%
/*
 * Copyright (c) 2008-2019 LabKey Corporation
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
<%@ page import="org.labkey.announcements.AnnouncementsController.RemoveUserView.RemoveUserBean" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    HttpView<RemoveUserBean> me = (HttpView<RemoveUserBean>) HttpView.currentView();
    RemoveUserBean bean = me.getModelBean();
%>
Are you sure you want to remove yourself (<%=h(bean.email)%>) from the member list of this <%=h(bean.conversationName)%>?
<p/>
<b>Title: <%=h(bean.title)%></b>
