package org.labkey.api.assay;

import org.apache.log4j.Logger;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.XarContext;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpDataRunInput;
import org.labkey.api.util.FileType;
import org.labkey.api.view.ViewBackgroundInfo;

import java.io.File;

public class PlateMetadataDataHandler extends AbstractAssayTsvDataHandler
{
    public static final String NAMESPACE = "AssayRunTSVPlateMetadata";
    public static final AssayDataType DATA_TYPE;

    static
    {
        FileType fileType = new FileType(".json");
        fileType.setExtensionsMutuallyExclusive(false);
        DATA_TYPE = new AssayDataType(NAMESPACE, fileType, ExpDataRunInput.IMPORTED_DATA_ROLE);
    }

    @Override
    protected boolean allowEmptyData()
    {
        return false;
    }

    @Override
    protected boolean shouldAddInputMaterials()
    {
        return false;
    }

    @Override
    public DataType getDataType()
    {
        return DATA_TYPE;
    }

    public Priority getPriority(ExpData data)
    {
        Lsid lsid = new Lsid(data.getLSID());
        if (DATA_TYPE.matches(lsid))
        {
            return Priority.HIGH;
        }
        return null;
    }

    @Override
    public void importFile(ExpData data, File dataFile, ViewBackgroundInfo info, Logger log, XarContext context) throws ExperimentException
    {
        // need to implement a json data loader
    }
}
