<%
/*
 * Copyright (c) 2005-2014 LabKey Corporation
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
<%@ page import="org.labkey.api.util.DateUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.core.admin.AdminController" %>
<%@ page import="java.util.Date" %>
<%@ page import="java.util.Set" %>
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    HttpView<AdminController.ThreadsBean> me = (HttpView<AdminController.ThreadsBean>) HttpView.currentView();
    AdminController.ThreadsBean bean = me.getModelBean();
%>
<p><strong>Threads as of <%= h(DateUtil.formatDateTime(new Date(), "yyyy-MM-dd HH:mm:ss.SSS")) %></strong></p>
<%
for (Thread t : bean.threads)
{
    try
    {
    %><a href="#<%=h(t.getName())%>"><strong><%= h(t.getName()) %></strong></a> (<%= t.getState() %>)<%
    Set<Integer> values = bean.spids.get(t);
    if (values.size() > 0)
    {
        %><span class="labkey-error">DB Connection SPID(s): <%= h(StringUtils.join(values, ",")) %></span><%
    }
    }
    catch (Exception x)
    {
        %><strong class=labkey-error><%=h(x)%></strong><%
    }
    %><br/><%
}

%><hr/><%

for (Thread t : bean.threads)
{
    try
    {
        %><a name="<%= h(t.getName()) %>"></a><%
        %><pre><%= h(t.getName()) %> (<%= t.getState() %>)<%=text("\n")%><%
        Set<Integer> values = bean.spids.get(t);
        if (values.size() > 0)
        {
            %>  DB Connection SPID(s): <%= h(StringUtils.join(values, ", "))%> <%
        }

        for (StackTraceElement e : bean.stackTraces.get(t))
        {
            %><%= h("    at " + e  + "\n") %><%
        }
    }
    catch (Exception x)
    {
        %><strong class=labkey-error><%=h((x.getMessage()!=null?x.getMessage():x.toString()) + "\n")%></strong><%
    }
    %></pre><%
}
%>

