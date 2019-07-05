package org.labkey.api.assay;

import org.labkey.api.exp.property.DomainImporterServiceBase;
import org.labkey.api.gwt.server.BaseRemoteService;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.springframework.web.servlet.ModelAndView;

import java.util.Map;

@AssayMigration
public interface AssayMigrationService
{
    static AssayMigrationService get()
    {
        return ServiceRegistry.get().getService(AssayMigrationService.class);
    }

    static void setInstance(AssayMigrationService impl)
    {
        ServiceRegistry.get().registerService(AssayMigrationService.class, impl);
    }

    BaseRemoteService getPlateDataServiceImpl(ViewContext ctx);

    HttpView getStudyGWTView(Map<String, String> properties);

    DomainImporterServiceBase getAssayImportService(ViewContext ctx);

    ModelAndView createAssayDesignerView(Map<String, String> properties);

    ModelAndView createAssayImportView(Map<String, String> properties);
}
