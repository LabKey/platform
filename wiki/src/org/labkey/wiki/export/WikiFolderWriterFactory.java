package org.labkey.wiki.export;

import org.labkey.api.admin.ExternalFolderWriter;
import org.labkey.api.admin.ExternalFolderWriterFactory;
import org.labkey.api.admin.ImportContext;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.data.Container;
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
import java.io.OutputStream;
import java.io.PrintWriter;

/**
 * User: jeckels
 * Date: Jan 18, 2012
 */
public class WikiFolderWriterFactory implements ExternalFolderWriterFactory
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
            FolderDocument.Folder.Wikis wikisXML = ctx.getXml().addNewWikis();
            wikisXML.setDir(DIRECTORY_NAME);

            VirtualFile wikiDir = vf.getDir(DIRECTORY_NAME);

            WikisDocument wikisDocument = WikisDocument.Factory.newInstance();
            WikisType wikis = wikisDocument.addNewWikis();

            for (WikiTree wikiTree : WikiSelectManager.getWikiTrees(container))
            {
                if (wikiTree.getParent().getRowId() == -1)
                {
                    writeTree(container, wikiTree, wikis, wikiDir);
                }
            }

            wikiDir.saveXmlBean(WIKIS_FILENAME, wikisDocument);
        }

        private void writeTree(Container container, WikiTree wikiTree, WikisType wikis, VirtualFile wikiDir) throws IOException
        {
            WikiType wikiXml = wikis.addNewWiki();
            Wiki wiki = WikiSelectManager.getWiki(container, wikiTree.getName());
            wikiXml.setName(wiki.getName().getSource());
            Wiki parentWiki = wiki.getParentWiki();
            if (parentWiki != null)
            {
                wikiXml.setParent(parentWiki.getName().getSource());
            }

            WikiVersion wikiVersion = wiki.getLatestVersion();
            wikiXml.setTitle(wikiVersion.getTitle().getSource());
            wikiXml.setShowAttachments(wiki.isShowAttachments());

            VirtualFile wikiSubdir = wikiDir.getDir(wiki.getName().getSource());
            PrintWriter writer = wikiSubdir.getPrintWriter(WikiWebdavProvider.getDocumentName(wiki));
            try
            {
                writer.print(wikiVersion.getBody());
            }
            finally
            {
                writer.close();
            }

            for (Attachment attachment : wiki.getAttachments())
            {
                OutputStream outputStream = wikiSubdir.getOutputStream(attachment.getName());
                try
                {
                    outputStream.write("test".getBytes());
                }
                finally
                {
                    try { outputStream.close(); } catch (IOException ignored) {}
                }
            }

            for (WikiTree child : wikiTree.getChildren())
            {
                writeTree(container, child, wikis, wikiDir);
            }
        }
    }
}
