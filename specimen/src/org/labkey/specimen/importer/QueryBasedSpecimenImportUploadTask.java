package org.labkey.specimen.importer;

import org.apache.logging.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.study.SpecimenService;
import org.labkey.api.study.SpecimenTransform;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.SystemMaintenance;
import org.labkey.specimen.SpecimenModule;

import java.util.stream.Stream;

public class QueryBasedSpecimenImportUploadTask implements SystemMaintenance.MaintenanceTask
{
    @Override
    public String getDescription()
    {
        return "Query-Based Specimen Import Upload Task";
    }

    @Override
    public String getName()
    {
        return "QueryBasedSpecimenUpload";
    }

    @Override
    public void run(Logger log)
    {
        String QBSpecimenImportKey = QueryBasedSpecimenTransform.PROPERTY_MAP_KEY;
        try (Stream<Container> stream = PropertyManager.getNormalStore().streamMatchingContainers(PropertyManager.SHARED_USER, QBSpecimenImportKey))
        {
            stream.forEach(c -> {
                try
                {
                    // Study must have been deleted
                    if (null == StudyService.get().getStudy(c))
                    {
                        log.error("Query-based specimen import failed: Study does not exist in folder " + c.getPath());
                        return;
                    }

                    if (!c.getActiveModules().contains(ModuleLoader.getInstance().getModule(SpecimenModule.class)))
                        return;

                    SpecimenTransform transform = SpecimenService.get().getSpecimenTransform(QueryBasedSpecimenTransform.NAME);
                    PropertyManager.PropertyMap props = PropertyManager.getProperties(c, QBSpecimenImportKey);
                    boolean enabled = ("on").equals(props.get("enabled"));

                    if (!enabled)
                    {
                        log.info(String.format("Prohibiting queuing specimen import for %s. Query-based specimen import is not enabled.", c.getName()));
                        return;
                    }
                    if (!transform.isActive(c))
                    {
                        log.info(String.format("Prohibiting queuing specimen import for %s. Query-based specimen import is not the active import mechanism.", c.getName()));
                        return;
                    }
                    log.info("Queuing specimen import for " + c.getName());

                    int userId = Integer.parseInt(props.get("userId"));
                    User reloadUser = UserManager.getUser(userId);
                    PipelineJob job = SpecimenService.get().createSpecimenReloadJob(c, reloadUser, transform, null);

                    PipelineService.get().queueJob(job);
                }
                catch (Exception e)
                {
                    log.error("Query-based specimen import failed", e);
                }
            });
        }
    }
}
