<%
/*
 * Copyright (c) 2011 LabKey Corporation
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
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<table>
    <tr><td>
        A JavaScript view contains code that runs in the viewing user's browser.  Your code can access the underlying
        data, transform it, and render any visualization of that data (e.g., a chart, grid, or summary report) to the
        page.
    </td></tr>
    <tr><td>&nbsp;</td></tr>
    <tr><td>
        Your code must define a render() function.  When a user views the report, LabKey calls this render() function,
        passing a query config and a div element.  You can pass the query config directly to LABKEY.Query.selectRows() to
        retrieve the data, or you can modify the query config (adding/removing filters, adding columns, etc.) before
        passing it to selectRows().  Render your view's HTML to the div passed into the render() function. 
    </td></tr>
    <tr><td>&nbsp;</td></tr>
    <tr><td>
        Your JavaScript code is wrapped in an anonymous function, which provides unique scoping for the functions and
        variables you define; these identifiers will not conflict with identifiers in other JavaScript views rendered
        on the same page. 
    </td></tr>
</table>
