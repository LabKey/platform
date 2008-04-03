<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.springframework.beans.PropertyValues" %>
<%@ page import="org.labkey.api.action.ConfirmAction" %>
<%@ page import="org.springframework.beans.PropertyValue" %>
<%@ page import="java.util.Arrays" %>
<%@ page import="java.util.Collection" %>
<%@ page import="java.io.IOException" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page extends="org.labkey.api.jsp.JspBase"%>
<%
    JspView me = (JspView) HttpView.currentView();
    ViewContext context = me.getViewContext();
    ConfirmAction confirmAction = (ConfirmAction) context.get(ConfirmAction.CONFIRMACTION);
    PropertyValues propertyValues = confirmAction.getPropertyValues();
    ActionURL cancelUrl = confirmAction.getCancelUrl();
%>
<form action="<%=context.getActionURL().getAction()%>.post" method="POST"><%
    me.include(me.getBody(), out);
    writePropertyValues(out, propertyValues);

    %>
<br><input type=image src="<%=PageFlowUtil.buttonSrc(confirmAction.getConfirmText(),"large")%>" name="_confirm" value="<%=confirmAction.getConfirmText()%>">&nbsp;<%
    if (null != cancelUrl)
    {
        %><a href="<%=h(cancelUrl.getLocalURIString())%>"><%=PageFlowUtil.buttonImg(confirmAction.getCancelText(),"large")%></a><%
    }
    else if (confirmAction.isPopupConfirmation())
    {
        %><input type=image src="<%=PageFlowUtil.buttonSrc(confirmAction.getCancelText(),"large")%>" value="Cancel" onclick="window.close(); return false;"><%
    }
    else
    {
        %><input type=image src="<%=PageFlowUtil.buttonSrc(confirmAction.getCancelText(),"large")%>" value="Cancel" onclick="window.history.back(); return false;"><%
    }
%></form>
<%!
    void writePropertyValues(JspWriter out, PropertyValues pvs) throws IOException
    {
        if (pvs == null)
            return;
        for (PropertyValue pv : pvs.getPropertyValues())
        {
            String name = pv.getName();
            Object v = pv.getValue();
            if (v == null)
                continue;
            if (v.getClass().isArray()) // only object arrays
                v = Arrays.asList((Object[]) v);
            if (v instanceof Collection)
            {
                for (Object o : (Collection) v)
                {
                    writeHidden(out, name, o);
                }
            }
            else
            {
                writeHidden(out, name, v);
            }
        }
    }

    void writeHidden(JspWriter out, String name, Object v) throws IOException
    {
        out.print("<input type=\"hidden\" name=\"");
        out.print(PageFlowUtil.filter(name));
        out.print("\" value=\"");
        out.print(PageFlowUtil.filter(String.valueOf(v)));
        out.print("\">");
    }
%>