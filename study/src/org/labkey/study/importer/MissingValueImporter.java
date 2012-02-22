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

import org.labkey.api.admin.*;
import org.labkey.api.admin.ImportContext;
import org.labkey.api.data.Container;
import org.labkey.api.data.MvUtil;
import org.labkey.api.pipeline.PipelineJobWarning;
import org.labkey.api.writer.VirtualFile;
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
public class MissingValueImporter implements FolderImporter<StudyDocument.Study>
{
    @Override
    public String getDescription()
    {
        return "missing value indicators";
    }

    @Override
    public void process(ImportContext<StudyDocument.Study> ctx, VirtualFile root) throws Exception
    {
        Container c = ctx.getContainer();
        MissingValueIndicatorsType mvXml = ctx.getXml().getMissingValueIndicators();

        if (null != mvXml)
        {
            ctx.getLogger().info("Loading missing value indicators");
            MissingValueIndicatorsType.MissingValueIndicator[] mvs = mvXml.getMissingValueIndicatorArray();

            // Create a map that looks just like the map returned by MvUtil.getIndicatorsAndLabels()
            Map<String, String> newMvMap = new HashMap<String, String>(mvs.length);

            for (MissingValueIndicatorsType.MissingValueIndicator mv : mvs)
                newMvMap.put(mv.getIndicator(), mv.getLabel());

            Map<String, String> oldMvMap = MvUtil.getIndicatorsAndLabels(c);

            // Only save the imported missing value indicators if they don't match the current settings exactly; this makes
            // it possible to share the same MV indicators across a folder tree, without an import breaking inheritance.
            if (!newMvMap.equals(oldMvMap))
            {
                String[] mvIndicators = newMvMap.keySet().toArray(new String[mvs.length]);
                String[] mvLabels = newMvMap.values().toArray(new String[mvs.length]);
                MvUtil.assignMvIndicators(c, mvIndicators, mvLabels);
            }
        }
    }

    @Override
    public Collection<PipelineJobWarning> postProcess(ImportContext<StudyDocument.Study> ctx, VirtualFile root) throws Exception
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
    }
}
