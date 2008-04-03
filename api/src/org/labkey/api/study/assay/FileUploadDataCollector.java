package org.labkey.api.study.assay;

import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpProtocol;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.Collections;
import java.util.Iterator;
import java.io.File;
import java.io.IOException;

/**
 * User: jeckels
 * Date: Jul 12, 2007
 */
public class FileUploadDataCollector extends AbstractAssayDataCollector
{
    public String getHTML(AssayRunUploadContext context)
    {
        return "<input type=\"file\" size=\"40\" name=\"uploadedFile\" />";
    }

    public String getShortName()
    {
        return "File upload";
    }

    public String getDescription()
    {
        return "Upload a data file";
    }

    public Map<String, File> createData(AssayRunUploadContext context) throws IOException, IllegalArgumentException, ExperimentException
    {
        if (!(context.getRequest() instanceof MultipartHttpServletRequest))
            throw new IllegalStateException("Expected MultipartHttpServletRequest when posting files.");
        
        Map<String, File> files = savePostedFiles(context, Collections.singleton("uploadedFile"));
        if (files.isEmpty())
            throw new ExperimentException("No data file was uploaded. Please enter a file name.");
        return files;
    }

    public boolean isVisible()
    {
        return true;
    }
}
