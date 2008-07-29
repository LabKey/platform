<%
/*
 * Copyright (c) 2005-2008 LabKey Corporation
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
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.core.admin.AdminController" %>
<%@ page import="java.util.Set" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    HttpView<AdminController.ThreadsBean> me = (HttpView<AdminController.ThreadsBean>) HttpView.currentView();
    AdminController.ThreadsBean bean = me.getModelBean();

%>
<p><b>Currently Running Threads</b></p>
<%
for (Thread t : bean.threads)
{
    try
    {
    %><a href="#<%=h(t.getName())%>"><b><%= t.getName() %></b></a> (<%= t.getState() %>)<%
    Set<Integer> values = bean.spids.get(t);
    if (values.size() > 0)
    {
        %><font class="labkey-error">DB Connection SPID(s): <%
        String separator = "";
        for (Integer value : values)
        { %>
            <%= separator %> <%= value %>
            <%
            separator = ", ";
        }
        %></font><%
    }
    }
    catch (Exception x)
    {
        %><b class=labkey-error><%=x.getMessage()%></b><%
    }
    %><br/><%
}

%><hr/><%

for (Thread t : bean.threads)
{
    try
    {
        %><a name="<%= t.getName() %>"></a><%
        %><pre><%= t.getName() %> (<%= t.getState() %>)<%="\n"%><%
        Set<Integer> values = bean.spids.get(t);
        if (values.size() > 0)
        {
            %>  DB Connection SPID(s): <%
            String separator = "";
            for (Integer value : values)
            { %><%= separator %><%= value %><%
                separator = ", ";
            }
        }

        for (StackTraceElement e : t.getStackTrace())
        {
            %><%= "    at " + e  + "\n" %><%
        }
    }
    catch (Exception x)
    {
        %><b class=labkey-error><%=(x.getMessage()!=null?x.getMessage():x.toString()) + "\n"%></b><%
    }
    %></pre><%
}
%>

