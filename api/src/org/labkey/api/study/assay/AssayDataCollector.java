package org.labkey.api.study.assay;

import org.labkey.api.exp.ExperimentException;
import org.labkey.api.study.assay.AssayRunUploadContext;
import org.labkey.api.study.actions.AssayRunUploadForm;
import org.labkey.api.view.InsertView;

import java.util.Map;
import java.io.File;
import java.io.IOException;

/**
 * User: jeckels
 * Date: Jul 12, 2007
 */
public interface AssayDataCollector
{
    public String getHTML(AssayRunUploadContext context);

    public String getShortName();

    public String getDescription();

    public Map<String, File> createData(AssayRunUploadContext context) throws IOException, ExperimentException;

    public boolean isVisible();

    void uploadComplete(AssayRunUploadContext context);

    public boolean allowAdditionalUpload(AssayRunUploadContext context);
}
