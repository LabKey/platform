package org.labkey.api.study.assay;


/**
 * User: jeckels
 * Date: Jul 13, 2007
 */
public abstract class AbstractAssayDataCollector extends AssayFileWriter implements AssayDataCollector
{
    public void uploadComplete(AssayRunUploadContext context)
    {
        
    }

    public boolean allowAdditionalUpload(AssayRunUploadContext context)
    {
        return true;
    }
}
