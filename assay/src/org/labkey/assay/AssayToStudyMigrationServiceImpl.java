package org.labkey.assay;

import org.labkey.api.assay.AssayToStudyMigrationService;
import org.labkey.api.gwt.client.assay.AssayService;
import org.labkey.api.study.PlateTypeHandler;
import org.labkey.api.view.ViewContext;
import org.labkey.assay.plate.PlateManager;

public class AssayToStudyMigrationServiceImpl implements AssayToStudyMigrationService
{
    @Override
    public PlateTypeHandler getPlateTypeHandler(String plateTypeName)
    {
        return PlateManager.get().getPlateTypeHandler(plateTypeName);
    }

    @Override
    public AssayService getAssayService(ViewContext context)
    {
        return new AssayServiceImpl(context);
    }
}
