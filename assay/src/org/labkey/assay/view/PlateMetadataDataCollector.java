package org.labkey.assay.view;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.assay.AssayDataCollector;
import org.labkey.api.assay.AssayProvider;
import org.labkey.api.assay.AssayRunUploadContext;
import org.labkey.api.assay.FileUploadDataCollector;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.FileUtil;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;

public class PlateMetadataDataCollector<ContextType extends AssayRunUploadContext<? extends AssayProvider>> extends FileUploadDataCollector<ContextType>
{
    private AssayRunUploadContext _context;

    public PlateMetadataDataCollector(int maxFileInputs, AssayRunUploadContext context)
    {
        super(maxFileInputs, Collections.emptyMap(), AssayDataCollector.PLATE_METADATA_FILE);
        _context = context;
    }

    public AssayRunUploadContext getContext()
    {
        return _context;
    }

    public String getInputName()
    {
        return AssayDataCollector.PLATE_METADATA_FILE;
    }

    @Override
    public HttpView getView(ContextType context)
    {
        return new JspView<FileUploadDataCollector>("/org/labkey/assay/view/plateMetadataFileUpload.jsp", this);
    }

    @Override
    public @NotNull Map<String, File> createData(ContextType context) throws IOException, IllegalArgumentException, ExperimentException
    {
        Map<String, File> createdData = super.createData(context);
        for (File file : createdData.values())
        {
            if (!FileUtil.getExtension(file).equalsIgnoreCase("json"))
            {
                ExperimentException x = new ExperimentException("Plate metadata must be of type : JSON.");
                ExceptionUtil.decorateException(x, ExceptionUtil.ExceptionInfo.SkipMothershipLogging, "true", true);
                throw x;
            }
        }
        return createdData;
    }
}
