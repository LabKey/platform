package org.labkey.api.assay;

import org.apache.logging.log4j.Logger;
import org.labkey.api.audit.AuditLogService;
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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static java.util.Collections.unmodifiableSet;
import static org.labkey.api.util.FileUtil.toFileForWrite;

public class AssayFilePropertyWriter<ContextType extends AssayRunUploadContext<? extends AssayProvider>> extends AssayFileWriter<ContextType>
{
    private final Set<FileLike> _uploadedFiles = new HashSet<>();

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

    public CaseInsensitiveHashMap<FileLike> savePostedFiles(Container container, User user, Map<String, MultipartFile> filePropertyMap, Long auditTransactionId, String auditComment) throws ExperimentException
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

                DataType dataType = ExperimentService.get().getDataType("RawAssayData");
                ExpData data = ExperimentService.get().createData(container, dataType);
                data.setDataFileURI(FileUtil.getAbsoluteCaseSensitiveFile(file).toURI());
                data.setName(originalName);
                data.save(user);

                FileSystemAuditProvider.FileSystemAuditEvent event = new FileSystemAuditProvider.FileSystemAuditEvent(container, auditComment);
                event.setProvidedFileName(originalName);
                event.setFile(file.getName());
                event.setDirectory(file.getParent());
                event.setTransactionId(auditTransactionId);
                event.setFieldName(entry.getKey());
                AuditLogService.get().addEvent(user, event);

                properties.put(entry.getKey(), data.getFileLike());
            }
        }
        catch (IOException e)
        {
            throw new ExperimentException("Unable to write uploaded file.", e);
        }

        return properties;
    }

    public Set<FileLike> getUploadedFiles()
    {
        return unmodifiableSet(_uploadedFiles);
    }
}
