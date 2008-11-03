/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

package org.labkey.xarassay;

import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineProvider;
import org.labkey.api.util.FileType;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;

import java.io.File;
import java.util.List;

/**
 * User: peter@labkey.com
 * Date: Aug 24, 2006
 * Time: 12:45:45 PM
 */
public class XarAssayPipelineProvider extends PipelineProvider
{
    public static String name = "XarAssay";
    public static String PIPELINE_BUTTON_TEXT = "Create Assay Run";
    public static final FileType FT_MZXML = new FileType(".mzXML");

    public XarAssayPipelineProvider()
    {
        super(name);
    }

    public boolean isStatusViewableFile(String name, String basename)
    {
        return true;

    }
    //TODO:
    // use same strategy as Microarray assays to put buttons on the pipeline browser and get rid of
    // the separate page for choosing which of the MsBaseAssay subclasses to use

    public void updateFileProperties(ViewContext context, PipeRoot pr, List<FileEntry> entries)
    {

        for (FileEntry entry : entries)
        {
            if (!entry.isDirectory())
            {
                continue;
            }
            ActionURL actionUrl = entry.cloneHref();
            actionUrl.setAction(XarAssayController.ChooseAssayAction.class);
            String protId = context.getRequest().getParameter("rowId");
            if (null != protId)
                actionUrl.addParameter("rowId", protId);

            //todo:  make it possible for xarAssayProvider extensions to each put up their own buttons
            addAction(actionUrl,PIPELINE_BUTTON_TEXT , entry, entry.listFiles(XarAssayPipelineProvider.getAnalyzeFilter(pr.isPerlPipeline())));

        }

    }

    public static PipelineProvider.FileEntryFilter getAnalyzeFilter(boolean supportCluster)
    {
        return new PipelineProvider.FileEntryFilter()
            {
                public boolean accept(File f)
                {
                    return isMzXMLFile(f);
                }
            };
    }

    public static class AnalyzeFileFilter extends PipelineProvider.FileEntryFilter
    {
        public boolean accept(File file)
        {
            // Show all mzXML files.
            if (isMzXMLFile(file))
                return true;

            // TODO:  If no corresponding mzXML file, show raw files.

            return false;
        }
    }
    public static boolean isMzXMLFile(File file)
    {
        return FT_MZXML.isType(file);
    }



}
