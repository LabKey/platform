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

import org.labkey.api.pipeline.PipelineProvider;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ViewURLHelper;

import java.util.List;
import java.util.ListIterator;

/**
 * User: peter@labkey.com
 * Date: Aug 24, 2006
 * Time: 12:45:45 PM
 */
public class XarAssayPipelineProvider extends PipelineProvider
{
    public static String name = "XarAssay";

    public XarAssayPipelineProvider()
    {
        super(name);
    }

    public boolean isStatusViewableFile(String name, String basename)
    {
        return true;

    }

    public void updateFileProperties(ViewContext context, List<FileEntry> entries)
    {

        for (ListIterator<FileEntry> it = entries.listIterator(); it.hasNext();)
        {
            FileEntry entry = it.next();
            if (!entry.isDirectory())
            {
                continue;
            }
            ViewURLHelper actionUrl = entry.cloneHref();
            actionUrl.setPageFlow("XarAssay");
            actionUrl.setAction("chooseAssay");
            String protId = context.getRequest().getParameter("rowId");
            if (null != protId)
                actionUrl.addParameter("rowId", protId);

            //todo:  make it possible for xarAssayProvider extensions to each put up their own buttons
            FileAction fa = new FileAction("Create Assay Run", actionUrl, entry.listFiles(new XarAssayProvider.AnalyzeFileFilter()));
            entry.addAction(fa);

        }
    }


}
