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

import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.FolderTab;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * User: migra
 * Date: Nov 7, 2011
 */
public class LabFolderTabs
{
    public static class OverviewPage extends FolderTab
    {
        protected OverviewPage(String caption)
        {
            super(caption);
        }

        @Override
        public boolean isSelectedPage(ViewContext viewContext)
        {
            ActionURL tabURL = getURL(viewContext);
            ActionURL curURL = viewContext.getActionURL();

            return tabURL.getAction().equals(curURL.getAction()) && tabURL.getPageFlow().equals(curURL.getPageFlow()) && (curURL.getParameter("pageId") == null);

        }

        @Override
        public ActionURL getURL(ViewContext viewContext)
        {
            return PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(viewContext.getContainer());
        }


    }

    public static class WorkbooksPage extends FolderTab.PortalPage
    {
        public static final String PAGE_ID = "lab.Workbooks";

        protected WorkbooksPage(String caption)
        {
            super(PAGE_ID, caption);
        }

        @Override
        public boolean isSelectedPage(ViewContext viewContext)
        {
            return super.isSelectedPage(viewContext) || viewContext.getContainer().isWorkbook();
        }

        @Override
        public List<Portal.WebPart> createWebParts()
        {
            List<Portal.WebPart> parts = Arrays.asList(Portal.getPortalPart("Workbooks").createWebPart());
            return parts;
        }

        @Override
        public Set<String> getLegacyNames()
        {
            return Collections.singleton("lab.Experiments");
        }
    }

    public static class AssaysPage extends FolderTab.PortalPage
    {
        public static final String PAGE_ID = "lab.Assays";

        protected AssaysPage(String caption)
        {
            super(PAGE_ID, caption);
        }

        @Override
        public boolean isSelectedPage(ViewContext viewContext)
        {
            return super.isSelectedPage(viewContext) || viewContext.getActionURL().getPageFlow().equals("assay");
        }

        @Override
        public List<Portal.WebPart> createWebParts()
        {
            List<Portal.WebPart> parts = Arrays.asList(Portal.getPortalPart("Assay List").createWebPart(),
                    Portal.getPortalPart("Lists").createWebPart());
            return parts;
        }
    }

    public static class MaterialsPage extends FolderTab.PortalPage
    {
        public static final String PAGE_ID = "lab.MATERIALS";

        protected MaterialsPage(String caption)
        {
            super(PAGE_ID, caption);
        }

        @Override
        public List<Portal.WebPart> createWebParts()
        {
            List<Portal.WebPart> parts = Arrays.asList(Portal.getPortalPart("Sample Sets").createWebPart(WebPartFactory.LOCATION_BODY));
            return parts;
        }
    }
}
