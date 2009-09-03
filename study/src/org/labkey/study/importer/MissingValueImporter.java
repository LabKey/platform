/*
 * Copyright (c) 2009 LabKey Corporation
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

import org.labkey.api.data.Container;
import org.labkey.api.data.MvUtil;
import org.labkey.api.study.StudyImportException;
import org.labkey.study.xml.StudyDocument;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * User: adam
 * Date: May 16, 2009
 * Time: 9:20:22 PM
 */
public class MissingValueImporter
{
    void process(ImportContext ctx) throws IOException, SQLException, StudyImportException
    {
        Container c = ctx.getContainer();
        StudyDocument.Study.MissingValueIndicators mvXml = ctx.getStudyXml().getMissingValueIndicators();

        if (null != mvXml)
        {
            ctx.getLogger().info("Loading missing value indicators");
            StudyDocument.Study.MissingValueIndicators.MissingValueIndicator[] mvs = mvXml.getMissingValueIndicatorArray();

            // Create a map that looks just like the map returned by MvUtil.getIndicatorsAndLabels()
            Map<String, String> newMvMap = new HashMap<String, String>(mvs.length);

            for (StudyDocument.Study.MissingValueIndicators.MissingValueIndicator mv : mvs)
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
}
