/*
 * Copyright (c) 2026 LabKey Corporation
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
package org.labkey.specimen.importer;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.TSVMapWriter;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.security.User;
import org.labkey.api.study.SpecimenService;
import org.labkey.api.study.SpecimenTransform;
import org.labkey.api.util.FileType;
import org.labkey.api.view.ActionURL;
import org.labkey.specimen.SpecimenModule;
import org.labkey.specimen.actions.SpecimenController.ConfigureQueryImportAction;
import org.labkey.vfs.FileLike;

import java.util.ArrayList;
import java.util.Map;

public class QueryBasedSpecimenTransform implements SpecimenTransform
{
    public static final String NAME = "QueryBased";
    public static final String PROPERTY_MAP_KEY = "queryBasedSpecimenLoader";

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public boolean isValid(Container container)
    {
        return container.getActiveModules().contains(ModuleLoader.getInstance().getModule(SpecimenModule.class));
    }

    @Override
    public boolean isActive(Container container)
    {
        PropertyManager.PropertyMap props = PropertyManager.getProperties(container, PROPERTY_MAP_KEY);
        boolean enabled = ("on").equals(props.get("enabled"));
        // If selected is null, the container has only one transform
        String selected = SpecimenService.get().getActiveSpecimenImporter(container);

        return enabled && (selected == null || NAME.equals(selected));
    }

    @Override
    public FileType getFileType()
    {
        return new FileType(".qbst.csv");
    }

    protected static class QueryBasedTSVWriter extends TSVMapWriter
    {
        private int _rowCount;
        public QueryBasedTSVWriter(Iterable<Map<String, Object>> rows)
        {
            super(new ArrayList<>(), rows);     // start with empty column list
        }

        public int getRowCount()
        {
            return _rowCount;
        }

        @Override
        public void writeRow(Map<String, Object> row)
        {
            if (_rowCount == 0)
            {
                writeFileHeader();
                setColumns(row.keySet());
                writeColumnHeaders();
            }
            super.writeRow(row);
            _rowCount++;
        }
    }

    @Override
    public void transform(@Nullable PipelineJob job, FileLike input, FileLike output) throws PipelineJobException
    {
        QueryBasedTransformTask task = new QueryBasedTransformTask(job);
        task.transform(input, output);
    }

    @Override
    public void postTransform(@Nullable PipelineJob job, FileLike input, FileLike outputArchive)
    {
        // noop
    }

    @Override
    public @Nullable ActionURL getManageAction(Container c, User user)
    {
        return new ActionURL(ConfigureQueryImportAction.class, c);
    }

    @Override
    public ExternalImportConfig getExternalImportConfig(Container c, User user)
    {
        Map<String, String> props = PropertyManager.getProperties(c, PROPERTY_MAP_KEY);
        QueryBasedSpecimenReloadConfig config = new QueryBasedSpecimenReloadConfig();

        String schemaName = props.get("schemaName");
        String queryName = props.get("queryName");
        String viewName = props.get("viewName");

        config.setSchemaName(schemaName);
        config.setQueryName(queryName);
        config.setViewName(viewName);

        return config;
    }

    @Override
    public void importFromExternalSource(@Nullable PipelineJob job, ExternalImportConfig importConfig, FileLike inputArchive) throws PipelineJobException
    {
        if (importConfig instanceof QueryBasedSpecimenReloadConfig queryConfig)
        {
            QueryBasedExport export = new QueryBasedExport(queryConfig, job, inputArchive);
            export.exportRepository();
        }
    }
}
