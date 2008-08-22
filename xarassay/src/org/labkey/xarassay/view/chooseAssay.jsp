<%
/*
 * Copyright (c) 2007-2008 LabKey Corporation
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
<%@ page import="org.labkey.api.exp.api.ExpProtocol" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.xarassay.XarChooseAssayForm" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.labkey.xarassay.XarAssayController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<XarChooseAssayForm> me = (JspView<XarChooseAssayForm>) HttpView.currentView();
    List<ExpProtocol> protocols = me.getModelBean().getAvailableProtocols();
    Map<String, String> lnks = me.getModelBean().getLinks();


%>
    <div>
        <br/>
<table>
        <tr>
            <td colspan="2" align='center'>Select the Assay definition to apply to these files:</td>
        </tr>
        <%
        for (Map.Entry<String, String> entry : lnks.entrySet())
        {
            boolean active = false;
            %>
             <tr>
                 <td align="right"><b><%= entry.getKey() %></b>&nbsp; &nbsp; </td>
                 <td align="left"> <%= generateButton("select", entry.getValue()) %> </td>
              </tr>
        <% } %>
    <tr>
        <td height="10" colspan="2">&nbsp;</td>
        </tr><tr>

         <td colspan="2" align="left">
         <%=generateButton("New Assay",new ActionURL("assay", "chooseAssayType", me.getModelBean().getContainer()) )%>
            &nbsp;
             <%=generateButton("Cancel",new ActionURL("Pipeline", "returnToReferer", me.getModelBean().getContainer()) )%>
        </td>


     </tr>
 
</table>
</div >


