package org.labkey.lab;

import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.StudyUrls;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.FolderTab;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFactory;
import org.springframework.web.servlet.mvc.Controller;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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

    public static class ExperimentsPage extends FolderTab.PortalPage
    {
        public static final String PAGE_ID = "lab.Experiments";

        protected ExperimentsPage(String caption)
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
            List<Portal.WebPart> parts = Arrays.asList(Portal.getPortalPart("Assay List").createWebPart());
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
