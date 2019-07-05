package org.labkey.study.assay;

import gwt.client.org.labkey.study.StudyApplication;
import org.labkey.api.assay.AssayMigrationService;
import org.labkey.api.exp.property.DomainImporterServiceBase;
import org.labkey.api.gwt.server.BaseRemoteService;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.labkey.study.plate.PlateDataServiceImpl;
import org.labkey.study.view.StudyGWTView;
import org.springframework.web.servlet.ModelAndView;

import java.util.Map;

/**
 * Exposes code in study-src to classes in assay, to help with assay migration process
 */
public class AssayMigrationServiceImpl implements AssayMigrationService
{
    @Override
    public BaseRemoteService getPlateDataServiceImpl(ViewContext ctx)
    {
        return new PlateDataServiceImpl(ctx);
    }

    @Override
    public HttpView getStudyGWTView(Map<String, String> properties)
    {
        return new StudyGWTView(gwt.client.org.labkey.plate.designer.client.TemplateDesigner.class, properties);
    }

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
