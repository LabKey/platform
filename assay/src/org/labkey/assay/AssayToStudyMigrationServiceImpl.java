package org.labkey.assay;

import org.labkey.api.assay.AssayToStudyMigrationService;
import org.labkey.study.assay.ModuleAssayCollections;

public class AssayToStudyMigrationServiceImpl implements AssayToStudyMigrationService
{
    @Override
    public ModuleAssayCollections getModuleAssayCollections()
    {
        return ModuleAssayCache.get().getModuleAssayCollections();
    }
}
