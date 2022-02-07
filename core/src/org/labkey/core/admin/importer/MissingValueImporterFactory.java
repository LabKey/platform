/*
 * Copyright (c) 2012-2018 LabKey Corporation
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
package org.labkey.core.admin.importer;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.AbstractFolderImportFactory;
import org.labkey.api.admin.FolderArchiveDataTypes;
import org.labkey.api.admin.FolderImportContext;
import org.labkey.api.admin.FolderImporter;
import org.labkey.api.admin.ImportException;
import org.labkey.api.data.MvUtil;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobWarning;
import org.labkey.api.writer.VirtualFile;
import org.labkey.folder.xml.MissingValueIndicatorsType;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * User: cnathe
 * Date: May 1, 2012
 */
public class MissingValueImporterFactory extends AbstractFolderImportFactory
{
    @Override
    public FolderImporter create()
    {
        return new MissingValueImporter();
    }

    public static class MissingValueImporter implements FolderImporter
    {
        @Override
        public String getDataType()
        {
            return FolderArchiveDataTypes.MISSING_VALUE_INDICATORS;
        }

        @Override
        public String getDescription()
        {
            return getDataType().toLowerCase();
        }

        @Override
        public void process(@Nullable PipelineJob job, FolderImportContext ctx, VirtualFile root) throws Exception
        {
            if (!ctx.isDataTypeSelected(getDataType()))
                return;

            if (isValidForImportArchive(ctx))
            {
                if (null != job)
                    job.setStatus("IMPORT " + getDescription());
                ctx.getLogger().info("Loading " + getDescription());

                // Create a map that looks just like the map returned by MvUtil.getIndicatorsAndLabels()
                Map<String, String> newMvMap = new HashMap<>();

                if (ctx.getXml().isSetMissingValueIndicator())
                {
                    // Import from the new "folder"-namespace element
                    MissingValueIndicatorsType mvXml = ctx.getXml().getMissingValueIndicator();
                    MissingValueIndicatorsType.MissingValueIndicator[] mvs = mvXml.getMissingValueIndicatorArray();
                    for (MissingValueIndicatorsType.MissingValueIndicator mv : mvs)
                        newMvMap.put(mv.getIndicator(), mv.getLabel());
                }
                else
                {
                    // Import from the old "study"-namespace element -- eliminate this in five years (2017)
                    org.labkey.study.xml.MissingValueIndicatorsType mvXml = ctx.getXml().getMissingValueIndicators();
                    org.labkey.study.xml.MissingValueIndicatorsType.MissingValueIndicator[] mvs = mvXml.getMissingValueIndicatorArray();
                    for ( org.labkey.study.xml.MissingValueIndicatorsType.MissingValueIndicator mv : mvs)
                        newMvMap.put(mv.getIndicator(), mv.getLabel());
                }

                Map<String, String> oldMvMap = MvUtil.getIndicatorsAndLabels(ctx.getContainer());

                // Only save the imported missing value indicators if they don't match the current settings exactly; this makes
                // it possible to share the same MV indicators across a folder tree, without an import breaking inheritance.
                if (!newMvMap.equals(oldMvMap))
                {
                    String[] mvIndicators = newMvMap.keySet().toArray(new String[0]);
                    String[] mvLabels = newMvMap.values().toArray(new String[0]);
                    MvUtil.assignMvIndicators(ctx.getContainer(), mvIndicators, mvLabels);
                }
                ctx.getLogger().info("Done importing " + getDescription());
            }
        }

        @NotNull
        @Override
        public Collection<PipelineJobWarning> postProcess(FolderImportContext ctx, VirtualFile root)
        {
            return Collections.emptyList();
        }

        @Override
        public boolean isValidForImportArchive(FolderImportContext ctx) throws ImportException
        {
            return ctx.getXml() != null && (
                /* folder namespace version */ ctx.getXml().getMissingValueIndicator() != null ||
                /* old study namespace version */ ctx.getXml().getMissingValueIndicators() != null);
        }
    }
}
