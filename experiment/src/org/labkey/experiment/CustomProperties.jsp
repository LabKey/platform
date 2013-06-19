<%
/*
 * Copyright (c) 2005-2012 LabKey Corporation
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
List<List<ObjectProperty>> stack = new ArrayList<>();
stack.add(new ArrayList<>(form.getCustomProperties().values()));
List<Integer> indices = new ArrayList<>();
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
                <td class="labkey-form-label"><% int i = 0; while(i < stack.size() - 1) { i++; %>&nbsp;&nbsp;&nbsp;&nbsp;<% } %><%= text(renderer.getDescription(value, values)) %></td>
                <td><%= text(renderer.getValue(value, values, me.getViewContext())) %></td>
            </tr> <%
        }
        if (value.retrieveChildProperties().size() > 0)
        {
            stack.add(new ArrayList<>(value.retrieveChildProperties().values()));
            indices.add(0);
        }
    }
} %>
</table>