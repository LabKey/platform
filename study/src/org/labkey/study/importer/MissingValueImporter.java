/*
 * Copyright (c) 2009-2012 LabKey Corporation
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
import org.labkey.api.admin.FolderImporter;
import org.labkey.api.admin.FolderImporterFactory;
import org.labkey.api.admin.ImportContext;
import org.labkey.api.data.MvUtil;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobWarning;
import org.labkey.api.writer.VirtualFile;
import org.labkey.folder.xml.FolderDocument;
import org.labkey.study.xml.MissingValueIndicatorsType;
import org.labkey.study.xml.StudyDocument;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * User: adam
 * Date: May 16, 2009
 * Time: 9:20:22 PM
 */
public class MissingValueImporter implements FolderImporter
{
    @Override
    public String getDescription()
    {
        return "missing value indicators";
    }

    @Override
    public void process(PipelineJob job, ImportContext ctx, VirtualFile root) throws Exception
    {
        // This conversion of the xml object to either a Study doc or a Folder doc is temparary until the
        // study archive is merged with the folder archive. For now, we need to support importing the
        // MissingValueIndicators from either document type.
        XmlObject xml = ctx.getXml();
        MissingValueIndicatorsType mvXml = null;
        if (xml instanceof StudyDocument.Study)
            mvXml = ((StudyDocument.Study)xml).getMissingValueIndicators();
        else if (xml instanceof FolderDocument.Folder)
            mvXml = ((FolderDocument.Folder)xml).getMissingValueIndicators();

        if (null != mvXml)
        {
            if (null != job)
                job.setStatus("IMPORT " + getDescription());
            ctx.getLogger().info("Loading " + getDescription());
            MissingValueIndicatorsType.MissingValueIndicator[] mvs = mvXml.getMissingValueIndicatorArray();

            // Create a map that looks just like the map returned by MvUtil.getIndicatorsAndLabels()
            Map<String, String> newMvMap = new HashMap<String, String>(mvs.length);

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

    @Override
    public Collection<PipelineJobWarning> postProcess(ImportContext ctx, VirtualFile root) throws Exception
    {
        return null;
    }

    public static class Factory implements FolderImporterFactory
    {
        @Override
        public FolderImporter create()
        {
            return new MissingValueImporter();
        }

        @Override
        public boolean isFinalImporter()
        {
            return false;
        }
    }
}
