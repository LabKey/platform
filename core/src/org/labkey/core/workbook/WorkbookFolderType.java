/*
 * Copyright (c) 2010-2013 LabKey Corporation
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
package org.labkey.core.workbook;

import org.labkey.api.files.FileContentService;
import org.labkey.api.module.DefaultFolderType;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.Portal;

import java.util.Arrays;

/**
 * User: labkey
 * Date: Jan 6, 2010
 * Time: 3:01:45 PM
 */
public class WorkbookFolderType extends DefaultFolderType
{
    public static final String NAME = "Workbook";

    public WorkbookFolderType()
    {
        super(NAME,
                "A workbook containing files and experiment runs.",
                null,
                Arrays.asList(
                        Portal.getPortalPart("Workbook Description").createWebPart(),
                        Portal.getPortalPart("Experiment Runs").createWebPart(),
                        createFileWebPart()
                ),
                getDefaultModuleSet(ModuleLoader.getInstance().getCoreModule(), getModule("Experiment")),
                ModuleLoader.getInstance().getCoreModule());
        setWorkbookType(true);
    }

    @Override
    public String getLabel()
    {
        return "Default Workbook";
    }

    private static Portal.WebPart createFileWebPart()
    {
        Portal.WebPart result = Portal.getPortalPart("Files").createWebPart(HttpView.BODY);
        result.setProperty("fileSet", FileContentService.PIPELINE_LINK);
        result.setProperty("webpart.title", "Files");
        return result;
    }
}
