/*
 * Copyright (c) 2012-2016 LabKey Corporation
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

package org.labkey.api.admin;

import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.writer.VirtualFile;
import org.labkey.folder.xml.FolderDocument;

import java.util.Collection;
import java.util.Set;

/**
 * User: cnathe
 * Date: Jan 18, 2012
 */
public class FolderWriterImpl extends BaseFolderWriter
{
    private static final Logger LOG = Logger.getLogger(FolderWriterImpl.class);

    private final Collection<FolderWriter> _writers;

    public FolderWriterImpl()
    {
        FolderSerializationRegistry registry = ServiceRegistry.get().getService(FolderSerializationRegistry.class);
        if (null == registry)
        {
            throw new RuntimeException();
        }

        _writers = registry.getRegisteredFolderWriters();
    }

    public String getDataType()
    {
        return null;
    }

    public void write(Container c, FolderExportContext ctx, VirtualFile vf) throws Exception
    {
        LOG.info("Exporting folder to " + vf.getLocation());

        Set<String> dataTypes = ctx.getDataTypes();

        // Initialize all the writers first, allowing them to create module-specific context, etc.
        for (FolderWriter writer : _writers)
        {
            writer.initialize(ctx);
        }

        // Call all the writers next -- this ensures that folder.xml is the last writer called.
        for (FolderWriter writer : _writers)
        {
            String text = writer.getDataType();

            if (null == text || dataTypes.contains(text))
                writer.write(c, ctx, vf);
        }

        // include container tab children in the folder export (and optionally all other subfolders if the user chooses, except workbooks)
        if (c.hasChildren())
        {
            SubfolderWriter subfolderWriter = new SubfolderWriter();
            subfolderWriter.write(c, ctx, vf);
        }

        writeFolderXml(c, ctx, vf);

        LOG.info("Done exporting folder to " + vf.getLocation());
    }

    // This writer is responsible for folder.xml.  It writes the top-level folder attributes and saves out the bean when it's complete.
    private void writeFolderXml(Container c, FolderExportContext ctx, VirtualFile vf) throws Exception
    {
        FolderDocument.Folder folderXml = ctx.getXml();

        // Insert standard comment explaining where the data lives, who exported it, and when
        XmlBeansUtil.addStandardExportComment(folderXml, ctx.getContainer(), ctx.getUser());

        folderXml.setArchiveVersion(ModuleLoader.getInstance().getCoreModule().getVersion());
        folderXml.setLabel(c.getName());

        folderXml.setType(c.getType().name());
        if (c.getTitle() != null)
            folderXml.setTitle(c.getTitle());
        if (c.getDescription() != null)
            folderXml.setDescription(c.getDescription());

        // Ask LookAndFeelProperties for actual stored values (we don't want inherited values)
        LookAndFeelProperties props = LookAndFeelProperties.getInstance(ctx.getContainer());

        String defaultDateFormat = props.getDefaultDateFormatStored();
        if (null != defaultDateFormat)
            folderXml.setDefaultDateFormat(defaultDateFormat);

        String defaultDateTimeFormat = props.getDefaultDateTimeFormatStored();
        if (null != defaultDateTimeFormat)
            folderXml.setDefaultDateTimeFormat(defaultDateTimeFormat);

        String defaultNumberFormat = props.getDefaultNumberFormatStored();
        if (null != defaultNumberFormat)
            folderXml.setDefaultNumberFormat(defaultNumberFormat);

        if (props.areRestrictedColumnsEnabled())
            folderXml.setRestrictedColumnsEnabled(true);

        // Save the folder.xml file.  This gets called last, after all other writers have populated the other sections.
        vf.saveXmlBean("folder.xml", ctx.getDocument());

        ctx.lockDocument();
    }
}

