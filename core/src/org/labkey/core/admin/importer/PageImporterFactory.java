package org.labkey.core.admin.importer;

import org.labkey.api.admin.FolderImporter;
import org.labkey.api.admin.FolderImporterFactory;
import org.labkey.api.admin.ImportContext;
import org.labkey.api.admin.ImportException;
import org.labkey.api.admin.InvalidFileException;
import org.labkey.api.pipeline.PipelineJobWarning;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.util.XmlValidationException;
import org.labkey.api.view.FolderTab;
import org.labkey.api.view.Portal;
import org.labkey.folder.xml.FolderDocument;
import org.labkey.folder.xml.PagesDocument;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * User: cnathe
 * Date: Jan 17, 2012
 */
public class PageImporterFactory implements FolderImporterFactory
{
    @Override
    public FolderImporter create()
    {
        return new PageImporter();
    }

    public class PageImporter implements FolderImporter<FolderDocument.Folder>
    {
        @Override
        public String getDescription()
        {
            return "pages and webpart properties";
        }

        @Override
        public void process(ImportContext<FolderDocument.Folder> ctx, File root) throws Exception
        {
            FolderDocument.Folder.Pages pagesXml = ctx.getXml().getPages();

            if (null != pagesXml)
            {
                PagesDocument pagesDocXml;
                String pagesFileName = pagesXml.getFile();
                File pagesXmlFile = new File(root, pagesFileName);
                if (!pagesXmlFile.exists())
                {
                    throw new ImportException("Could not find expected file: " + pagesXmlFile);
                }

                try
                {
                    pagesDocXml = PagesDocument.Factory.parse(pagesXmlFile);
                    XmlBeansUtil.validateXmlDocument(pagesDocXml);
                }
                catch (XmlValidationException e)
                {
                    throw new InvalidFileException(pagesFileName, e);
                }

                PagesDocument.Pages.Page[] pageXmls = pagesDocXml.getPages().getPageArray();
                List<Portal.WebPart> tabs = new ArrayList<Portal.WebPart>();
                for (PagesDocument.Pages.Page pageXml : pageXmls)
                {
                    String pageId = pageXml.getName();
                    // for the study folder type(s), the Overview tab should have a pageId of portal.default
                    if (pageId.equals("Overview"))
                    {
                        pageId = Portal.DEFAULT_PORTAL_PAGE_ID;
                    }

                    Portal.WebPart tab = new Portal.WebPart();
                    tab.setLocation(FolderTab.LOCATION);
                    tab.setName(pageXml.getName());
                    tab.setIndex(pageXml.getIndex());
                    tabs.add(tab);

                    PagesDocument.Pages.Page.Webpart[] webpartXmls = pageXml.getWebpartArray();
                    List<Portal.WebPart> webparts = new ArrayList<Portal.WebPart>();
                    for (PagesDocument.Pages.Page.Webpart webpartXml : webpartXmls)
                    {
                        Portal.WebPart webpart = new Portal.WebPart();
                        webpart.setName(webpartXml.getName());
                        webpart.setIndex(webpartXml.getIndex());
                        webpart.setLocation(webpartXml.getLocation());
                        webpart.setPermanent(webpartXml.getPermanent());
                        if (null != webpartXml.getProperties())
                        {
                            webpart.setProperties(webpartXml.getProperties());
                        }
                        webparts.add(webpart);
                    }
                    Portal.saveParts(ctx.getContainer(), pageId, webparts);
                }

                if (tabs.size() > 1)
                {
                    Portal.saveParts(ctx.getContainer(), FolderTab.FOLDER_TAB_PAGE_ID, tabs);
                }
            }
        }

        @Override
        public Collection<PipelineJobWarning> postProcess(ImportContext<FolderDocument.Folder> ctx, File root) throws Exception
        {
            return null;
        }
    }
}
