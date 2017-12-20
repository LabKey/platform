<%
    /*
     * Copyright (c) 2017 LabKey Corporation
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
<table class="table-condensed">
    <tr>
        <td colspan=2><b>Markdown Formatting Guide</b> (<a href="https://markdown-it.github.io/" target="_blank">more help</a>)</td>
    </tr>
    <tr>
        <td>Headers</td>
        <td># H1 | ## H2 | ### H3</td>
    </tr>
    <tr>
        <td>Bold Text</td>
        <td>**use double asterisks**</td>
    </tr>
    <tr>
        <td>Italics</td>
        <td>_use underlines_</td>
    </tr>
    <tr>
        <td>links</td>
        <td>[I'm an inline-style link with title](https://www.google.com "Google's Homepage")</td>
    </tr>
    <tr>
        <td>code</td>
        <td>``` js
            var foo = function (bar) {
            return bar++;
            };
            ``` </td>
    </tr>
    <tr>
        <td>lists</td>
        <td>Create a list by starting a line with '+', '-', or '*'</td>
    </tr>
</table>