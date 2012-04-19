/*
 * Copyright (c) 2012 LabKey Corporation
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
package org.labkey.wiki.export;

import org.labkey.api.admin.FolderImporter;
import org.labkey.api.admin.FolderImporterFactory;
import org.labkey.api.admin.ImportContext;
import org.labkey.api.admin.ImportException;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.attachments.FileAttachmentFile;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobWarning;
import org.labkey.api.util.HString;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.wiki.WikiRendererType;
import org.labkey.api.writer.VirtualFile;
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
public class WikiImporterFactory implements FolderImporterFactory
{
    @Override
    public FolderImporter create()
    {
        return new WikiImporter();
    }

    @Override
    public boolean isFinalImporter()
    {
        return false;
    }

    private class WikiImporter implements FolderImporter<FolderDocument.Folder>
    {
        @Override
        public String getDescription()
        {
            return "wikis";
        }

        @Override
        public void process(PipelineJob job, ImportContext<FolderDocument.Folder> ctx, VirtualFile root) throws Exception
        {
            File wikisDir = ctx.getDir("wikis");
            if (wikisDir != null)
            {
                if (null != job)
                    job.setStatus("IMPORT " + getDescription());
                ctx.getLogger().info("Loading " + getDescription());

                File wikisXmlFile = new File(wikisDir, WikiWriterFactory.WIKIS_FILENAME);

                if (!wikisXmlFile.exists())
                {
                    throw new ImportException("Could not find expected file: " + wikisXmlFile);
                }

                Set<String> importedWikiNames = new CaseInsensitiveHashSet();
                Map<Wiki, String> parentsToBeSet = new HashMap<Wiki, String>();

                WikisDocument document = WikisDocument.Factory.parse(wikisXmlFile);
                WikisType rootNode = document.getWikis();
                int displayOrder = 0;
                for (WikiType wikiXml : rootNode.getWikiArray())
                {
                    File wikiSubDir = new File(wikisDir, wikiXml.getName());
                    if (!wikiSubDir.isDirectory())
                    {
                        ctx.getLogger().error("Could not find content subdirectory for wiki with name \"" + wikiXml.getName() + "\"");
                    }
                    Wiki wiki = importWiki(wikiXml.getName(), wikiXml.getTitle(), wikiXml.getShowAttachments(), wikiSubDir, ctx, displayOrder++);
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
                        importWiki(wikiSubDir.getName(), null, true, wikiSubDir, ctx, displayOrder++);
                        importedWikiNames.add(wikiSubDir.getName());
                    }
                }

                setParents(ctx, parentsToBeSet);

                ctx.getLogger().info("Done importing " + getDescription());
            }
        }

        private void setParents(ImportContext<FolderDocument.Folder> ctx, Map<Wiki, String> parentsToBeSet)
                throws SQLException
        {
            for (Map.Entry<Wiki, String> entry : parentsToBeSet.entrySet())
            {
                // Look up the parent in the database because it's possible that it wasn't included in the archive
                // that we imported, but was already present
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

        private Wiki importWiki(String name, String title, boolean showAttachments, File wikiSubDir, ImportContext ctx, int displayOrder) throws IOException, SQLException, ImportException
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
            wiki.setDisplayOrder(displayOrder);

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
        public Collection<PipelineJobWarning> postProcess(ImportContext<FolderDocument.Folder> ctx, VirtualFile root) throws Exception
        {
            return Collections.emptySet();
        }

        @Override
        public boolean supportsVirtualFile()
        {
            return false;
        }        
    }
}
