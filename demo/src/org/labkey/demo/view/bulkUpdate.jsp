<%
/*
 * Copyright (c) 2006-2008 LabKey Corporation
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
<%@ page import="org.labkey.demo.model.Person"%>
<%@ page import="org.springframework.validation.BindException"%>
<%@ page import="org.springframework.validation.ObjectError" %>
<%@ page import="java.util.HashSet" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Set" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    /*
    JspView<BulkUpdatePage> me = (JspView<BulkUpdatePage>) HttpView.currentView();
    ViewContext context = me.getViewContext();

    /* This is one way to pass parameters, which we usually use
    BulkUpdatePage pageInfo = me.getModelBean();
    BindException errors = me.getErrors();
    List<org.labkey.demo.model.Person> people = pageInfo.getList();
    */

    // here is typical struts/spring way
    BindException errors = (BindException)request.getAttribute("errors");
    List<Person> people = (List<Person>)request.getAttribute("people");

    if (null != errors)
    {
        // since we're don't showing errors field-by-field don't show duplicates
        Set<String> messages = new HashSet<String>();
        for (ObjectError e : (List<ObjectError>) errors.getAllErrors())
        {
            String message = getMessage(e);
            if (messages.add(message))
            {
                %><font color=red><%=h(message)%></font><br><%
            }
        }
    }

    if (people.size() > 0)
    {
%>
    <form action="bulkUpdate.post" method="POST">
        <table class="normal">
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
                            <input type="text" size="20" name="firstName" value="<%= firstName %>">
                        </td>
                        <td>
                            <input type="text" size="20" name="lastName" value="<%= lastName %>">
                        </td>
                        <td>
                            <input type="text" size="5" name="age" value="<%= age %>">
                        </td>
                    </tr>
                    <%
                }
            %>
        </table>
        <%= buttonImg("Save") %>&nbsp;<%= buttonLink("Cancel", "begin.view") %>
    </form>
<%
    }
    else
    {
%>
    <span class="normal">There is no data to update.</span><br>
    <%= buttonLink("Grid View", "begin.view") %>
<%
    }
%>