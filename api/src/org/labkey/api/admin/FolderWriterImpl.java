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

package org.labkey.api.admin;

import org.apache.logging.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.util.logging.LogHelper;
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
    public static final Logger LOG = LogHelper.getLogger(FolderWriterImpl.class, "Coordinates folder exports");

    private final Collection<FolderWriter> _writers;

    public FolderWriterImpl()
    {
        FolderSerializationRegistry registry = FolderSerializationRegistry.get();
        if (null == registry)
        {
            throw new RuntimeException();
        }

        _writers = registry.getRegisteredFolderWriters();
    }

    @Override
    public String getDataType()
    {
        return null;
    }

    protected BaseFolderWriter createSubfolderWriter()
    {
        return new SubfolderWriter();
    }

    @Override
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
            BaseFolderWriter subfolderWriter = createSubfolderWriter();
            subfolderWriter.write(c, ctx, vf);
        }

        if (ctx.isIncludeFolderXml())
            writeFolderXml(c, ctx, vf);

        LOG.info("Done exporting folder to " + vf.getLocation());
    }

    // This writer is responsible for folder.xml. It writes the top-level folder attributes and saves out the bean when it's complete.
    private void writeFolderXml(Container c, FolderExportContext ctx, VirtualFile vf) throws Exception
    {
        FolderDocument.Folder folderXml = ctx.getXml();

        // Insert standard comment explaining where the data lives, who exported it, and when
        if (ctx.isAddExportComment())
            XmlBeansUtil.addStandardExportComment(folderXml, c, ctx.getUser());

        folderXml.setArchiveVersion(AppProps.getInstance().getSchemaVersion());
        folderXml.setLabel(c.getName());

        folderXml.setType(c.getContainerType().getName());
        folderXml.setTitle(c.getTitle());
        if (c.getDescription() != null)
            folderXml.setDescription(c.getDescription());

        // Ask LookAndFeelProperties for actual stored values (we don't want inherited values)
        LookAndFeelProperties props = LookAndFeelProperties.getInstance(c);

        String defaultDateFormat = props.getDefaultDateFormatStored();
        if (null != defaultDateFormat)
            folderXml.setDefaultDateFormat(defaultDateFormat);

        String defaultDateTimeFormat = props.getDefaultDateTimeFormatStored();
        if (null != defaultDateTimeFormat)
            folderXml.setDefaultDateTimeFormat(defaultDateTimeFormat);

        String defaultNumberFormat = props.getDefaultNumberFormatStored();
        if (null != defaultNumberFormat)
            folderXml.setDefaultNumberFormat(defaultNumberFormat);

        String extraDateParsingPattern = props.getExtraDateParsingPatternStored();
        if (null != extraDateParsingPattern)
            folderXml.setExtraDateParsingPattern(extraDateParsingPattern);

        String extraDateTimeParsingPattern = props.getExtraDateTimeParsingPatternStored();
        if (null != extraDateTimeParsingPattern)
            folderXml.setExtraDateTimeParsingPattern(extraDateTimeParsingPattern);

        if (props.areRestrictedColumnsEnabled())
            folderXml.setRestrictedColumnsEnabled(true);

        // Save the folder.xml file. This gets called last, after all other writers have populated the other sections.
        vf.saveXmlBean("folder.xml", ctx.getDocument());

        ctx.lockDocument();
    }
}

