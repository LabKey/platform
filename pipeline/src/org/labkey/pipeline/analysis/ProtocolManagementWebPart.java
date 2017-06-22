/*
 * Copyright (c) 2016-2017 LabKey Corporation
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
package org.labkey.pipeline.analysis;

import org.labkey.api.data.ActionButton;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.CachedResultSets;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.ResultsImpl;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SimpleDisplayColumn;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.PipelineProtocolFactory;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.TaskPipeline;
import org.labkey.api.pipeline.file.FileAnalysisTaskPipeline;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.GridView;
import org.labkey.api.view.ViewContext;
import org.springframework.validation.BindException;

import java.io.IOException;
import java.io.Writer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Grid view of a list of all pipeline protocols in a container
 * User: tgaluhn
 * Date: 11/5/2016
 */
public class ProtocolManagementWebPart extends GridView
{
    private static final String NAME = "Pipeline Protocols";

    public static String getName()
    {
        return NAME;
    }

    public ProtocolManagementWebPart(ViewContext viewContext)
    {
        super(new DataRegion(), (BindException) null);
        setViewContext(viewContext);
        setTitle(NAME);
        createResults();
        getDataRegion().setSettings(new QuerySettings(getViewContext(), NAME));
        getDataRegion().setSortable(false);
        getDataRegion().setShowFilters(false);
        if (getViewContext().getContainer().hasPermission(getViewContext().getUser(), DeletePermission.class))
        {
            getDataRegion().setButtonBar(createButtonBar());
            getDataRegion().setShowRecordSelectors(true);
        }
        getDataRegion().setRecordSelectorValueColumns("taskId", "name");
    }

    private ButtonBar createButtonBar()
    {
        ButtonBar bb = new ButtonBar();
        for (AnalysisController.ProtocolTask action : AnalysisController.ProtocolTask.values())
        {
            ActionURL url = new ActionURL(AnalysisController.ProtocolManagementAction.class, getViewContext().getContainer());
            url.addParameter("action", action.toString()).addReturnURL(getContextURLHelper());
            ActionButton button = new ActionButton(url, action.toString());
            String confirmMessage = "Are you sure you want to " + action.toString() + " the selected ";
            button.setRequiresSelection(true, confirmMessage + "protocol?", confirmMessage + "protocols?");
            bb.add(button);
        }
        return bb;
    }

    private void createResults() // Accept filter & sort ? Tough to use standard UI components the way this is wired in.
    {
        List<Map<String, Object>> rows = getProtocols().stream().map(Protocol::toMap).collect(Collectors.toList());
        ResultSet rs = CachedResultSets.create(rows, Arrays.asList("taskId", "name", "pipeline", "archived"));
        try
        {
            List<ColumnInfo> colInfos = DataRegion.colInfosFromMetaData(rs.getMetaData());

            Map<String, String> params = new HashMap<>();
            params.put("taskId", "taskId");
            params.put("name", "name");
            params.put("archived", "archived");
            ActionURL actionURL = new ActionURL(AnalysisController.ProtocolDetailsAction.class, getViewContext().getContainer());
            DetailsURL url = new DetailsURL(actionURL.addReturnURL(getContextURLHelper()), params);
            colInfos.get(1).setURL(url);
            setResults(new ResultsImpl(rs, colInfos));
            getDataRegion().setColumns(colInfos);
            getDataRegion().getDisplayColumn("taskId").setVisible(false);
            getDataRegion().replaceDisplayColumn("archived", new ArchivedDisplayColumn());
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    private List<Protocol> getProtocols()
    {
        // Implementing a fixed sort order here -> taskPipeline description, archived status, protocol name
        PipeRoot root = PipelineService.get().findPipelineRoot(getViewContext().getContainer());
        List<Protocol> protocols = new ArrayList<>();

        PipelineJobService.get().getTaskPipelines(getViewContext().getContainer(), FileAnalysisTaskPipeline.class).stream()
                .filter(tp -> tp.getProtocolFactoryName() != null)
                .sorted(Comparator.comparing(TaskPipeline::getDescription, String.CASE_INSENSITIVE_ORDER))
                .forEach(tp ->
                {
                    protocols.addAll(getTaskPipelineProtocols(root, tp, false));
                    protocols.addAll(getTaskPipelineProtocols(root, tp, true));
                });
        return protocols;
    }

    private List<Protocol> getTaskPipelineProtocols(PipeRoot root, TaskPipeline taskPipeline, boolean archived)
    {
        PipelineProtocolFactory factory = PipelineJobService.get().getProtocolFactory(taskPipeline);
        return getFactoryProtocols(root, taskPipeline, archived, factory);
    }

    private List<Protocol> getFactoryProtocols(PipeRoot root, TaskPipeline taskPipeline, boolean archived, PipelineProtocolFactory factory)
    {
        // The code in AnalysisController.GetSavedProtocolsAction suggests that we may need to call getProtocolNames() with
        // workbook roots/dirDatas, and or a non-null dirData  in some cases, but I can't find any code path in which
        // those would have been set.
        return Arrays.stream(factory.getProtocolNames(root, null, archived)).sorted(String.CASE_INSENSITIVE_ORDER)
                .map(protocolName -> new Protocol(taskPipeline, protocolName, archived))
                .collect(Collectors.toList());
    }

    private static class ArchivedDisplayColumn extends SimpleDisplayColumn
    {
        public ArchivedDisplayColumn()
        {
            setCaption("Archived");
            setTextAlign("center");
        }

        public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
        {
            if ((boolean)ctx.getRow().get("archived"))
            {
                out.write("<b>&#x2714;</b>"); // html checkmark
            }
        }
    }

    class Protocol
    {
        final String _taskId;
        final String _name;
        final String _pipelineDescription;
        final boolean _archived;

        Protocol(TaskPipeline pipeline, String name, boolean archived)
        {
            _taskId = pipeline.getId().toString();
            _name = name;
            _pipelineDescription = pipeline.getDescription();
            _archived = archived;
        }

        Map<String, Object> toMap()
        {
            Map<String, Object> row = new HashMap<>();
            row.put("taskId", _taskId);
            row.put("name", _name);
            row.put("pipeline", _pipelineDescription);
            row.put("archived", _archived);

            return row;
        }
    }
}