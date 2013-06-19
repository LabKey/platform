<%
/*
 * Copyright (c) 2009-2012 LabKey Corporation
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
<%@ page import="org.labkey.api.data.TSVWriter" %>
<%@ page import="org.labkey.api.util.GUID" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="java.util.LinkedHashMap" %>
<%@ page import="java.util.Map" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    String delimGUID = GUID.makeGUID();
    String quoteGUID = GUID.makeGUID();

    Map<String, String> delimiterMap = new LinkedHashMap<>();
    delimiterMap.put(TSVWriter.DELIM.TAB.name(), TSVWriter.DELIM.TAB.text);
    delimiterMap.put(TSVWriter.DELIM.COMMA.name(), TSVWriter.DELIM.COMMA.text);
    delimiterMap.put(TSVWriter.DELIM.COLON.name(), TSVWriter.DELIM.COLON.text);
    delimiterMap.put(TSVWriter.DELIM.SEMICOLON.name(), TSVWriter.DELIM.SEMICOLON.text);

    Map<String, String> quoteMap = new LinkedHashMap<>();
    quoteMap.put(TSVWriter.QUOTE.DOUBLE.name(), "Double (" + TSVWriter.QUOTE.DOUBLE.quoteChar + ")");
    quoteMap.put(TSVWriter.QUOTE.SINGLE.name(), "Single (" + TSVWriter.QUOTE.SINGLE.quoteChar + ")");

    ActionURL url = (ActionURL)HttpView.currentModel();

    String onClickScript = "window.location='" + url + "&delim=' + document.getElementById('" + delimGUID + "').value + '&quote=' + document.getElementById('" + quoteGUID + "').value;return false;";
%>
<table class="labkey-export-tab-contents">
    <tr>
        <td>Separator:</td>
        <td>
            <select id="<%=delimGUID%>" name="delim">
                <labkey:options value="<%=TSVWriter.DELIM.TAB%>" map="<%=delimiterMap%>" />
            </select>
        </td>
    </tr>
    <tr>
        <td>Quote:</td>
        <td>
            <select id="<%=quoteGUID%>" name="quote">
                <labkey:options value="<%=TSVWriter.QUOTE.DOUBLE%>" map="<%=quoteMap%>" />
            </select>
        </td>
    </tr>
    <tr>
        <td colspan=2>
            <%=PageFlowUtil.generateButton("Export to Text", "", onClickScript, "rel=\"nofollow\"") %>
        </td>
    </tr>
</table>
