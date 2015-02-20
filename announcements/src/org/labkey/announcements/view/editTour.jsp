<%
    /*
     * Copyright (c) 2008-2015 LabKey Corporation
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
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page import="org.labkey.announcements.model.TourModel" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%!
    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
        resources.add(ClientDependency.fromPath("internal/jQuery"));
        resources.add(ClientDependency.fromPath("Ext4"));
        resources.add(ClientDependency.fromPath("codemirror"));
        resources.add(ClientDependency.fromPath("announcements/EditTour.css"));
        resources.add(ClientDependency.fromPath("announcements/EditTour.js"));
        return resources;
    }
%>
<%
    JspView<TourModel> me = (JspView<TourModel>) HttpView.currentView();
    TourModel model = me.getModelBean();
%>

<script type="text/javascript">
    LABKEY._tour.title = <%=PageFlowUtil.jsString(model.getTitle())%>;
    LABKEY._tour.description = <%=PageFlowUtil.jsString(model.getDescription())%>;
    LABKEY._tour.mode = <%=model.getMode()%>;
    LABKEY._tour.json = <%=model.toJSON()%>;
    LABKEY._tour.rowId = <%=model.getRowId()%>;
</script>

<labkey:form name="editTour" method="post">
    <div class="uxtour">
        <div class="tablewrapper">
            <div class="leftcolumn">
                <div id="status" class="labkey-status-info" style="display: none;"></div>
                <div class="col-width button-row">
                    <%= button("Save").id("tour-button-save") %>
                    <%= button("Save & Close").id("tour-button-save-close") %>
                    <%= button("Cancel").id("tour-button-cancel") %>
                    <%= button("Clear").id("tour-button-clear") %>
                    <%= button("Add Step").id("tour-button-add-step") %>
                    <%= button("Import").id("tour-button-import") %>
                    <%= button("Export").id("tour-button-export") %>
                </div>
            </div>
            <div class="rightcolumn" id="rightcolumn">
                <div id="dummy"></div>
            </div>
        </div>
    </div>
</labkey:form>