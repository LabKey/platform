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
        %><b color=red><%=x.getMessage()%></b><%
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
        %><b color=red><%=(x.getMessage()!=null?x.getMessage():x.toString()) + "\n"%></b><%
    }
    %></pre><%
}
%>

