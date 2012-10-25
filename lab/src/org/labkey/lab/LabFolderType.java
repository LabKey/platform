/*
 * Copyright (c) 2011-2012 LabKey Corporation
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
package org.labkey.lab;

import org.labkey.api.module.Module;
import org.labkey.api.module.MultiPortalFolderType;
import org.labkey.api.view.FolderTab;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;


/**
 * Created by IntelliJ IDEA.
 * User: markigra
 * Date: 11/7/11
 * Time: 3:17 PM
 */
public class LabFolderType extends MultiPortalFolderType
{
    private static final List<FolderTab> PAGES = Arrays.asList(
            (FolderTab) new LabFolderTabs.OverviewPage("Overview"),
            new LabFolderTabs.WorkbooksPage("Workbooks"),
            new LabFolderTabs.AssaysPage("Data"),
            new LabFolderTabs.MaterialsPage("Materials")
    );


    public LabFolderType(Module module)
    {
        super("Lab", "A folder type to to store experiments, assays, and materials",
                                Collections.<Portal.WebPart>emptyList(),
                null,
                getDefaultModuleSet(module, getModule("Experiment"), getModule("Study"), getModule("Pipeline")),
                module);
        this.setForceAssayUploadIntoWorkbooks(true);
    }

    @Override
    public List<FolderTab> getDefaultTabs()
    {
        return PAGES;
    }


    @Override
    protected String getFolderTitle(ViewContext context)
    {
        return context.getContainer().getName();
    }
}
