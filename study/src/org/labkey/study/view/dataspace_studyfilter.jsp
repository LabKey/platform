<%
/*
 * Copyright (c) 2007-2015 LabKey Corporation
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
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="org.labkey.api.study.Study" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%!
    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
        resources.add(ClientDependency.fromFilePath("clientapi/ext4"));
        return resources;
    }
%>
<%
    Study study = StudyManager.getInstance().getStudy(getViewContext().getContainer());
    int uuid = this.getRequestScopedUID();
%>
<style>
    div.study-list .study-selected
    {
        font-weight:bold;
    }
    div.study-list .study-notselected
    {
        display:none;
    }
</style>
<div id="studyFilterDiv<%=uuid%>">

</div>
<script>
Ext4.onReady(function()
{
    var div = Ext4.get('studyFilterDiv<%=uuid%>');
    var availableStudies = null;
    var selectedStudies = null;

    var hasStudy = <%=null==study?"false":"true"%>;
    var isSharedStudy = <%=null==study||!study.isDataspaceStudy() ? "false":"true"%>;
    var studyLabel = <%=q(null==study?"":study.getLabel())%>;

    if (!hasStudy)
    {
        div.update("No study found in this folder");
        return;
    }
    if (!isSharedStudy)
    {
        div.update("Showing " + Ext4.htmlEncode(studyLabel));
        return;
    }

    // query available studies
    LABKEY.Query.selectRows(
    {
        requiredVersion: 15.1,
        schemaName: 'study',
        queryName: 'StudyProperties',
        columns: 'Label,Description,Container',
        containerFilter: 'CurrentAndSubfolders',
        filterArray: null,
        success: updateAvailableStudies
    });

    // query current filters
    // CONSIDER: delete the shared container filter if all loaded_studies are selected
    LABKEY.Ajax.request({
        url: LABKEY.ActionURL.buildURL('study-shared', 'sharedStudyContainerFilter.api'),
        method: 'GET',
        success : updateSelectedStudies
    });


    function updateAvailableStudies(results)
    {
        availableStudies = [];
        var rows = results.rows;
        var length = Math.min(10, rows.length);
        for (var i = 0; i < length; i++)
        {
            var row = rows[i].data;
            availableStudies.push({"container":row.Container.value, "label":row.Label.value});
        }
        if (null != selectedStudies)
            update();
    }

    function updateSelectedStudies(result)
    {
        selectedStudies = [];
        var json = JSON.parse(result.response);
        if (!('data' in json))
            return;
        var data = json.data;
        var containers = data.containers || [];

        for (var i = 0; i < containers.length; i++)
        {
            selectedStudies.push({"container":containers[i]});
        }
        if (null != availableStudies)
            update();
    }

    function update()
    {
        var i, s, studyMap = {};
        var selectedCount = 0;
        var availableCount = 0;
        var html = [];

        for (i = 0; i < availableStudies.length; i++)
        {
            s = availableStudies[i];
            studyMap[s.container] = s;
            availableCount++;
        }

        for (i = 0; i < selectedStudies.length; i++)
        {
            s = studyMap[selectedStudies[i].container];
            if (s)
            {
                s.selected = true;
                selectedCount++;
            }
        }

        if (0 == selectedCount)
        {
            html.push("<p>Showing all available studies (" + availableCount + ")</p>");
        }
        else
        {
            html.push("<p>Showing " + selectedCount + " of " + availableCount + " available " + (availableCount==1?"study":"studies") + "</p>");
            html.push('<div class="study-list">');
            for (i = 0; i < availableStudies.length; i++)
            {
                s = availableStudies[i];
                html.push('<div data-container="' + s.container + '" class="');
                html.push(s.selected ? 'study-selected' : 'study-notselected');
                html.push('">');
                html.push(Ext4.htmlEncode(s.label));
                html.push("</div>");
            }
            html.push("</div>");
        }
        div.insertHtml('afterEnd',html.join(''));
    }


    /*
     //TODO use ext template
     var row = rows[idxRow].data;
     html.push("<span data-container=\""+row.Container.value+"\">");
     //html.push("<img src=\"<%=getContextPath()%>/_images/close.png\">");
 html.push(Ext4.htmlEncode(row.Label.value));
 html.push("</span><br>");
 div.insertHtml('afterEnd',html.join(''));


 */

});
</script>
