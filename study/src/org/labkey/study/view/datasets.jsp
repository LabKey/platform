<%
/*
 * Copyright (c) 2006-2015 LabKey Corporation
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
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.study.model.DatasetDefinition" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.List" %>
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
            out.print(textLink("Manage Datasets", new ActionURL(StudyController.ManageTypesAction.class, container)));
        }
        return;
    }

    List<DatasetDefinition> userDatasets = new ArrayList<>();
    for (DatasetDefinition dataset : datasets)
    {
        if (!dataset.isShowByDefault())
            continue;

        if (dataset.canRead(getUser()))
            userDatasets.add(dataset);
    }

    int datasetsPerCol = userDatasets.size() / 3;
%>
<table width="100%">
    <tr>
        <td valign=top><%=renderDatasets(userDatasets, 0, datasetsPerCol + 1)%>
        </td>
        <td valign=top><%=renderDatasets(userDatasets, datasetsPerCol + 1, (2 * datasetsPerCol) + 1)%>
        </td>
        <td valign=top><%=renderDatasets(userDatasets, (2 * datasetsPerCol) + 1, userDatasets.size())%>
        </td>
    </tr>
</table>
<%
    if (container.hasPermission(user, AdminPermission.class))
        out.print("<br>" + textLink("Manage Datasets", new ActionURL(StudyController.ManageTypesAction.class, container)));
%>
<%!
    String renderDatasets(List<DatasetDefinition> datasets, int startIndex, int endIndex)
    {
        StringBuilder sb = new StringBuilder();
        if (startIndex >= datasets.size() || startIndex >= endIndex)
            return "";

        String category = startIndex == 0 ? null : datasets.get(startIndex - 1).getCategory();
        ActionURL datasetURL = new ActionURL(StudyController.DefaultDatasetReportAction.class, getContainer());
        sb.append("<table>\n");
        //Print a column header if necessary
        Dataset firstDataset = datasets.get(startIndex);
        if (!equal(category, firstDataset.getCategory()))
        {
            category = firstDataset.getCategory();
            // don't need extra padding (labkey-announcement-title) on first row
            sb.append("<tr><td class=\"labkey-announcement-title\" style=\"padding-top:0;\"><span>");
            sb.append(h(category == null ? "Uncategorized" : category));
            sb.append("</span></td></tr>\n");
            sb.append("<tr><td class=\"labkey-title-area-line\"></td></tr>\n");
        }
        else if (null != category)
        {
            sb.append("<tr><td class=\"labkey-announcement-title\" style=\"padding-top:0;\"><span>");
            sb.append(h(category)).append(" (Continued)");
            sb.append("</span></td></tr>\n");
            sb.append("<tr><td class=\"labkey-title-area-line\"></td></tr>\n");
        }

        for (Dataset dataset : datasets.subList(startIndex, endIndex))
        {
            if (!equal(category, dataset.getCategory()))
            {
                category = dataset.getCategory();
                sb.append("<tr><td class=\"labkey-announcement-title\"><span>").append(h(category == null ? "Uncategorized" : category)).append("</span></td></tr>\n");
                sb.append("<tr><td class=\"labkey-title-area-line\"></td></tr>\n");
            }

            String datasetLabel = (dataset.getLabel() != null ? dataset.getLabel() : "" + dataset.getDatasetId());

            sb.append("        <tr><td>");
            sb.append("<a href=\"").append(datasetURL.replaceParameter("datasetId", String.valueOf(dataset.getDatasetId())));
            sb.append("\">");
            sb.append(h(datasetLabel));
            sb.append("</a></td></tr>\n");
        }
        sb.append("    </table>");

        return sb.toString();
    }

    boolean equal(String s1, String s2)
    {
        if ((s1 == null) != (s2 == null))
            return false;

        if (null == s1)
            return true;

        return s1.equals(s2);
    }
%>
