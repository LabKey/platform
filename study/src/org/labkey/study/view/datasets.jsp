<%
/*
 * Copyright (c) 2008-2019 LabKey Corporation
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
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.study.Dataset" %>
<%@ page import="org.labkey.api.study.Study" %>
<%@ page import="org.labkey.api.util.DOM" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.study.model.DatasetDefinition" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.List" %>
<%@ page import="static org.labkey.api.util.DOM.*" %>
<%@ page import="static org.labkey.api.util.DOM.Attribute.*" %>
<%@ page import="org.labkey.study.query.StudyQuerySchema" %>
<%@ page import="org.labkey.study.model.StudyImpl" %>
<%@ page import="org.labkey.api.security.permissions.ReadPermission" %>
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    StudyManager manager = StudyManager.getInstance();
    Container container = getContainer();
    Study study = manager.getStudy(container);
    User user = getUser();
    List<DatasetDefinition> datasets = manager.getDatasetDefinitions(study);

    if (null == datasets || datasets.isEmpty())
    {
        out.print(text("No datasets defined<br><br>"));
        if (container.hasPermission(user, AdminPermission.class))
        {
            out.print(link("Manage Datasets", new ActionURL(StudyController.ManageTypesAction.class, container)));
        }
        return;
    }

    StudyQuerySchema sqs = StudyQuerySchema.createSchema((StudyImpl)study, user);

    List<DatasetDefinition> userDatasets = new ArrayList<>();
    for (DatasetDefinition dataset : datasets)
    {
        if (!dataset.isShowByDefault())
            continue;

        var t = sqs.getDatasetTable(dataset, null);
        if (null != t && t.hasPermission(getUser(), ReadPermission.class))
            userDatasets.add(dataset);
    }

    int datasetsPerCol = userDatasets.size() / 3;
%>
<table width="100%">
    <tr>
        <td valign=top><%renderDatasets(out, userDatasets, 0, datasetsPerCol + 1);%>
        </td>
        <td valign=top><%renderDatasets(out, userDatasets, datasetsPerCol + 1, (2 * datasetsPerCol) + 1);%>
        </td>
        <td valign=top><%renderDatasets(out, userDatasets, (2 * datasetsPerCol) + 1, userDatasets.size());%>
        </td>
    </tr>
</table>
<%
    if (container.hasPermission(user, AdminPermission.class))
    {
        out.print(unsafe("<br>"));
        out.print(link("Manage Datasets", new ActionURL(StudyController.ManageTypesAction.class, container)));
    }
%>
<%!
    void renderDatasets(JspWriter out, List<DatasetDefinition> datasets, int startIndex, int endIndex)
    {
        if (startIndex >= datasets.size() || startIndex >= endIndex)
            return;

        String category = startIndex == 0 ? null : datasets.get(startIndex - 1).getCategory();
        ActionURL datasetURL = new ActionURL(StudyController.DefaultDatasetReportAction.class, getContainer());
        List<DOM.Renderable> tds = new ArrayList<>();
        //Print a column header if necessary
        Dataset firstDataset = datasets.get(startIndex);
        if (!StringUtils.equals(category, firstDataset.getCategory()))
        {
            category = firstDataset.getCategory();
            // don't need extra padding (labkey-announcement-title) on first row
            tds.add(TR(TD(cl("labkey-announcement-title").at(style, "padding-top:0;"), SPAN(category == null ? "Uncategorized" : category))));
            tds.add(TR(TD(cl("labkey-title-area-line"))));
        }
        else if (null != category)
        {
            tds.add(TR(TD(cl("labkey-announcement-title").at(style, "padding-top:0;"), SPAN(category + " (Continued)"))));
            tds.add(TR(TD(cl("labkey-title-area-line"))));
        }

        for (Dataset dataset : datasets.subList(startIndex, endIndex))
        {
            if (!StringUtils.equals(category, dataset.getCategory()))
            {
                category = dataset.getCategory();
                tds.add(TR(TD(cl("labkey-announcement-title"), SPAN(category == null ? "Uncategorized" : category))));
                tds.add(TR(TD(cl("labkey-title-area-line"))));
            }

            String datasetLabel = (dataset.getLabel() != null ? dataset.getLabel() : "" + dataset.getDatasetId());
            tds.add(TR(TD(link(datasetLabel).href(datasetURL.replaceParameter("datasetId", dataset.getDatasetId())).clearClasses())));
        }

        TABLE(tds.toArray()).appendTo(out);
    }
%>
