<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.labkey.api.study.actions.AssayRunUploadForm" %>
<%@ page import="org.labkey.api.exp.PropertyDescriptor" %>
<%@ page import="org.labkey.api.study.assay.AssayRunUploadContext" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<AssayRunUploadForm> me = (JspView<AssayRunUploadForm>) HttpView.currentView();
    org.labkey.api.study.actions.AssayRunUploadForm bean = me.getModelBean();
%>
<table>
<%
    if (bean.isSuccessfulUploadComplete())
    {
%>
    <tr>
        <td class="heading-1" colspan="2">Upload successful.  Upload another run below, or click Cancel to view previously uploaded runs.</td>
    </tr>
    <tr>
        <td>&nbsp;</td>
    </tr>
<%
    }
%>
    <tr class="wpHeader">
        <td class="wpTitle" colspan="2">Assay Properties</td>
    </tr>
    <tr>
        <td class="ms-searchform" nowrap="true">Name</td>
        <td width="100%"><%= h(bean.getProtocol().getName()) %></td>
    </tr>
    <tr>
        <td class="ms-searchform" nowrap="true">Description</td>
        <td><%= h(bean.getProtocol().getProtocolDescription()) %></td>
    </tr>
    <% if (!bean.getUploadSetProperties().isEmpty())
    { %>
        <tr><td>&nbsp;</td></tr>
        <tr class="wpHeader">
            <td class="wpTitle" colspan="2">Upload Set Properties</td>
        </tr>
        <%
            for (Map.Entry<PropertyDescriptor, String> entry : bean.getUploadSetProperties().entrySet())
            {
                %>
                <tr>
                    <td class="ms-searchform" nowrap="true"><%= h(entry.getKey().getNonBlankLabel()) %></td>
                    <td>
                        <%= h(bean.getUploadSetPropertyValue(entry.getKey(), entry.getValue())) %>
                    </td>
                </tr>
                <%
            }
        }
    %>
    <tr><td>&nbsp;</td></tr>
</table>
