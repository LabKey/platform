package org.labkey.api.study.importer;

import org.labkey.api.admin.ImportException;
import org.labkey.api.writer.VirtualFile;
import org.springframework.validation.BindException;

public interface BaseStudyImporter<CONTEXT extends SimpleStudyImportContext>
{
    String getDataType();
    String getDescription();
    void process(CONTEXT ctx, VirtualFile root, BindException errors) throws Exception;

    /**
     * Validate if the study importer is valid for the given import context. Default to true.
     * @return boolean
     */
    default boolean isValidForImportArchive(CONTEXT ctx, VirtualFile root) throws ImportException
    {
        return true;
    }
}
