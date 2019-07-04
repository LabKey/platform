package org.labkey.api.assay;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.property.DomainEditorServiceBase;
import org.labkey.api.exp.property.DomainImporterServiceBase;
import org.labkey.api.gwt.client.assay.AssayService;
import org.labkey.api.gwt.client.assay.model.GWTProtocol;
import org.labkey.api.gwt.server.BaseRemoteService;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;

import java.util.Collection;
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

    ExpProtocol findExpProtocol(GWTProtocol protocol, Container c);

    AssayService getGwtAssayService(ViewContext ctx);

    DomainEditorServiceBase getAssayDomainEditorService(ViewContext ctx);

    void verifyLegalName(AssayProvider provider);

    @Nullable AssayProvider getProvider(String providerName, Collection<AssayProvider> providers);

    DomainImporterServiceBase getAssayImportService(ViewContext ctx);
}
