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
<%@ page import="org.labkey.api.view.*" %>
<%@ page import="org.labkey.workbook.WorkbookController" %>
<%@ page import="org.labkey.api.exp.api.ExpProtocol" %>
<%@ page import="org.labkey.api.study.assay.AssayService" %>
<%@ page import="org.apache.commons.collections15.MultiMap" %>
<%@ page import="org.labkey.api.study.assay.AssayProvider" %>
<%@ page import="org.apache.commons.collections15.multimap.MultiHashMap" %>
<%@ page import="java.util.*" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<WorkbookController.CreateWorkbookForm> me = (JspView<WorkbookController.CreateWorkbookForm>) HttpView.currentView();
    WorkbookController.CreateWorkbookForm form = me.getModelBean();
    ViewContext ctx = me.getViewContext();
%>
<form action="createWorkbook.post" method="POST">
Experiment Name <input type="text" name="name"><br><br>
    Experiment Description<br>
    <textarea rows="10" cols="80" name="description"></textarea>
    <br>
    <br>
<%
    Collection<ExpProtocol> allProtocols = AssayService.get().getAssayProtocols(ctx.getContainer());
    if (null != allProtocols && allProtocols.size() > 0)
    { %>
    <h3>Assays</h3>Choose the set of assays you will use in this workbook. You can change this later.<div style="width:100%"> <%
        MultiMap<AssayProvider,ExpProtocol> providerProtocols = new MultiHashMap<AssayProvider,ExpProtocol>();
        for (ExpProtocol protocol : allProtocols)
            providerProtocols.put(AssayService.get().getProvider(protocol), protocol);

        SortedSet<AssayProvider> providers = new TreeSet<AssayProvider>(new Comparator<AssayProvider>(){
            public int compare(AssayProvider o, AssayProvider o1)
            {
                return o.getName().compareTo(o1.getName());
            }
        });
        providers.addAll(providerProtocols.keySet());
        for (AssayProvider provider : providers)
        {
    %>
        <div style="display:inline-block;vertical-align:top;padding-right:2em">    <b><%=h(provider.getName())%></b><br>
        <%

            for (ExpProtocol protocol : providerProtocols.get(provider))
            {
        %>
             <input type="checkbox" name="protocolIDs" value="<%=protocol.getRowId()%>"><%=h(protocol.getName())%><br>
        <%
            }%>
            </div><%
        }%>
        </div><span style="clear:both"></span>
    <%
    }
    %>
    <%=generateSubmitButton("Create Workbook")%>
    </form>
