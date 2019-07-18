package org.labkey.assay;

import org.labkey.api.assay.AssayToStudyMigrationService;
import org.labkey.api.gwt.client.assay.AssayService;
import org.labkey.api.view.ViewContext;

public class AssayToStudyMigrationServiceImpl implements AssayToStudyMigrationService
{
    @Override
    public AssayService getAssayService(ViewContext context)
    {
        return new AssayServiceImpl(context);
    }
}
