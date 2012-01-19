package org.labkey.pipeline.importer;

import org.springframework.validation.BindException;

import java.io.File;

/**
 * User: cnathe
 * Date: Jan 18, 2012
 */
public interface FolderJobSupport
{
    FolderImportContext getImportContext();
    File getRoot();
    String getOriginalFilename();
}
