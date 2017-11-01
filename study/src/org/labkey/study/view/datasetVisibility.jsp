<%
/*
 * Copyright (c) 2006-2017 LabKey Corporation
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
<%@ page import="com.fasterxml.jackson.databind.ObjectMapper"%>
<%@ page import="org.labkey.api.study.Study" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.study.model.CohortImpl" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.LinkedHashMap" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%!
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("dataviews");
    }
%>
<%
    JspView<Map<Integer,StudyController.DatasetVisibilityData>> me = (JspView<Map<Integer,StudyController.DatasetVisibilityData>>) HttpView.currentView();

    Study study = getStudy();
    Study sharedStudy = StudyManager.getInstance().getSharedStudy(study);

    List<CohortImpl> cohorts = StudyManager.getInstance().getCohorts(study.getContainer(), getUser());
    Map<Integer,StudyController.DatasetVisibilityData> bean = me.getModelBean();
    ArrayList<Integer> emptyDatasets = new ArrayList<>();

    String storeId = "dataset-visibility-category-store";
    ObjectMapper jsonMapper = new ObjectMapper();

    List<Map<String, Object>> datasetInfo = new ArrayList<>();
    for (Map.Entry<Integer, StudyController.DatasetVisibilityData> entry : bean.entrySet())
    {
        Map<String, Object> ds = new HashMap<>();
        Integer categoryId = entry.getValue().categoryId;

        ds.put("id", entry.getKey());
        ds.put("categoryId", categoryId);
        ds.put("inherited", entry.getValue().inherited);

        datasetInfo.add(ds);
    }

    Map<String, String> statusOpts = new LinkedHashMap<>();
    statusOpts.put(null, "None");
    statusOpts.put("Draft", "Draft");
    statusOpts.put("Final", "Final");
    statusOpts.put("Locked", "Locked");
    statusOpts.put("Unlocked", "Unlocked");

    Map<Integer, String> cohortOpts = new LinkedHashMap<>();
    cohortOpts.put(null, "All");
    for (CohortImpl cohort : cohorts)
    {
        cohortOpts.put(cohort.getRowId(), cohort.getLabel());
    }

%>


<labkey:errors/>

<%
    if (bean.entrySet().size() == 0)
    {
        ActionURL createURL = new ActionURL(StudyController.DefineDatasetTypeAction.class, getContainer());
        createURL.addParameter("autoDatasetId", "true");
%>
    No datasets have been created in this study.<br><br>
    <%= button("Create New Dataset").href(createURL) %>&nbsp;<%= button("Cancel").href(StudyController.ManageTypesAction.class, getContainer()) %>
<%
    }
    else
    {
%>

<labkey:form action="<%=h(buildURL(StudyController.DatasetVisibilityAction.class))%>" method="POST">

<p>Datasets can be hidden on the study overview screen.</p>
<p>Hidden data can always be viewed, but is not shown by default.</p>
    <table>
        <thead>
            <tr>
                <th align="left">ID</th>
                <th align="left">Label</th>
                <th align="left">Category</th>
                <th align="left">Cohort</th>
                <th align="left">Status</th>
                <th align="left">Visible</th>
                <th>&nbsp;</th>
            </tr>
        </thead>
    <%
        for (Map.Entry<Integer, StudyController.DatasetVisibilityData> entry : bean.entrySet())
        {
            int id = entry.getKey().intValue();
            StudyController.DatasetVisibilityData data = entry.getValue();
            if (data.empty)
                emptyDatasets.add(id);
    %>
        <tr data-datasetid="<%=id%>">
            <td><%= id %></td>
            <td>
                <input type="text" size="20" name="<%="dataset[" + id + "].label"%>" value="<%= h(data.label != null ? data.label : "") %>" placeholder="Dataset label required" <%=readonly(data.inherited)%>>
            </td>
            <td>
                <div id="<%=h(id + "-viewcategory")%>"></div>
            </td>
            <td>
                <%
                    if (cohorts == null || cohorts.size() == 0)
                    {
                %>
                    <em>No cohorts defined</em>
                <%
                    }
                    else
                    {
                    %>
                    <select name="<%="dataset[" + id + "].cohort"%>" <%=disabled(data.inherited)%>>
                        <labkey:options value="<%=data.cohort%>" map="<%=cohortOpts%>"/>
                    </select>
                    <%
                    }
                %>
            </td>
            <td>
                <select name="<%="dataset[" + id + "].status"%>" <%=disabled(data.inherited)%>>
                    <labkey:options value="<%=data.status%>" map="<%=statusOpts%>"/>
                </select>
            </td>
            <td align="center">
                <labkey:checkbox name='<%="dataset[" + id + "].visible"%>' id='<%="dataset[" + id + "].visible"%>' value="true" checked="<%=data.visible%>"/>
            </td>
            <td><%= text(data.empty ? "empty" : "&nbsp;") %></td>
        </tr>
    <%
        }
    %>
    </table>
    <p>
    <%= button("Save").submit(true) %>&nbsp;
    <%= button("Cancel").href(StudyController.ManageTypesAction.class, getContainer()) %>&nbsp;
    <% if (sharedStudy == null) { %>
    <%= button("Manage Categories").href("javascript:void(0);").onClick("onManageCategories()") %>
    <% } %>
    <% if (!emptyDatasets.isEmpty()) { %>
    <%= button("Hide empty datasets").href("javascript:void(0);").onClick("onHideEmptyDatasets()") %>
    <% } %>
    <% if (sharedStudy != null /*&& overrides exist in this container*/) { %>
    <%= button("Reset Overrides").href("javascript:void(0);").onClick("onResetOverrides()") %>
    <% } %>
</labkey:form>
<%
    }
%>

<script type="text/javascript">

    function onManageCategories()
    {
        Ext4.onReady(function(){
            var window = LABKEY.study.DataViewUtil.getManageCategoriesDialog();

            window.on('afterchange', function(cmp){
                var store = Ext4.data.StoreManager.lookup('<%=h(storeId)%>');
                if (store)
                    store.load();
                else
                    location.reload();
            }, this);
            window.show();
        });
    }


    function onHideEmptyDatasets()
    {
        var emptyDatasets =
        {
            <%
            String comma="";
            for (int id:emptyDatasets)
            {
                %><%=text(comma)%>"<%=id%>":true<%
                comma = ",";
            }
            %>
        };
        for (var id in emptyDatasets) {
            if (!emptyDatasets.hasOwnProperty(id))
                continue;
            var checkbox = document.getElementById("dataset[" + id + "].visible");
            if (checkbox)
                checkbox.checked = false;
        }
    }


    function onResetOverrides()
    {
        Ext4.Msg.confirm("Reset all dataset overrides in this folder?",
                "Are you sure you want to reset all dataset overides in the current folder?",
                function (btn) {
                    if (btn == "yes") {
                        LABKEY.Ajax.request({
                            method: 'POST',
                            url: LABKEY.ActionURL.buildURL('study', 'deleteDatasetPropertyOverride.api'),
                            jsonData: {},
                            success: function () {
                                // reshow the page
                                window.location.reload(true);
                            },
                            // Show generic error message
                            failure: LABKEY.Utils.getCallbackWrapper(null, null, true)
                        })
                    }
                }
        );
    }

    Ext4.onReady(function()
    {
        var datasetInfo = <%=text(jsonMapper.writeValueAsString(datasetInfo))%>;
        var store = LABKEY.study.DataViewUtil.getViewCategoriesStore({
            storeId : '<%=h(storeId)%>',
            container: <%=q((sharedStudy != null ? sharedStudy.getContainer() : study.getContainer()).getPath())%>
        });

        for (var i=0; i < datasetInfo.length; i++)
        {
            var ds = datasetInfo[i];
            Ext4.create('Ext.form.field.ComboBox', {
                name           : 'dataset[' + ds.id + '].categoryLabel',
                hiddenName     : 'dataset[' + ds.id + '].categoryId',
                store          : store,
                typeAhead      : true,
                typeAheadDelay : 75,
                renderTo       : ds.id + "-viewcategory",
                minChars       : 1,
                autoSelect     : false,
                queryMode      : 'remote',
                displayField   : 'label',
                valueField     : 'rowid',
                value          : ds.categoryId || '',
                emptyText      : 'Uncategorized',
                disabled       : ds.inherited,
                tpl : new Ext4.XTemplate(
                    '<ul><tpl for=".">',
                        '<li role="option" class="x4-boundlist-item">',
                            '<tpl if="parent &gt; -1">',
                                '<span style="padding-left: 20px;">{label:htmlEncode}</span>',
                            '</tpl>',
                            '<tpl if="parent &lt; 0">',
                                '<span>{label:htmlEncode}</span>',
                            '</tpl>',
                        '</li>',
                    '</tpl></ul>'
                ),
                listeners: {
                    change: function () {
                        if (this.getValue() === null) {
                            this.clearValue();
                        }
                    }
                }
            });
        }
    });

</script>

