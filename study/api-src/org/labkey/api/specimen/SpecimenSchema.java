package org.labkey.api.specimen;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.security.User;
import org.labkey.api.specimen.model.SpecimenTablesProvider;
import org.labkey.api.study.SpecimenTablesTemplate;
import org.labkey.api.study.StudyService;

public class SpecimenSchema
{
    private static final SpecimenSchema INSTANCE = new SpecimenSchema();
    private static final SpecimenTablesTemplate SPECIMEN_TABLES_TEMPLATE = new DefaultSpecimenTablesTemplate();

    private final DbSchema _studySchema = StudyService.get().getStudySchema();

    private SpecimenSchema()
    {
    }

    public static SpecimenSchema get()
    {
        return INSTANCE;
    }

    public DbSchema getSchema()
    {
        return _studySchema;
    }

    public DbScope getScope()
    {
        return getSchema().getScope();
    }

    public SqlDialect getSqlDialect()
    {
        return getSchema().getSqlDialect();
    }

    public TableInfo getTableInfoSampleRequest()
    {
        return getSchema().getTable("SampleRequest");
    }

    public TableInfo getTableInfoSampleRequestEvent()
    {
        return getSchema().getTable("SampleRequestEvent");
    }

    public TableInfo getTableInfoSampleRequestRequirement()
    {
        return getSchema().getTable("SampleRequestRequirement");
    }

    public TableInfo getTableInfoSampleRequestActor()
    {
        return getSchema().getTable("SampleRequestActor");
    }

    public TableInfo getTableInfoSampleRequestStatus()
    {
        return getSchema().getTable("SampleRequestStatus");
    }

    public TableInfo getTableInfoSampleRequestSpecimen()
    {
        return getSchema().getTable("SampleRequestSpecimen");
    }

    public TableInfo getTableInfoSampleAvailabilityRule()
    {
        return getSchema().getTable("SampleAvailabilityRule");
    }

    public TableInfo getTableInfoSpecimenComment()
    {
        return getSchema().getTable("SpecimenComment");
    }

    public TableInfo getTableInfoLocation(Container container)
    {
        return getTableInfoLocation(container, null);
    }

    public TableInfo getTableInfoLocation(Container container, User user)
    {
        SpecimenTablesProvider specimenTablesProvider = new SpecimenTablesProvider(container, user, SPECIMEN_TABLES_TEMPLATE);
        return specimenTablesProvider.createTableInfo(SpecimenTablesProvider.LOCATION_TABLENAME);
    }

    /*
     *  Provisioned tables: Specimen, Vial, SpecimenEvent
     */
    private static SpecimenTablesTemplate _specimenTablesTemplate = new DefaultSpecimenTablesTemplate();

    public SpecimenTablesTemplate setSpecimenTablesTemplates(SpecimenTablesTemplate template)
    {
        if (template != null)
        {
            SpecimenTablesTemplate prevTemplate = _specimenTablesTemplate;
            _specimenTablesTemplate = template;

            return prevTemplate;
        }
        return null;
    }

    @NotNull
    public TableInfo getTableInfoVial(Container container)
    {
        return getTableInfoVial(container, null);
    }

    @NotNull
    public TableInfo getTableInfoVial(Container container, User user)
    {
        SpecimenTablesProvider specimenTablesProvider = new SpecimenTablesProvider(container, user, _specimenTablesTemplate);
        return specimenTablesProvider.createTableInfo(SpecimenTablesProvider.VIAL_TABLENAME);
    }

    @NotNull
    public TableInfo getTableInfoSpecimen(Container container)
    {
        return getTableInfoSpecimen(container, null);
    }

    @NotNull
    public TableInfo getTableInfoSpecimen(Container container, User user)
    {
        SpecimenTablesProvider specimenTablesProvider = new SpecimenTablesProvider(container, user, _specimenTablesTemplate);
        return specimenTablesProvider.createTableInfo(SpecimenTablesProvider.SPECIMEN_TABLENAME);
    }

    @NotNull
    public TableInfo getTableInfoSpecimenEvent(Container container)
    {
        return getTableInfoSpecimenEvent(container, null);
    }

    @NotNull
    public TableInfo getTableInfoSpecimenEvent(Container container, User user)
    {
        SpecimenTablesProvider specimenTablesProvider = new SpecimenTablesProvider(container, user, _specimenTablesTemplate);
        return specimenTablesProvider.createTableInfo(SpecimenTablesProvider.SPECIMENEVENT_TABLENAME);
    }
}
