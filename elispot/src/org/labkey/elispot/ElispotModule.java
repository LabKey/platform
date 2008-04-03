package org.labkey.elispot;

import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.security.User;
import org.labkey.api.study.PlateService;
import org.labkey.api.study.assay.AssayService;
import org.labkey.elispot.plate.ElispotPlateReaderService;
import org.labkey.elispot.plate.ExcelPlateReader;
import org.labkey.elispot.plate.TextPlateReader;

import java.beans.PropertyChangeEvent;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

public class ElispotModule extends DefaultModule implements ContainerManager.ContainerListener
{
    public static final String NAME = "Elispot";

    public ElispotModule()
    {
        super(NAME, 0.01, null, false);
        addController("elispot", ElispotController.class);
    }

    public void containerCreated(Container c)
    {
    }

    public void containerDeleted(Container c, User user)
    {
    }

    public Collection<String> getSummary(Container c)
    {
        return Collections.emptyList();
    }

    public void propertyChange(PropertyChangeEvent evt)
    {
    }

    public void startup(ModuleContext moduleContext)
    {
        super.startup(moduleContext);
        // add a container listener so we'll know when our container is deleted:
        ContainerManager.addContainerListener(this);

        PlateService.get().registerPlateTypeHandler(new ElispotPlateTypeHandler());
        ExperimentService.get().registerExperimentDataHandler(new ElispotDataHandler());
        AssayService.get().registerAssayProvider(new ElispotAssayProvider());

        ElispotPlateReaderService.registerProvider(new ExcelPlateReader());
        ElispotPlateReaderService.registerProvider(new TextPlateReader());
    }

    public Set<String> getModuleDependencies()
    {
        return Collections.singleton("Experiment");
    }
}