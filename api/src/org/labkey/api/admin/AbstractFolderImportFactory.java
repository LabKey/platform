package org.labkey.api.admin;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Jul 6, 2012
 */
public abstract class AbstractFolderImportFactory implements FolderImporterFactory
{
    @Override
    public int getPriority()
    {
        return DEFAULT_PRIORITY;
    }
}
