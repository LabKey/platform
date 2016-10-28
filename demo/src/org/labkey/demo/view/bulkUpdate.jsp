<%
/*
 * Copyright (c) 2006-2016 LabKey Corporation
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
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView"%>
<%@ page import="org.labkey.demo.model.Person" %>
<%@ page import="org.springframework.validation.Errors" %>
<%@ page import="org.springframework.validation.ObjectError" %>
<%@ page import="java.util.HashSet" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Set" %>
<%@ page import="org.labkey.demo.DemoController" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<List<Person>> me = (JspView<List<Person>>) HttpView.currentView();
    Errors errors = getErrors("form");
    List<Person> people = me.getModelBean();

    if (null != errors)
    {
        // since we're not showing errors field-by-field don't show duplicates
        Set<String> messages = new HashSet<>();
        for (ObjectError e : errors.getAllErrors())
        {
            String message = getMessage(e);
            if (messages.add(message))
            {
                %><font class="labkey-error"><%=h(message)%></font><br><%
            }
        }
    }

    if (people.size() > 0)
    {
%>
    <labkey:form action="bulkUpdate.post" method="POST">
        <table>
            <tr>
                <th>First Name</th>
                <th>Last Name</th>
                <th>Age</th>
            </tr>
            <%
                for (Person person : people)
                {
                    String firstName = person.getFirstName() != null ? person.getFirstName() : "";
                    String lastName = person.getLastName() != null ? person.getLastName() : "";
                    String age = person.getAge() != null ? person.getAge().toString() : "";
                    %>
                    <tr>
                        <td>
                            <input type="hidden" name="rowId" value="<%= person.getRowId() %>">
                            <input type="text" size="20" name="firstName" value="<%= h(firstName) %>">
                        </td>
                        <td>
                            <input type="text" size="20" name="lastName" value="<%= h(lastName) %>">
                        </td>
                        <td>
                            <input type="text" size="5" name="age" value="<%= h(age) %>">
                        </td>
                    </tr>
                    <%
                }
            %>
        </table>
        <%= button("Save").submit(true) %>&nbsp;<%= button("Cancel").href(DemoController.BeginAction.class, getContainer()) %>
    </labkey:form>
<%
    }
    else
    {
%>
    <span>There is no data to update.</span><br>
    <%= button("Grid View").href(DemoController.BeginAction.class, getContainer()) %>
<%
    }
%>