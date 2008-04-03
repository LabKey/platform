<%@ page import="org.labkey.api.exp.ObjectProperty"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.experiment.CustomPropertiesView" %>
<%@ page import="org.labkey.experiment.CustomPropertyRenderer" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.List" %>

<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<CustomPropertiesView.CustomPropertiesBean> me = (JspView<CustomPropertiesView.CustomPropertiesBean>) HttpView.currentView();
    CustomPropertiesView.CustomPropertiesBean form = me.getModelBean();
%>

<table>
<%
List<List<ObjectProperty>> stack = new ArrayList<List<ObjectProperty>>();
stack.add(new ArrayList<ObjectProperty>(form.getCustomProperties().values()));
List<Integer> indices = new ArrayList<Integer>();
indices.add(0);

while (!stack.isEmpty())
{
    List<ObjectProperty> values = stack.get(stack.size() - 1);
    int currentIndex = indices.get(indices.size() - 1);
    indices.set(indices.size() - 1, currentIndex + 1);

    if (currentIndex == values.size())
    {
        stack.remove(stack.size() - 1);
        indices.remove(indices.size() - 1);
    }
    else
    {
        ObjectProperty value = values.get(currentIndex);
        CustomPropertyRenderer renderer = form.getRenderers().get(value.getPropertyURI());
        if (renderer.shouldRender(value, values)) { %>
            <tr>
                <td class="ms-searchform"><% int i = 0; while(i < stack.size() - 1) { i++; %>&nbsp;&nbsp;&nbsp;&nbsp;<% } %><%= renderer.getDescription(value, values) %></td>
                <td><%= renderer.getValue(value, values, me.getViewContext()) %></td>
            </tr> <%
        }
        if (value.retrieveChildProperties().size() > 0)
        {
            stack.add(new ArrayList(value.retrieveChildProperties().values()));
            indices.add(0);
        }
    }
} %>
</table>