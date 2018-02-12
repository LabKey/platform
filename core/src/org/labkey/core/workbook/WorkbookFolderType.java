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
import org.labkey.api.view.WebPartFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
                null,
                getDefaultModuleSet(ModuleLoader.getInstance().getCoreModule(), ModuleLoader.getInstance().getModule("Experiment")),
                ModuleLoader.getInstance().getCoreModule());
        setWorkbookType(true);
    }

    @Override
    public String getLabel()
    {
        return "Default Workbook";
    }

    @Override
    public List<Portal.WebPart> getPreferredWebParts()
    {
        ArrayList<Portal.WebPart> parts = new ArrayList<>();
        if (null != Portal.getPortalPart("Workbook Description"))
            parts.add(Portal.getPortalPart("Workbook Description").createWebPart());
        if (null != Portal.getPortalPart("Experiment Runs"))
            parts.add(Portal.getPortalPart("Experiment Runs").createWebPart());
        Portal.WebPart files = createFileWebPart();
        if (null != files)
            parts.add(files);
        return parts;
    }

    private static Portal.WebPart createFileWebPart()
    {
        WebPartFactory wpf =  Portal.getPortalPart("Files");
        if (null == wpf)
            return null;
        Portal.WebPart result = wpf.createWebPart(HttpView.BODY);
        result.setProperty("fileSet", FileContentService.PIPELINE_LINK);
        result.setProperty("webpart.title", "Files");
        return result;
    }
}
