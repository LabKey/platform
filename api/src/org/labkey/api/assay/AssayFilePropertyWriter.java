/*
 * Copyright (c) 2025-2026 LabKey Corporation
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
package org.labkey.api.assay;

import org.apache.logging.log4j.Logger;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.TransactionAuditProvider;
import org.labkey.api.audit.provider.FileSystemAuditProvider;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.security.User;
import org.labkey.api.util.FileUtil;
import org.labkey.vfs.FileLike;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.Collections.unmodifiableSet;
import static org.labkey.api.util.FileUtil.toFileForWrite;

public class AssayFilePropertyWriter<ContextType extends AssayRunUploadContext<? extends AssayProvider>> extends AssayFileWriter<ContextType>
{
    private final Set<FileLike> _uploadedFiles = new HashSet<>();
    private final List<PostedFile> _unrecordedFiles = new ArrayList<>();

    private record PostedFile(String fieldName, String originalName, File file, String auditComment) {}

    public void cleanupPostedFiles(ContextType context)
    {
        cleanupPostedFiles(context.getContainer(), context.getUploadedPropertyFiles(), context.getProtocol().getName(), context.getLogger());
    }

    public void cleanupPostedFiles(Container container, String protocolName, Logger logger)
    {
        cleanupPostedFiles(container, _uploadedFiles, protocolName, logger);
    }

    public static void cleanupPostedFiles(Container container, Set<FileLike> files, String protocolName, Logger logger)
    {
        if (files == null || files.isEmpty())
            return;

        FileLike targetDir = AssayFileWriter.getUploadDirectory(container);

        for (var file : files)
        {
            try
            {
                if (file.exists() && file.isFile() && targetDir.equals(file.getParent()))
                    file.delete();
            }
            catch (IOException e)
            {
                if (logger != null)
                    logger.error("Failed to clean up file {} for protocol \"{}\" in folder {} after failed run import", file, protocolName, container.getPath(), e);
            }
        }
    }

    /** Writes the posted files to the upload directory; {@link #recordPostedFiles} adds their exp.data rows and audit events. */
    public CaseInsensitiveHashMap<FileLike> writePostedFiles(Container container, Map<String, MultipartFile> filePropertyMap, String auditComment) throws ExperimentException
    {
        FileLike targetDirectory = AssayFileWriter.ensureUploadDirectory(container);
        CaseInsensitiveHashMap<FileLike> properties = new CaseInsensitiveHashMap<>();

        try
        {
            for (Map.Entry<String, MultipartFile> entry : filePropertyMap.entrySet())
            {
                MultipartFile multipartFile = entry.getValue();
                String originalName = multipartFile.getOriginalFilename();
                String legalName = FileUtil.makeLegalName(originalName);
                FileLike fileLike = FileUtil.findUniqueFileName(legalName, targetDirectory);
                File file = toFileForWrite(fileLike);
                multipartFile.transferTo(file);

                _uploadedFiles.add(fileLike);
                _unrecordedFiles.add(new PostedFile(entry.getKey(), originalName, file, auditComment));

                properties.put(entry.getKey(), fileLike);
            }
        }
        catch (IOException e)
        {
            throw new ExperimentException("Unable to write uploaded file.", e);
        }

        return properties;
    }

    public void recordPostedFiles(Container container, User user, TransactionAuditProvider.TransactionAuditEvent auditTransactionEvent)
    {
        DataType dataType = ExperimentService.get().getDataType("RawAssayData");
        for (PostedFile posted : _unrecordedFiles)
        {
            ExpData data = ExperimentService.get().createData(container, dataType);
            data.setDataFileURI(FileUtil.getAbsoluteCaseSensitiveFile(posted.file()).toURI());
            data.setName(posted.originalName());
            data.save(user);

            FileSystemAuditProvider.FileSystemAuditEvent event = new FileSystemAuditProvider.FileSystemAuditEvent(container, posted.auditComment());
            event.setProvidedFileName(posted.originalName());
            event.setFile(posted.file().getName());
            event.setDirectory(posted.file().getParent());
            event.setTransactionEvent(auditTransactionEvent, FileSystemAuditProvider.EVENT_TYPE);
            event.setFieldName(posted.fieldName());
            AuditLogService.get().addEvent(user, event);
        }
        _unrecordedFiles.clear();
    }

    public Set<FileLike> getUploadedFiles()
    {
        return unmodifiableSet(_uploadedFiles);
    }
}
