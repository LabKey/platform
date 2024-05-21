package org.labkey.api.specimen;

import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.study.TimepointType;
import org.labkey.api.study.importer.SimpleStudyImportContext;
import org.labkey.api.view.ActionURL;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

// Temporary service that provides entry points to ease migration of code from study module to specimen module
// These should all go away once the migration is complete
public interface SpecimenMigrationService
{
    // These constants were copied from SimpleSpecimenImporter, allowing that class to move into specimen
    String VIAL_ID = "global_unique_specimen_id";
    String SAMPLE_ID = "specimen_number";
    String DRAW_TIMESTAMP = "draw_timestamp";
    String VISIT = "visit_value";
    String DERIVATIVE_TYPE = "derivative_type";
    String PARTICIPANT_ID = "ptid";

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
    void updateVialCounts(Container container, User user);

    @Nullable QueryUpdateService getSpecimenQueryUpdateService(Container c, TableInfo queryTable);

    void importSpecimens(Container container, User user, List<Map<String, Object>> specimens) throws ValidationException, IOException;
    void exportSpecimens(Container container, User user, List<Map<String, Object>> specimens, TimepointType timepointType, String participantIdLabel, HttpServletResponse response);
    Map<String, String> getColumnLabelMap(Container container, User user);
    void fixupSpecimenColumns(Container container, User user, TabLoader loader) throws IOException;
}
