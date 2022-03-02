/*
 * Copyright (c) 2012-2018 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.labkey.api.admin.AbstractFolderImportFactory;
import org.labkey.api.admin.FolderArchiveDataTypes;
import org.labkey.api.admin.FolderImportContext;
import org.labkey.api.admin.FolderImporter;
import org.labkey.api.admin.ImportContext;
import org.labkey.api.admin.ImportException;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.attachments.InputStreamAttachmentFile;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobWarning;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.wiki.WikiRendererType;
import org.labkey.api.writer.VirtualFile;
import org.labkey.data.xml.wiki.WikiType;
import org.labkey.data.xml.wiki.WikisDocument;
import org.labkey.data.xml.wiki.WikisType;
import org.labkey.folder.xml.FolderDocument.Folder;
import org.labkey.wiki.WikiManager;
import org.labkey.wiki.WikiSelectManager;
import org.labkey.wiki.model.Wiki;
import org.labkey.wiki.model.WikiVersion;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * User: jeckels
 * Date: Jan 18, 2012
 */
public class WikiImporterFactory extends AbstractFolderImportFactory
{
    @Override
    public FolderImporter create()
    {
        return new WikiImporter();
    }

    private static class WikiImporter implements FolderImporter
    {
        @Override
        public String getDataType()
        {
            return FolderArchiveDataTypes.WIKIS_AND_THEIR_ATTACHMENTS;
        }

        @Override
        public String getDescription()
        {
            return "wikis";
        }

        @Override
        public void process(PipelineJob job, FolderImportContext ctx, VirtualFile root) throws Exception
        {
            if (isValidForImportArchive(ctx))
            {
                VirtualFile wikisDir = ctx.getDir("wikis");

                if (null != job)
                    job.setStatus("IMPORT " + getDescription());
                ctx.getLogger().info("Loading " + getDescription());

                Set<String> importedFolderNames = new CaseInsensitiveHashSet();
                Map<Wiki, String> parentsToBeSet = new HashMap<>();

                // since the WikiWriter saves the webdav tree, the files need to be accessed as InputStreams
                try (InputStream wikisIS = wikisDir.getInputStream(WikiWriterFactory.WIKIS_FILENAME))
                {
                    if (null == wikisIS)
                    {
                        throw new ImportException("Could not find expected file: " + WikiWriterFactory.WIKIS_FILENAME);
                    }

                    WikisDocument document = WikisDocument.Factory.parse(wikisIS);
                    WikisType rootNode = document.getWikis();
                    int displayOrder = 0;
                    for (WikiType wikiXml : rootNode.getWikiArray())
                    {
                        VirtualFile wikiSubDir = wikisDir.getDir(wikiXml.getName());
                        if (null == wikiSubDir)
                        {
                            ctx.getLogger().error("Could not find content subdirectory for wiki with name \"" + wikiXml.getName() + "\"");
                            continue;
                        }
                        // ensure that older versions of exported wikis that do not have the shouldIndex bit set
                        // continue to be indexed by default
                        boolean shouldIndex = !wikiXml.isSetShouldIndex() || wikiXml.getShouldIndex();

                        // TODO: We should add VirtualFile.getName()
                        String folderName = new File(wikiSubDir.getLocation()).getName();
                        Wiki wiki = importWiki(wikiXml.getName(), wikiXml.getTitle(), shouldIndex, wikiXml.getShowAttachments(), wikiSubDir, folderName, ctx, displayOrder++, wikiXml.getAttachmentsOrder());
                        if (wikiXml.getParent() != null)
                        {
                            parentsToBeSet.put(wiki, wikiXml.getParent());
                        }
                        importedFolderNames.add(folderName);
                    }

                    // Import any wiki subdirectories that weren't present in the XML metadata using default metadata values
                    for (String wikiSubDirName : wikisDir.listDirs())
                    {
                        VirtualFile wikiSubDir = wikisDir.getDir(wikiSubDirName);
                        if (null != wikiSubDir && !importedFolderNames.contains(wikiSubDirName))
                        {
                            importWiki(wikiSubDirName, null, true, true, wikiSubDir, wikiSubDirName, ctx, displayOrder++, null);
                            importedFolderNames.add(wikiSubDirName);
                        }
                    }

                    setParents(ctx, parentsToBeSet);

                    ctx.getLogger().info(importedFolderNames.size() + " wiki" + (1 == importedFolderNames.size() ? "" : "s") + " imported");
                    ctx.getLogger().info("Done importing " + getDescription());
                }
            }
        }

        private void setParents(FolderImportContext ctx, Map<Wiki, String> parentsToBeSet)
        {
            for (Map.Entry<Wiki, String> entry : parentsToBeSet.entrySet())
            {
                // Look up the parent in the database because it's possible that it wasn't included in the archive
                // that we imported, but was already present
                Wiki parentWiki = WikiSelectManager.getWiki(ctx.getContainer(), entry.getValue());
                if (parentWiki == null)
                {
                    ctx.getLogger().warn("Could not find parent wiki: " + entry.getValue());
                }
                else
                {
                    Wiki childWiki = entry.getKey();
                    childWiki.setParent(parentWiki.getRowId());
                    WikiManager.get().updateWiki(ctx.getUser(), childWiki, childWiki.getLatestVersion(), false);
                }
            }
        }

        private Wiki importWiki(String name, String title, boolean shouldIndex, boolean showAttachments, VirtualFile wikiSubDir, String folderName, ImportContext<Folder> ctx, int displayOrder, String attachmentOrder) throws IOException, ImportException
        {
            Wiki existingWiki = WikiSelectManager.getWiki(ctx.getContainer(), name);
            List<String> existingAttachmentNames = new ArrayList<>();

            Wiki wiki;

            if (existingWiki == null)
            {
                wiki = new Wiki(ctx.getContainer(), name);
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
            wiki.setShouldIndex(shouldIndex);
            wiki.setDisplayOrder(displayOrder);

            // since the WikiWriter saves the webdav tree, the files need to be accessed as InputStreams
            Pair<String, InputStream> contentSteamPair = findContentStream(wikiSubDir, folderName);
            String contentFileName = contentSteamPair.first;
            InputStream contentSteam = contentSteamPair.second;

            WikiVersion wikiversion = new WikiVersion(wiki.getName());
            wikiversion.setBody(PageFlowUtil.getStreamContentsAsString(contentSteam));
            wikiversion.setRendererTypeEnum(WikiRendererType.getType(contentFileName));

            List<AttachmentFile> attachments = new ArrayList<>();
            for (String fileName : wikiSubDir.list())
            {
                if (!fileName.equals(contentFileName) && null != wikiSubDir.getInputStream(fileName))
                {
                    InputStream aIS = wikiSubDir.getInputStream(fileName);
                    attachments.add(new InputStreamAttachmentFile(aIS, fileName));
                }
            }
            AtomicInteger inc = new AtomicInteger();
            Map<String, Integer> attachmentOrderMap = List.of(attachmentOrder.split(";")).stream().collect(Collectors.toMap(i -> i, i -> inc.getAndIncrement()));
            attachments.sort(Comparator.comparing(e -> attachmentOrderMap.get(e.getFilename())));

            wikiversion.setTitle(title == null ? wiki.getName() : title);
            if (existingWiki == null)
            {
                WikiManager.get().insertWiki(ctx.getUser(), ctx.getContainer(), wiki, wikiversion, attachments, false);
            }
            else
            {
                WikiManager.get().updateWiki(ctx.getUser(), wiki, wikiversion, false);
                WikiManager.get().updateAttachments(ctx.getUser(), wiki, existingAttachmentNames, attachments);
            }
            return wiki;
        }

        // Currently, content filename is the same as the folder name, plus an extension that determines the type
        // TODO: We should store the type and/or the content filename
        private Pair<String, InputStream> findContentStream(VirtualFile dir, String contentName) throws ImportException, IOException
        {
            for (WikiRendererType wikiRendererType : WikiRendererType.values())
            {
                InputStream is = dir.getInputStream(wikiRendererType.getDocumentName(contentName));
                if (null != is)
                {
                    return new Pair<>(wikiRendererType.getDocumentName(contentName), is);
                }
            }

            throw new ImportException("Could not find a content file for wiki with name \"" + contentName + "\"");
        }

        @NotNull
        @Override
        public Collection<PipelineJobWarning> postProcess(FolderImportContext ctx, VirtualFile root)
        {
            return Collections.emptySet();
        }

        @Override
        public boolean isValidForImportArchive(FolderImportContext ctx) throws ImportException
        {
            return ctx.getDir("wikis") != null;
        }
    }
}
