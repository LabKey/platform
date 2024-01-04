package org.labkey.api.specimen;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.study.importer.SimpleStudyImportContext;
import org.labkey.api.view.ActionURL;

import java.nio.file.Path;

// Temporary service that provides entry points to ease migration of code from study module to specimen module
// These should all go away once the migration is complete
public interface SpecimenMigrationService
{
    static SpecimenMigrationService get()
    {
        return ServiceRegistry.get().getService(SpecimenMigrationService.class);
    }

    static void setInstance(SpecimenMigrationService impl)
    {
        ServiceRegistry.get().registerService(SpecimenMigrationService.class, impl);
    }

    String SPECIMENS_ARCHIVE_TYPE = "Specimens";

    ActionURL getBeginURL(Container c);
    ActionURL getInsertSpecimenQueryRowURL(Container c, String schemaName, TableInfo table);
    ActionURL getSelectedSpecimensURL(Container c);
    ActionURL getSpecimenEventsURL(Container c, ActionURL returnUrl);
    ActionURL getSpecimensURL(Container c);
    ActionURL getUpdateSpecimenQueryRowURL(Container c, String schemaName, TableInfo table);

    void importSpecimenArchive(@Nullable Path inputFile, PipelineJob job, SimpleStudyImportContext ctx, boolean merge,
                               boolean syncParticipantVisit) throws PipelineJobException, ValidationException;

    void clearRequestCaches(Container c);
    void clearGroupedValuesForColumn(Container container);
    void updateVialCounts(Container container, User user);
    void purgeRequestRequirementsAndActors(Container container);
}
