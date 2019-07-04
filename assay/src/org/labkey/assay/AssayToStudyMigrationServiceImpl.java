package org.labkey.assay;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.assay.AssayToStudyMigrationService;
import org.labkey.api.assay.ModuleAssayCollections;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.study.PlateTypeHandler;
import org.labkey.assay.query.AssaySchemaImpl;
import org.labkey.api.study.assay.AssaySchema;
import org.labkey.assay.plate.PlateManager;

public class AssayToStudyMigrationServiceImpl implements AssayToStudyMigrationService
{
    @Override
    public ModuleAssayCollections getModuleAssayCollections()
    {
        return ModuleAssayCache.get().getModuleAssayCollections();
    }

    @Override
    public AssaySchema getAssaySchema(User user, Container container, @Nullable Container targetStudy)
    {
        return new AssaySchemaImpl(user, container, targetStudy);
    }

    @Override
    public PlateTypeHandler getPlateTypeHandler(String plateTypeName)
    {
        return PlateManager.get().getPlateTypeHandler(plateTypeName);
    }
}
