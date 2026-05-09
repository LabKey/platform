package org.labkey.api.specimen;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.study.Study;
import org.labkey.api.study.importer.SimpleStudyImportContext;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.vfs.FileLike;

import java.util.Set;

// Temporary service that provides entry points to ease migration of code from study module to specimen module
// These should all go away once the migration is complete
public interface SpecimenMigrationService
{
    static @Nullable SpecimenMigrationService get()
    {
        return ServiceRegistry.get().getService(SpecimenMigrationService.class);
    }

    static void setInstance(SpecimenMigrationService impl)
    {
        ServiceRegistry.get().registerService(SpecimenMigrationService.class, impl);
    }

    String SPECIMENS_ARCHIVE_TYPE = "Specimens";

    ActionURL getBeginURL(Container c);
    ActionURL getSelectedSpecimensURL(Container c);
    ActionURL getSpecimensURL(Container c);

    void importSpecimenArchive(@Nullable FileLike inputFile, PipelineJob job, SimpleStudyImportContext ctx, boolean merge,
                               boolean syncParticipantVisit) throws PipelineJobException;

    void clearRequestCaches(Container c);

    @Nullable QueryUpdateService getSpecimenQueryUpdateService(Container c, TableInfo queryTable);

    QueryView getSpecimenQueryView(ViewContext context, QuerySettings settings);

    void setDefaultRequestabilityRules(Container container, User user);

    boolean isEnableRequests(Container c);

    void addSpecimenPivotTableNames(Set<String> names);

    /**
     * Returns a specimen pivot TableInfo, if that's what was requested
     */
    @Nullable TableInfo getSpecimenPivotTable(UserSchema schema, String name, Study study, ContainerFilter cf);
}
