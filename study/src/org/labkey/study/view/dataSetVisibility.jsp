<%
/*
 * Copyright (c) 2006-2014 LabKey Corporation
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
<%@ page import="org.labkey.api.reports.model.ViewCategory" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.study.model.CohortImpl" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%!
  public LinkedHashSet<ClientDependency> getClientDependencies()
  {
      LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
      resources.add(ClientDependency.fromFilePath("dataviews"));
      return resources;
  }
%>
<%
    List<CohortImpl> cohorts = StudyManager.getInstance().getCohorts(getStudy().getContainer(), getUser());
    JspView<Map<Integer,StudyController.DatasetVisibilityData>> me = (JspView<Map<Integer,StudyController.DatasetVisibilityData>>) HttpView.currentView();
    Map<Integer,StudyController.DatasetVisibilityData> bean = me.getModelBean();
    ArrayList<Integer> emptyDatasets = new ArrayList<>();

    String storeId = "dataset-visibility-category-store";
    ObjectMapper jsonMapper = new ObjectMapper();

    List<Map<String, Object>> datasetInfo = new ArrayList<>();
    for (Map.Entry<Integer, StudyController.DatasetVisibilityData> entry : bean.entrySet())
    {
        Map<String, Object> ds = new HashMap<>();
        ViewCategory category = entry.getValue().viewCategory;

        ds.put("id", entry.getKey() + "-viewcategory");
        ds.put("categoryId", category != null ? category.getRowId() : null);

        datasetInfo.add(ds);
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

<form action="<%=h(buildURL(StudyController.DatasetVisibilityAction.class))%>" method="POST">

<p>Datasets can be hidden on the study overview screen.</p>
<p>Hidden data can always be viewed, but is not shown by default.</p>
    <table>
        <tr>
            <th align="left">ID</th>
            <th align="left">Label</th>
            <th align="left">Category</th>
            <th align="left">Cohort</th>
            <th align="left">Status</th>
            <th align="left">Visible</th>
            <th>&nbsp;</th>
        </tr>
    <%
        for (Map.Entry<Integer, StudyController.DatasetVisibilityData> entry : bean.entrySet())
        {
            int id = entry.getKey().intValue();
            StudyController.DatasetVisibilityData data = entry.getValue();
            if (data.empty)
                emptyDatasets.add(id);
    %>
        <tr>
            <td><%= id %></td>
            <td>
                <input type="text" size="20" name="label" value="<%= h(data.label != null ? data.label : "") %>">
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
                    <select name="cohort">
                        <option value="-1">All</option>
                    <%

                        for (CohortImpl cohort : cohorts)
                        {
                    %>
                        <option value="<%= cohort.getRowId()%>"<%=selected(data.cohort != null && data.cohort == cohort.getRowId()) %>>
                            <%= h(cohort.getLabel())%>
                        </option>
                    <%
                        }
                    %>
                    </select>
                    <%
                    }
                %>
            </td>
            <td>
                <select name="statuses">
                    <option value=""<%=selected(data.status == null || data.status.equals("None"))%>>None</option>
                    <option value="Draft"<%=selected(data.status != null && data.status.equals("Draft"))%>>Draft</option>
                    <option value="Final"<%=selected(data.status != null && data.status.equals("Final"))%>>Final</option>
                    <option value="Locked"<%=selected(data.status != null && data.status.equals("Locked"))%>>Locked</option>
                    <option value="Unlocked"<%=selected(data.status != null && data.status.equals("Unlocked"))%>>Unlocked</option>
                </select>
            </td>
            <td align="center">
                <input type="checkbox" name="visible"<%=checked(data.visible)%> value="<%= id %>">
                <input type="hidden" name="ids" value="<%= id %>">
            </td>
            <td><%= text(data.empty ? "empty" : "&nbsp;") %></td>
        </tr>
    <%
        }
    %>
    </table>
    <%= button("Save").submit(true) %>&nbsp;
    <%= button("Cancel").href(StudyController.ManageTypesAction.class, getContainer()) %>&nbsp;
    <%= button("Manage Categories").href("javascript:void(0);").onClick("onManageCategories()") %>
    <% if (!emptyDatasets.isEmpty()) { %>
    <%= button("Hide empty datasets").href("javascript:void(0);").onClick("onHideEmptyDatasets()") %>
    <% } %>
</form>
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
        var checkboxes = Ext.select("INPUT[name=visible]").elements;
        for (var i=0 ; i<checkboxes.length ; i++)
        {
            var checkbox = checkboxes[i];
            if (emptyDatasets[checkbox.value])
                checkbox.checked = false;
        }
    }


    Ext4.onReady(function()
    {
        var datasetInfo = <%=text(jsonMapper.writeValueAsString(datasetInfo))%>;
        var store = LABKEY.study.DataViewUtil.getViewCategoriesStore({storeId : '<%=h(storeId)%>'});

        for (var i=0; i < datasetInfo.length; i++)
        {
            var ds = datasetInfo[i];
            Ext4.create('Ext.form.field.ComboBox', {
                name        : 'category',
                hiddenName  : 'extraData',
                store       : store,
                typeAhead   : true,
                typeAheadDelay : 75,
                renderTo       : ds.id,
                minChars       : 1,
                autoSelect     : false,
                queryMode      : 'remote',
                displayField   : 'label',
                valueField     : 'rowid',
                value          : ds.categoryId || '',
                emptyText      : 'Uncategorized',
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
                )
            });
        }
    });

</script>

