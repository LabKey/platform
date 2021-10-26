/*
 * Copyright (c) 2018 LabKey Corporation
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
package org.labkey.list.pipeline;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.writer.FileSystemFile;
import org.labkey.list.model.ListImportContext;
import org.labkey.list.model.ListImporter;

import java.io.File;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;

public class ListReloadJob extends PipelineJob
{
    private final Path _dataFile;
    private final ListImportContext _importContext;

    @JsonCreator
    protected ListReloadJob(@JsonProperty("_dataFile") Path dataFile, @JsonProperty("_importContext") ListImportContext importContext)
    {
        _dataFile = dataFile;
        _importContext = importContext;
    }

    public ListReloadJob(ViewBackgroundInfo info, @NotNull PipeRoot root, Path dataFile, Path logFile, @NotNull ListImportContext importContext)
    {
        super(null, info, root);
        _dataFile = dataFile;
        _importContext = importContext;
        setLogFile(logFile);
    }

    @Override
    public String getDescription()
    {
        return "Reloading list";
    }

    @Override
    public URLHelper getStatusHref()
    {
        return null;
    }

    @Override
    public void run()
    {
        setStatus("RELOADING", "Job started at: " + DateUtil.nowISO());
        ListImporter importer = new ListImporter(_importContext);

        String fileName = _dataFile.getFileName().toString();

        getLogger().info("Loading " + fileName);

        List<String> errors = new LinkedList<>();
        try
        {
            if (!importer.processSingle(new FileSystemFile(_dataFile.getParent()), fileName, getPipeRoot().getContainer(), getInfo().getUser(), errors, getLogger()))
            {
                error("Job failed.");
            }
        }
        catch (Exception e)
        {
            error("Job failed: " + e.getMessage());
        }

        for (String error : errors)
            getLogger().error(error);

        getLogger().info("Done importing " + getDescription());
    }
}
