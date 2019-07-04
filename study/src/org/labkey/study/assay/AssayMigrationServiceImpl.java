package org.labkey.study.assay;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.assay.AssayMigrationService;
import org.labkey.api.data.Container;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.property.DomainEditorServiceBase;
import org.labkey.api.exp.property.DomainImporterServiceBase;
import org.labkey.api.gwt.client.assay.model.GWTProtocol;
import org.labkey.api.gwt.server.BaseRemoteService;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.labkey.study.plate.PlateDataServiceImpl;
import org.labkey.study.view.StudyGWTView;

import java.util.Collection;
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
    public ExpProtocol findExpProtocol(GWTProtocol protocol, Container c)
    {
        return AssayManager.get().findExpProtocol(protocol, c);
    }

    @Override
    public AssayServiceImpl getGwtAssayService(ViewContext ctx)
    {
        return new AssayServiceImpl(ctx);
    }

    @Override
    public DomainEditorServiceBase getAssayDomainEditorService(ViewContext ctx)
    {
        return new AssayServiceImpl(ctx);
    }

    @Override
    public void verifyLegalName(AssayProvider provider)
    {
        AssayManager.get().verifyLegalName(provider);
    }

    @Override
    public @Nullable AssayProvider getProvider(String providerName, Collection<AssayProvider> providers)
    {
        return AssayManager.get().getProvider(providerName, providers);
    }

    @Override
    public DomainImporterServiceBase getAssayImportService(ViewContext ctx)
    {
        return new AssayImportServiceImpl(ctx);
    }
}
