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
import org.labkey.api.data.DbSequence;
import org.labkey.api.data.DbSequenceManager;
import org.labkey.api.gwt.client.AuditBehaviorType;
import org.labkey.api.module.FolderType;
import org.labkey.api.module.FolderTypeManager;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.reports.report.view.ReportUtil;
import org.labkey.api.security.User;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.util.XmlValidationException;
import org.labkey.api.writer.VirtualFile;
import org.labkey.folder.xml.FolderDocument;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.labkey.api.exp.XarContext.XAR_JOB_ID_NAME;

/**
 * User: cnathe
 * Date: Jan 18, 2012
 */
public class FolderImportContext extends AbstractFolderContext
{
    private Path _folderXml;

    private String _xarJobId;

    private final HashSet<String> _importedReports = new HashSet<>();

    private static final String FOLDER_IMPORT_DB_SEQUENCE_PREFIX = "FolderImportJobCounter-";

    private boolean _isNewFolderImport; // if we know the target folder is empty, can skip certain merge logic

    public static final String IS_NEW_FOLDER_IMPORT_KEY = "isNewFolderImport";

    /** Required for xstream serialization on Java 7 */
    @SuppressWarnings({"UnusedDeclaration"})
    public FolderImportContext()
    {
        super(null, null, null, null, null, null);
    }

    public FolderImportContext(User user, Container c, Path folderXml, Set<String> dataTypes, LoggerGetter logger, VirtualFile root)
    {
        super(user, c, null, dataTypes, logger, root);
        _folderXml = folderXml;
    }

    public FolderImportContext(User user, Container c, FolderDocument folderDoc, Set<String> dataTypes, LoggerGetter logger, VirtualFile root)
    {
        super(user, c, folderDoc, dataTypes, logger, root);
    }

    public FolderImportContext(FolderImportContext original, VirtualFile root) throws ImportException
    {
        super(original.getUser(), original.getContainer(), original.getDocument(), original.getDataTypes(), original.getLoggerGetter(), root);
        this.setActivity(original.getActivity());
        this.setCreateSharedDatasets(original.isCreateSharedDatasets());
        this.setIncludeSubfolders(original.isIncludeSubfolders());
        this.setFailForUndefinedVisits(original.isFailForUndefinedVisits());
        this.setLoggerGetter(original.getLoggerGetter());
        this.setAddExportComment(original.isAddExportComment());
        this.setSkipQueryValidation(original.isSkipQueryValidation());
    }

    @Override
    public synchronized FolderDocument getDocument() throws ImportException
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

    private FolderDocument readFolderDocument(Path folderXml) throws ImportException, IOException
    {
        if (!Files.exists(folderXml))
            throw new ImportException(folderXml.getFileName() + " file does not exist.");

        FolderDocument folderDoc;

        try (InputStream inputStream = Files.newInputStream(folderXml))
        {
            folderDoc = FolderDocument.Factory.parse(inputStream, XmlBeansUtil.getDefaultParseOptions());
            XmlBeansUtil.validateXmlDocument(folderDoc, folderXml.getFileName().toString());
        }
        catch (XmlException | XmlValidationException e)
        {
            throw new InvalidFileException(folderXml.getParent(), folderXml, e);
        }

        return folderDoc;
    }

    @Override
    public Double getArchiveVersion()
    {
        try
        {
            FolderDocument folderDoc = getDocument();
            return folderDoc.getFolder() != null ? folderDoc.getFolder().getArchiveVersion() : null;
        }
        catch (Exception e)
        {
            return null;
        }
    }

    public String getXarJobId()
    {
        return _xarJobId;
    }

    public Map<String, String> getXarJobIdContext()
    {
        if (_xarJobId == null)
        {
            DbSequence newSequence = DbSequenceManager.get(getContainer(), FOLDER_IMPORT_DB_SEQUENCE_PREFIX);
            _xarJobId = "Xar-" + newSequence.next();
            /* NOTE: I think this does create non-unique values for the DB_SEQUENCE parts of the LSID
             * e.g.
             *  "Xar-1" + "23" -> Xar-123
             *  "Xar-12" + "3" -> Xar-123
             * Maybe this is not a problem in practice because of other unique parts of the LSID, however, we might consider doing this differently.
             */
        }

        return new HashMap<>()
        {{
            put(XAR_JOB_ID_NAME, _xarJobId);
        }};
    }

    public boolean isImportedReport(ReportDescriptor d)
    {
        return _importedReports.contains(ReportUtil.getSerializedName(d));
    }

    public void addImportedReport(ReportDescriptor d)
    {
        _importedReports.add(ReportUtil.getSerializedName(d));
    }

    @Override
    public AuditBehaviorType getAuditBehaviorType() throws Exception
    {
        var xmlFolderType = this.getXml().getFolderType();
        FolderType folderType = null;
        if (xmlFolderType != null)
            folderType = FolderTypeManager.get().getFolderType(xmlFolderType.getName());
        return folderType == null ? null : folderType.getImportAuditBehavior();
    }

    public boolean isNewFolderImport()
    {
        return _isNewFolderImport;
    }

    public void setNewFolderImport(boolean newFolderImport)
    {
        _isNewFolderImport = newFolderImport;
    }

}
