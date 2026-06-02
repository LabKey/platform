/*
 * Copyright (c) 2021-2026 LabKey Corporation
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
package org.labkey.api.specimen;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.view.FolderTab;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFactory;

import java.util.ArrayList;
import java.util.List;

public class SpecimensPage extends FolderTab.PortalPage
{
    public static final String PAGE_ID = "study.SPECIMENS";
    public static final String SPECIMEN_TOOLS_WEBPART_NAME = "Specimen Tools";  // TODO: Move back to SpecimenToolsWebPartFactory

    public SpecimensPage(String caption)
    {
        super(PAGE_ID, caption);
    }

    @Override
    public boolean isSelectedPage(ViewContext viewContext)
    {
        return super.isSelectedPage(viewContext) ||
                "specimen".equals(viewContext.getActionURL().getController());
    }

    @Override
    public boolean isVisible(Container c, User user)
    {
        Study study = StudyService.get().getStudy(c);
        if (study != null)
        {
            return SpecimenManager.get().isSpecimenModuleActive(c);
        }
        return false;
    }

    @Override
    public List<Portal.WebPart> createWebParts()
    {
        List<Portal.WebPart> parts = new ArrayList<>();
        parts.add(Portal.getPortalPart(StudyService.SPECIMEN_SEARCH_WEBPART).createWebPart());
        parts.add(Portal.getPortalPart(StudyService.SPECIMEN_BROWSE_WEBPART).createWebPart());
        Portal.WebPart toolsWebPart = Portal.getPortalPart(SPECIMEN_TOOLS_WEBPART_NAME).createWebPart();
        toolsWebPart.setLocation(WebPartFactory.LOCATION_RIGHT);
        parts.add(toolsWebPart);
        return parts;
    }
}
