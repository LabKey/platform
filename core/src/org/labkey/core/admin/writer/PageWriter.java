package org.labkey.core.admin.writer;

import org.labkey.api.data.Container;
import org.labkey.api.view.FolderTab;
import org.labkey.api.view.Portal;
import org.labkey.api.view.Portal.WebPart;
import org.labkey.api.writer.VirtualFile;
import org.labkey.folder.xml.FolderDocument;
import org.labkey.folder.xml.PagesDocument;

import java.util.List;


/**
 * User: cnathe
 * Date: Jan 17, 2012
 */
public class PageWriter implements InternalFolderWriter
{
    private static final String FILENAME = "pages.xml";
    
    public String getSelectionText()
    {
        return "Webpart Properties and Layout";
    }

    public void write(Container c, FolderExportContext ctx, VirtualFile root) throws Exception
    {
        FolderDocument.Folder folderXml = ctx.getFolderXml();
        FolderDocument.Folder.Pages folderPagesXML = folderXml.addNewPages();
        folderPagesXML.setFile(FILENAME);

        PagesDocument pagesDocXML = PagesDocument.Factory.newInstance();
        PagesDocument.Pages pagesXML = pagesDocXML.addNewPages();

        List<WebPart> tabs = Portal.getParts(ctx.getContainer(), FolderTab.FOLDER_TAB_PAGE_ID);
        if (tabs.size() == 0)
        {
            // if there are no tabs, try getting webparts for the default page ID
            PagesDocument.Pages.Page pageXml = pagesXML.addNewPage();
            pageXml.setName(Portal.DEFAULT_PORTAL_PAGE_ID);
            addWebPartsToPage(pageXml, Portal.getParts(ctx.getContainer(), Portal.DEFAULT_PORTAL_PAGE_ID));
        }
        else
        {
            for (WebPart tab : tabs)
            {
                PagesDocument.Pages.Page pageXml = pagesXML.addNewPage();
                pageXml.setIndex(tab.getIndex());
                pageXml.setName(tab.getName());

                // for the study folder type(s), the Overview tab has a pageId of portal.default
                String pageId = tab.getName().equals("Overview") ? Portal.DEFAULT_PORTAL_PAGE_ID : tab.getName();
                addWebPartsToPage(pageXml, Portal.getParts(ctx.getContainer(), pageId));
            }
        }

        root.saveXmlBean(FILENAME, pagesDocXML);
    }

    public void addWebPartsToPage(PagesDocument.Pages.Page pageXml, List<WebPart> webpartsInPage)
    {
        for (WebPart webPart : webpartsInPage)
        {
            PagesDocument.Pages.Page.Webpart webpartXml = pageXml.addNewWebpart();
            webpartXml.setName(webPart.getName());
            webpartXml.setIndex(webPart.getIndex());
            webpartXml.setLocation(webPart.getLocation());
            webpartXml.setPermanent(webPart.isPermanent());
            if (null != webPart.getProperties())
            {
                webpartXml.setProperties(webPart.getProperties());
            }
        }
    }
}
