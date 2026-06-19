package org.labkey.studydesign;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.SpringModule;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.studydesign.StudyDesignService;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.WebPartFactory;
import org.labkey.studydesign.model.TreatmentManager;
import org.labkey.studydesign.view.AssayScheduleWebpartFactory;
import org.labkey.studydesign.view.ImmunizationScheduleWebpartFactory;
import org.labkey.studydesign.view.VaccineDesignWebpartFactory;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public class StudyDesignModule extends SpringModule
{
    private static final Logger LOG = LogHelper.getLogger(StudyDesignModule.class, "Study Design");
    public static final String NAME = "StudyDesign";

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    @NotNull
    protected Collection<WebPartFactory> createWebPartFactories()
    {
        return List.of(
            new AssayScheduleWebpartFactory(),
            new ImmunizationScheduleWebpartFactory(),
            new VaccineDesignWebpartFactory()
        );
    }

    @Override
    public @Nullable Double getSchemaVersion()
    {
        return null;
    }

    @Override
    public boolean hasScripts()
    {
        return false;
    }

    @Override
    protected void init()
    {
        addController("study-design", StudyDesignController.class);

        ServiceRegistry.get().registerService(StudyDesignService.class, new StudyDesignServiceImpl());
    }

    @Override
    protected void startupAfterSpringConfig(ModuleContext moduleContext)
    {
    }

    @Override
    public @NotNull Collection<String> getSchemaNames()
    {
        return Set.of();
    }

    @Override
    public @NotNull Set<Class<?>> getIntegrationTests()
    {
        return Set.of(
            TreatmentManager.TreatmentDataTestCase.class,
            TreatmentManager.AssayScheduleTestCase.class,
            StudyDesignController.ContainerScopingTestCase.class
        );
    }
}