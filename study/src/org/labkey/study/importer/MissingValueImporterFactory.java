/*
 * Copyright (c) 2012-2016 LabKey Corporation
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
package org.labkey.study.importer;

import org.apache.xmlbeans.XmlObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.AbstractFolderImportFactory;
import org.labkey.api.admin.FolderImporter;
import org.labkey.api.admin.FolderArchiveDataTypes;
import org.labkey.api.admin.ImportContext;
import org.labkey.api.admin.ImportException;
import org.labkey.api.data.MvUtil;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobWarning;
import org.labkey.api.writer.VirtualFile;
import org.labkey.folder.xml.FolderDocument;
import org.labkey.study.xml.MissingValueIndicatorsType;
import org.labkey.study.xml.StudyDocument;

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

    public class MissingValueImporter implements FolderImporter
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
        public void process(@Nullable PipelineJob job, ImportContext ctx, VirtualFile root) throws Exception
        {
            if (!ctx.isDataTypeSelected(getDataType()))
                return;

            if (isValidForImportArchive(ctx))
            {
                MissingValueIndicatorsType mvXml = getMissingValueIndicatorsFromXml(ctx.getXml());

                if (null != job)
                    job.setStatus("IMPORT " + getDescription());
                ctx.getLogger().info("Loading " + getDescription());
                MissingValueIndicatorsType.MissingValueIndicator[] mvs = mvXml.getMissingValueIndicatorArray();

                // Create a map that looks just like the map returned by MvUtil.getIndicatorsAndLabels()
                Map<String, String> newMvMap = new HashMap<>(mvs.length);

                for (MissingValueIndicatorsType.MissingValueIndicator mv : mvs)
                    newMvMap.put(mv.getIndicator(), mv.getLabel());

                Map<String, String> oldMvMap = MvUtil.getIndicatorsAndLabels(ctx.getContainer());

                // Only save the imported missing value indicators if they don't match the current settings exactly; this makes
                // it possible to share the same MV indicators across a folder tree, without an import breaking inheritance.
                if (!newMvMap.equals(oldMvMap))
                {
                    String[] mvIndicators = newMvMap.keySet().toArray(new String[mvs.length]);
                    String[] mvLabels = newMvMap.values().toArray(new String[mvs.length]);
                    MvUtil.assignMvIndicators(ctx.getContainer(), mvIndicators, mvLabels);
                }
                ctx.getLogger().info("Done importing " + getDescription());
            }
        }

        @NotNull
        @Override
        public Collection<PipelineJobWarning> postProcess(ImportContext ctx, VirtualFile root) throws Exception
        {
            return Collections.emptyList();
        }

        @Override
        public boolean isValidForImportArchive(ImportContext ctx) throws ImportException
        {
            return ctx.getXml() != null && getMissingValueIndicatorsFromXml(ctx.getXml()) != null;
        }

        private MissingValueIndicatorsType getMissingValueIndicatorsFromXml(XmlObject xml)
        {
            // This conversion of the xml object to either a Study doc or a Folder doc is to support backward
            // compatibility for importing study archives which have the MVI info in the study.xml file
            MissingValueIndicatorsType mvi = null;
            if (xml instanceof StudyDocument.Study)
                mvi = ((StudyDocument.Study)xml).getMissingValueIndicators();
            else if (xml instanceof FolderDocument.Folder)
                mvi = ((FolderDocument.Folder)xml).getMissingValueIndicators();

            return mvi;
        }
    }
}
