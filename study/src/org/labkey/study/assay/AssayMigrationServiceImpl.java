package org.labkey.study.assay;

import gwt.client.org.labkey.study.StudyApplication;
import org.labkey.api.assay.AssayMigrationService;
import org.labkey.api.exp.property.DomainImporterServiceBase;
import org.labkey.api.view.ViewContext;
import org.labkey.study.view.StudyGWTView;
import org.springframework.web.servlet.ModelAndView;

import java.util.Map;

/**
 * Exposes code in study-src to classes in assay, to help with assay migration process
 */
public class AssayMigrationServiceImpl implements AssayMigrationService
{
    @Override
    public DomainImporterServiceBase getAssayImportService(ViewContext ctx)
    {
        return new AssayImportServiceImpl(ctx);
    }

    @Override
    public ModelAndView createAssayDesignerView(Map<String, String> properties)
    {
        return new StudyGWTView(new StudyApplication.AssayDesigner(), properties);
    }

    @Override
    public ModelAndView createAssayImportView(Map<String, String> properties)
    {
        return new StudyGWTView(new StudyApplication.AssayImporter(), properties);
    }
}
