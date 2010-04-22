<%
/*
 * Copyright (c) 2010 LabKey Corporation
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
<%@ page import="org.apache.commons.lang.StringUtils" %>
<%@ page import="org.labkey.api.search.SearchService" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.services.ServiceRegistry" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.search.SearchController.ClearExternalIndexAction" %>
<%@ page import="org.labkey.search.SearchController.SwapExternalIndexAction" %>
<%@ page import="org.labkey.search.model.ExternalAnalyzer" %>
<%@ page import="org.labkey.search.model.ExternalIndexProperties" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
JspView<ExternalIndexProperties> me = (JspView<ExternalIndexProperties>) HttpView.currentView();
User user = me.getViewContext().getUser();
ExternalIndexProperties props = me.getModelBean();
SearchService ss = ServiceRegistry.get().getService(SearchService.class);
String message = me.getViewContext().getActionURL().getParameter("externalMessage");

%><labkey:errors /><%
if (!StringUtils.isEmpty(message))
{ %><br>
    <span style="color:green;"><%=h(message)%></span><%
}

if (null != ss)
{
    %><p><form method="POST" action="setExternalIndex.post">
        <table>
            <tr><td>Path to external index directory:</td><td><input name="externalIndexPath" value="<%=h(props.getExternalIndexPath())%>"/></td></tr>
            <tr><td>Analyzer to use:</td><td>
                <select name="analyzer"><%
                    String currentAnalyzer = props.getAnalyzer();

                    for (ExternalAnalyzer a : ExternalAnalyzer.values())
                    { %>
                        <option<%=(a.toString().equals(currentAnalyzer) ? " selected" : "")%>><%=a.toString()%></option><%
                    }
                %>
                </select>
            </td></tr><%

            if (user.isAdministrator())
            {
            %>
            <tr><td colspan="2" align="center">
                <%=generateSubmitButton("Set")%>
                <%=generateButton("Swap", SwapExternalIndexAction.class)%>
                <%=generateButton("Clear", ClearExternalIndexAction.class)%>
            </td></tr><%
            }
            %>
        </table>
    </form>
<%
}
%>