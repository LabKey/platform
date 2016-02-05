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

import org.apache.xmlbeans.XmlException;
import org.labkey.api.data.Container;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.reports.report.view.ReportUtil;
import org.labkey.api.security.User;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.util.XmlValidationException;
import org.labkey.api.writer.VirtualFile;
import org.labkey.folder.xml.FolderDocument;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * User: cnathe
 * Date: Jan 18, 2012
 */
public class FolderImportContext extends AbstractFolderContext
{
    private File _folderXml;
    HashSet<String> _importedReports = new HashSet<>();

    /** Required for xstream serialization on Java 7 */
    @SuppressWarnings({"UnusedDeclaration"})
    public FolderImportContext()
    {
        super(null, null, null, null, null, null);
    }

    public FolderImportContext(User user, Container c, File folderXml, Set<String> dataTypes, LoggerGetter logger, VirtualFile root)
    {
        super(user, c, null, dataTypes, logger, root);
        _folderXml = folderXml;
    }

    public FolderImportContext(User user, Container c, FolderDocument folderDoc, Set<String> dataTypes, LoggerGetter logger, VirtualFile root)
    {
        super(user, c, folderDoc, dataTypes, logger, root);
    }

    @Override
    public synchronized FolderDocument getDocument() throws ImportException, InvalidFileException
    {
        FolderDocument folderDoc = super.getDocument();

        // XStream can't seem to serialize the FolderDocument XMLBean, so we initially set to null and parse the file on demand
        if (null == folderDoc)
        {
            try
            {
                folderDoc = readFolderDocument(_folderXml);
            }
            catch (IOException e)
            {
                throw new ImportException("Exception loading folder.xml file", e);
            }

            setDocument(folderDoc);
        }

        return folderDoc;
    }

    // Assume file was referenced in folder.xml file   // TODO: Context should hold onto the root -- shouldn't have to pass it in
    public File getFolderFile(File root, File dir, String name) throws ImportException
    {
        return getFolderFile(root, dir, name, _folderXml.getName());
    }

    public File getFolderFile(File root, File dir, String name, String source) throws ImportException
    {
        File file = new File(dir, name);

        if (!file.exists())
            throw new ImportException(source + " refers to a file that does not exist: " + ImportException.getRelativePath(root, file));

        if (!file.isFile())
            throw new ImportException(source + " refers to " + ImportException.getRelativePath(root, file) + ": expected a file but found a directory");

        return file;
    }

    private FolderDocument readFolderDocument(File folderXml) throws ImportException, IOException, InvalidFileException
    {
        if (!folderXml.exists())
            throw new ImportException(folderXml.getName() + " file does not exist.");

        FolderDocument folderDoc;

        try
        {
            folderDoc = FolderDocument.Factory.parse(folderXml, XmlBeansUtil.getDefaultParseOptions());
            XmlBeansUtil.validateXmlDocument(folderDoc, folderXml.getName());
        }
        catch (XmlException e)
        {
            throw new InvalidFileException(folderXml.getParentFile(), folderXml, e);
        }
        catch (XmlValidationException e)
        {
            throw new InvalidFileException(folderXml.getParentFile(), folderXml, e);
        }

        return folderDoc;
    }

    @Override
    public Double getArchiveVersion()
    {
        try {
            FolderDocument folderDoc = getDocument();
            return folderDoc.getFolder() != null ? folderDoc.getFolder().getArchiveVersion() : null;
        }
        catch (Exception e)
        {
            return null;
        }
    }

    public boolean isImportedReport(ReportDescriptor d)
    {
        return _importedReports.contains(ReportUtil.getSerializedName(d));
    }

    public void addImportedReport(ReportDescriptor d)
    {
        _importedReports.add(ReportUtil.getSerializedName(d));
    }
}
