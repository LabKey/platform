package org.labkey.wiki.export;

import org.labkey.api.admin.ExternalFolderWriter;
import org.labkey.api.admin.ExternalFolderWriterFactory;
import org.labkey.api.admin.ImportContext;
import org.labkey.api.data.Container;
import org.labkey.api.webdav.WebdavResource;
import org.labkey.api.writer.VirtualFile;
import org.labkey.data.xml.wiki.WikiType;
import org.labkey.data.xml.wiki.WikisDocument;
import org.labkey.data.xml.wiki.WikisType;
import org.labkey.folder.xml.FolderDocument;
import org.labkey.wiki.WikiSelectManager;
import org.labkey.wiki.WikiWebdavProvider;
import org.labkey.wiki.model.Wiki;
import org.labkey.wiki.model.WikiTree;
import org.labkey.wiki.model.WikiVersion;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * User: jeckels
 * Date: Jan 18, 2012
 */
public class WikiWriterFactory implements ExternalFolderWriterFactory
{
    public static final String DIRECTORY_NAME = "wikis";
    public static final String WIKIS_FILENAME = "wikis.xml";

    @Override
    public ExternalFolderWriter create()
    {
        return new WikiFolderWriter();
    }

    private class WikiFolderWriter implements ExternalFolderWriter
    {
        @Override
        public String getSelectionText()
        {
            return "Wikis and their attachments";
        }

        @Override
        public void write(Container container, ImportContext<FolderDocument.Folder> ctx, VirtualFile vf) throws Exception
        {
            // Set up the pointer in the folder.xml file
            FolderDocument.Folder.Wikis wikisXML = ctx.getXml().addNewWikis();
            wikisXML.setDir(DIRECTORY_NAME);

            // Just dump the @wiki WebDav tree to the output
            VirtualFile wikiDir = vf.getDir(DIRECTORY_NAME);
            WikiWebdavProvider.WikiProviderResource parent = new WikiWebdavProvider.WikiProviderResource(new DummyWebdavResource(), container);
            wikiDir.saveWebdavTree(parent);
        }
    }
}
