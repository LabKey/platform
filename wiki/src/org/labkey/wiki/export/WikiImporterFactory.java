package org.labkey.wiki.export;

import org.labkey.api.admin.ExternalFolderImporter;
import org.labkey.api.admin.ExternalFolderImporterFactory;
import org.labkey.api.admin.ImportContext;
import org.labkey.api.admin.ImportException;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.attachments.FileAttachmentFile;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.pipeline.PipelineJobWarning;
import org.labkey.api.util.HString;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.wiki.WikiRendererType;
import org.labkey.data.xml.wiki.WikiType;
import org.labkey.data.xml.wiki.WikisDocument;
import org.labkey.data.xml.wiki.WikisType;
import org.labkey.folder.xml.FolderDocument;
import org.labkey.wiki.WikiManager;
import org.labkey.wiki.WikiSelectManager;
import org.labkey.wiki.model.Wiki;
import org.labkey.wiki.model.WikiVersion;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: jeckels
 * Date: Jan 18, 2012
 */
public class WikiImporterFactory implements ExternalFolderImporterFactory
{
    @Override
    public ExternalFolderImporter create()
    {
        return new WikiImporter();
    }

    private class WikiImporter implements ExternalFolderImporter
    {
        @Override
        public String getDescription()
        {
            return "wikis";
        }

        @Override
        public void process(ImportContext<FolderDocument.Folder> ctx, File root) throws Exception
        {
            FolderDocument.Folder.Wikis wikisXml = ctx.getXml().getWikis();
            if (wikisXml != null)
            {
                File wikisDir = ctx.getDir(root, wikisXml.getDir());
                File wikisXmlFile = new File(wikisDir, WikiWriterFactory.WIKIS_FILENAME);

                if (!wikisXmlFile.exists())
                {
                    throw new ImportException("Could not find expected file: " + wikisXmlFile);
                }

                Set<String> importedWikiNames = new CaseInsensitiveHashSet();
                Map<Wiki, String> parentsToBeSet = new HashMap<Wiki, String>();

                WikisDocument document = WikisDocument.Factory.parse(wikisXmlFile);
                WikisType rootNode = document.getWikis();
                for (WikiType wikiXml : rootNode.getWikiArray())
                {
                    File wikiSubDir = new File(wikisDir, wikiXml.getName());
                    if (!wikiSubDir.isDirectory())
                    {
                        ctx.getLogger().error("Could not find content subdirectory for wiki with name \"" + wikiXml.getName() + "\"");
                    }
                    Wiki wiki = importWiki(wikiXml.getName(), wikiXml.getTitle(), wikiXml.getShowAttachments(), wikiSubDir, ctx);
                    if (wikiXml.getParent() != null)
                    {
                        parentsToBeSet.put(wiki, wikiXml.getParent());
                    }
                    importedWikiNames.add(wikiXml.getName());
                }

                // Import any wiki subdirectories that weren't present in the XML metadata using default metadata values
                for (File wikiSubDir : wikisDir.listFiles())
                {
                    if (wikiSubDir.isDirectory() && !importedWikiNames.contains(wikiSubDir.getName()))
                    {
                        importWiki(wikiSubDir.getName(), null, true, wikiSubDir, ctx);
                        importedWikiNames.add(wikiSubDir.getName());
                    }
                }

                setParents(ctx, parentsToBeSet);
            }
        }

        private void setParents(ImportContext<FolderDocument.Folder> ctx, Map<Wiki, String> parentsToBeSet)
                throws SQLException
        {
            for (Map.Entry<Wiki, String> entry : parentsToBeSet.entrySet())
            {
                Wiki parentWiki = WikiSelectManager.getWiki(ctx.getContainer(), new HString(entry.getValue()));
                if (parentWiki == null)
                {
                    ctx.getLogger().warn("Could not find parent wiki: " + entry.getValue());
                }
                else
                {
                    Wiki childWiki = entry.getKey();
                    childWiki.setParent(parentWiki.getRowId());
                    WikiManager.get().updateWiki(ctx.getUser(), childWiki, childWiki.getLatestVersion());
                }
            }
        }

        private Wiki importWiki(String name, String title, boolean showAttachments, File wikiSubDir, ImportContext ctx) throws IOException, SQLException, ImportException
        {
            Wiki existingWiki = WikiSelectManager.getWiki(ctx.getContainer(), new HString(name));
            List<String> existingAttachmentNames = new ArrayList<String>();

            Wiki wiki;

            if (existingWiki == null)
            {
                wiki = new Wiki(ctx.getContainer(), new HString(name));
            }
            else
            {
                wiki = existingWiki;
                for (Attachment attachment : wiki.getAttachments())
                {
                    existingAttachmentNames.add(attachment.getName());
                }
            }
            wiki.setShowAttachments(showAttachments);

            File contentFile = findContentFile(wikiSubDir, name);

            WikiVersion wikiversion = new WikiVersion(wiki.getName());
            wikiversion.setBody(PageFlowUtil.getFileContentsAsString(contentFile));
            wikiversion.setRendererTypeEnum(WikiRendererType.getType(contentFile.getName()));

            List<AttachmentFile> attachments = new ArrayList<AttachmentFile>();
            for (File file : wikiSubDir.listFiles())
            {
                if (!file.equals(contentFile) && file.isFile())
                {
                    attachments.add(new FileAttachmentFile(file));
                }
            }

            wikiversion.setTitle(title == null ? wiki.getName() : new HString(title));
            if (existingWiki == null)
            {
                WikiManager.get().insertWiki(ctx.getUser(), ctx.getContainer(), wiki, wikiversion, attachments);
            }
            else
            {
                WikiManager.get().updateWiki(ctx.getUser(), wiki, wikiversion);
                WikiManager.get().updateAttachments(ctx.getUser(), wiki, existingAttachmentNames, attachments);
            }
            return wiki;
        }

        private File findContentFile(File dir, String wikiName) throws ImportException
        {
            Map<String, File> files = new CaseInsensitiveHashMap<File>();
            for (File file : dir.listFiles())
            {
                if (file.isFile())
                {
                    files.put(file.getName(), file);
                }
            }

            for (WikiRendererType wikiRendererType : WikiRendererType.values())
            {
                File file = files.get(wikiRendererType.getDocumentName(wikiName));
                if (file != null)
                {
                    return file;
                }
            }

            throw new ImportException("Could not find a content file for wiki with name \"" + wikiName + "\"");
        }

        @Override
        public Collection<PipelineJobWarning> postProcess(ImportContext<FolderDocument.Folder> ctx, File root) throws Exception
        {
            return Collections.emptySet();
        }
    }
}
