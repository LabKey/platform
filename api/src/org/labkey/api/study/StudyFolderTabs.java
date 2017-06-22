/*
 * Copyright (c) 2011-2017 LabKey Corporation
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
package org.labkey.api.study;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.FolderTab;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * User: jeckels
 * Date: Oct 28, 2011
 */
public class StudyFolderTabs
{
    public static class OverviewPage extends FolderTab
    {
        public OverviewPage(String caption)
        {
            super(caption);
        }

        @Override
        public ActionURL getURL(Container container, User user)
        {
            return PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(container);
        }

        @Override
        public boolean isSelectedPage(ViewContext viewContext)
        {
            // Actual container we use doesn't matter, we just care about the controller and action names
            ActionURL defaultURL = PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(ContainerManager.getHomeContainer());
            ActionURL currentURL = viewContext.getActionURL();
            return currentURL.getController().equalsIgnoreCase(defaultURL.getController()) && currentURL.getAction().equalsIgnoreCase(defaultURL.getAction()) && currentURL.getParameter("pageId") == null;
        }
    }

    public static class SpecimensPage extends FolderTab.PortalPage
    {
        public static final String PAGE_ID = "study.SPECIMENS";

        public SpecimensPage(String caption)
        {
            super(PAGE_ID, caption);
        }

        @Override
        public boolean isSelectedPage(ViewContext viewContext)
        {
            return super.isSelectedPage(viewContext) ||
                   "study-samples".equals(viewContext.getActionURL().getController());
        }

        @Override
        public boolean isVisible(Container c, User user)
        {
            Study study = StudyService.get().getStudy(c);
            return (study != null);
        }

        @Override
        public List<Portal.WebPart> createWebParts()
        {
            List<Portal.WebPart> parts = new ArrayList<>();
            parts.add(Portal.getPortalPart(StudyService.SPECIMEN_SEARCH_WEBPART).createWebPart());
            parts.add(Portal.getPortalPart(StudyService.SPECIMEN_BROWSE_WEBPART).createWebPart());
            Portal.WebPart toolsWebPart = Portal.getPortalPart(StudyService.SPECIMEN_TOOLS_WEBPART_NAME).createWebPart();
            toolsWebPart.setLocation(WebPartFactory.LOCATION_RIGHT);
            parts.add(toolsWebPart);
            return parts;
        }
    }

    public static class DataAnalysisPage extends FolderTab.PortalPage
    {
        public static final String PAGE_ID = "study.DATA_ANALYSIS";

        public DataAnalysisPage(String caption)
        {
            super(PAGE_ID, caption);
        }

        @Override
        public boolean isSelectedPage(ViewContext viewContext)
        {
            ActionURL currentURL = viewContext.getActionURL();
            return super.isSelectedPage(viewContext) ||
                    currentURL.getController().equalsIgnoreCase("study-reports") ||
                    (currentURL.getController().equalsIgnoreCase("reports") && !currentURL.getAction().equalsIgnoreCase("manageviews")) ||
                    currentURL.getController().equalsIgnoreCase("dataset") ||
                    currentURL.getController().equalsIgnoreCase("visualization") ||
                    currentURL.getAction().equalsIgnoreCase("dataset");
        }

        @Override
         public List<Portal.WebPart> createWebParts()
        {
            List<Portal.WebPart> parts = new ArrayList<>();
            parts.add(Portal.getPortalPart("Data Views").createWebPart());
            Portal.WebPart toolsWebPart = Portal.getPortalPart(StudyService.DATA_TOOLS_WEBPART_NAME).createWebPart();
            toolsWebPart.setLocation(WebPartFactory.LOCATION_RIGHT);
            parts.add(toolsWebPart);
            return parts;
        }

        @Override
        public boolean isVisible(Container c, User user)
        {
            Study study = StudyService.get().getStudy(c);
            return (study != null);
        }
    }

    public static class ParticipantsPage extends FolderTab.PortalPage
    {
        public static final String PAGE_ID = "study.PARTICIPANTS";

        public ParticipantsPage(String caption)
        {
            super(PAGE_ID, caption);
        }

        @Override
        public String getCaption(@Nullable ViewContext viewContext)
        {
            Study study = (null != viewContext) ? StudyService.get().getStudy(viewContext.getContainer()) : null;
            if (null == study)
                return super.getCaption(viewContext);
            else
                return study.getSubjectNounPlural();
        }

        @Override
        public List<Portal.WebPart> createWebParts()
        {
            List<Portal.WebPart> parts = new ArrayList<>();

            WebPartFactory factory = Portal.getPortalPart("Search");
            if (factory != null)
                parts.add(factory.createWebPart());

            factory = Portal.getPortalPart("Subject List");
            if (factory != null)
                parts.add(factory.createWebPart());
            
            return parts;
        }

        @Override
        public boolean isSelectedPage(ViewContext viewContext)
        {
            ActionURL currentURL = viewContext.getActionURL();
            return super.isSelectedPage(viewContext) ||
                    currentURL.getAction().equalsIgnoreCase("subjectList") ||
                    currentURL.getAction().equalsIgnoreCase("participant");
        }

        @Override
        public boolean isVisible(Container c, User user)
        {
            Study study = StudyService.get().getStudy(c);
            return (study != null);
        }
    }

    public static class ManagePage extends FolderTab
    {
        public ManagePage(String caption)
        {
            super(caption);
        }

        @Override
        public ActionURL getURL(Container container, User user)
        {
            return PageFlowUtil.urlProvider(StudyUrls.class).getManageStudyURL(container);
        }

        @Override
        public boolean isSelectedPage(ViewContext viewContext)
        {
            ActionURL currentURL = viewContext.getActionURL();
            return super.isSelectedPage(viewContext) ||
                    currentURL.getController().equalsIgnoreCase("study-definition") ||
                    currentURL.getController().equalsIgnoreCase("cohort") ||
                    currentURL.getController().equalsIgnoreCase("study-properties") ||
                    (currentURL.getController().equalsIgnoreCase("reports") && currentURL.getAction().equalsIgnoreCase("manageviews"));
        }

        @Override
        public boolean isVisible(Container container, User user)
        {
            if (!container.hasPermission(getName() + ".isVisible()", user, AdminPermission.class))
                return false;

            Study study = StudyService.get().getStudy(container);
            return (study != null);
        }
    }
}
