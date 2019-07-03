package org.labkey.study.assay;

import org.labkey.api.assay.AssayToStudyMigrationService;

public class AssayToStudyMigrationServiceImpl implements AssayToStudyMigrationService
{
    @Override
    public ModuleAssayCollections getModuleAssayCollections()
    {
        return ModuleAssayCache.get().getModuleAssayCollections();
    }
}
