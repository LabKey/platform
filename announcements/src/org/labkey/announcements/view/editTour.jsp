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
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%!
    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
        resources.add(ClientDependency.fromPath("announcements/EditTour.css"));
        resources.add(ClientDependency.fromPath("announcements/EditTour.js"));
        resources.add(ClientDependency.fromPath("vis/baseExportScriptPanel.js"));
        resources.add(ClientDependency.fromPath("internal/jQuery"));
        resources.add(ClientDependency.fromPath("codemirror"));
        return resources;
    }
%>
<%
    JspView<TourModel> me = (JspView<TourModel>) HttpView.currentView();
    TourModel model = me.getModelBean();
%>

<span class="labkey-nav-page-header">Tour Builder</span>

<labkey:form name="editTour" method="post">
    <div class="uxtour"><input id="tour-rowid" hidden="true" value="<%=h(null!=model?model.getRowId():"")%>">
        <div class="tablewrapper">
            <div class="leftcolumn" id="leftcolumn">
                <div class="col-width row">
                    <div>
                        <label class="label" for="tour-title">Title</label>
                    </div>
                    <div>
                        <input class="input" type="text" name="tour-title" id="tour-title" value="<%=h(null!=model?model.getTitle():"")%>">
                    </div>
                </div>
                <div class="col-width row">
                    <div>
                        <label class="label" for="tour-description">Description</label>
                    </div>
                    <div>
                        <textarea rows="20" cols="65" id="tour-description"><%=h(null!=model?model.getDescription():"")%></textarea>
                    </div>
                </div>
                <div id="mode-dummy"><input id="mode-dummy-hidden" hidden="true" value="<%=h(null!=model?model.getMode():"")%>"></div>
                <div class="col-width button-row">
                    <%= button("Save").submit(false).attributes("id='tour-button-save' class='button'") %>
                    <%= button("Save & Close").submit(false).attributes("id='tour-button-save-close' class='button'") %>
                    <%= button("Clear").submit(false).attributes("id='tour-button-clear' class='button'") %>
                    <%= button("Cancel").submit(false).attributes("id='tour-button-cancel' class='button'") %>
                    <%= button("Import").submit(false).attributes("id='tour-button-import' class='button'") %>
                    <%= button("Export").submit(false).attributes("id='tour-button-export' class='button'") %>
                    <%= button("Add Step").submit(false).attributes("id='tour-button-add-step' class='button'") %>
                </div>
            </div>

            <div class="rightcolumn" id="rightcolumn">
                <div id="dummy"><input id="dummy-hidden" hidden="true" value="<%=h(null!=model ?model.targetStepObject():"")%>"></div>
            </div>
        </div>
    </div>
</labkey:form>