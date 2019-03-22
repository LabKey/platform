<%
/*
 * Copyright (c) 2009-2017 LabKey Corporation
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
<%@ page import="org.labkey.api.util.GUID" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="java.util.Iterator" %>
<%@ page import="java.util.Map" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<% /* DO NOT ADD DEPENDENCIES HERE, WOULD END UP LOADING WITH EACH DATA REGION */ %>
<%
    JspView<Map<String, ActionURL>> me = (JspView<Map<String, ActionURL>>) HttpView.currentView();
    Map<String, ActionURL> map = me.getModelBean();
    String guid = GUID.makeGUID();
    boolean first = true;
%>
<script type="text/javascript">
    /**
     * Given a radio button, determine which one in the group is selected and return its value
     * @param radioButton one of the radio buttons in the group
     */
    var getRadioButtonValue = function(radioButton) {
        if (radioButton.form && radioButton.name)
        {
            var radioButtonElements = radioButton.form.elements[radioButton.name];
            for (var i = 0; i < radioButtonElements.length; i++)
            {
                if (radioButtonElements[i].checked)
                {
                    return radioButtonElements[i].value;
                }
            }
        }
    };
</script>
<table class="lk-fields-table">
    <%
    int columns = (int)Math.round(map.size() / 2.0);    // Put all the script languages into two rows, and use as many columns as needed
    Iterator<Map.Entry<String, ActionURL>> iter = map.entrySet().iterator();

    for (int i = 0; i < 2; i++)
    {
        %><tr><%

        for (int j = 0; j < columns; j++)
        {
            if (iter.hasNext())
            {
                Map.Entry<String, ActionURL> entry = iter.next();
            %>
                <td valign="center"><input type="radio" <%= first ? "id=\"" + guid + "\"" : "" %> name="scriptExportType"<%=checked(first)%> value="<%=h(entry.getValue()) %>"/></td>
                <td valign="center"><%= h(entry.getKey())%></td><%

                first = false;
            }
        }

        %></tr><%
    } %>
    <tr>
        <td colspan="6">
            <br>
            <%= button("Create Script").primary(true).onClick("window.open(getRadioButtonValue(document.getElementById(\"" + guid + "\")), \"_blank\"); return false;") %>
        </td>
    </tr>
</table>

