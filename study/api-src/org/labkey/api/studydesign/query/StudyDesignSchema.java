package org.labkey.api.studydesign.query;

import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.TableInfo;

public class StudyDesignSchema
{
    private static final StudyDesignSchema instance = new StudyDesignSchema();
    public static final String STORAGE_SCHEMA_NAME = "studydesign";
    public static final String STUDY_SCHEMA_NAME = "study";

    public static StudyDesignSchema getInstance()
    {
        return instance;
    }

    private StudyDesignSchema()
    {
    }

    public String getSchemaName()
    {
        return STUDY_SCHEMA_NAME;
    }

    public String getStorageSchemaName()
    {
        return STORAGE_SCHEMA_NAME;
    }

    public DbSchema getSchema()
    {
        return DbSchema.get(STUDY_SCHEMA_NAME, DbSchemaType.Module);
    }

    public DbScope getScope()
    {
        return getSchema().getScope();
    }

    public TableInfo getTableInfoStudyDesignImmunogenTypes()
    {
        return getSchema().getTable("StudyDesignImmunogenTypes");
    }

    public TableInfo getTableInfoStudyDesignChallengeTypes()
    {
        return getSchema().getTable("StudyDesignChallengeTypes");
    }

    public TableInfo getTableInfoStudyDesignGenes()
    {
        return getSchema().getTable("StudyDesignGenes");
    }

    public TableInfo getTableInfoStudyDesignRoutes()
    {
        return getSchema().getTable("StudyDesignRoutes");
    }

    public TableInfo getTableInfoStudyDesignSubTypes()
    {
        return getSchema().getTable("StudyDesignSubTypes");
    }

    public TableInfo getTableInfoStudyDesignSampleTypes()
    {
        return getSchema().getTable("StudyDesignSampleTypes");
    }

    public TableInfo getTableInfoStudyDesignAssays()
    {
        return getSchema().getTable("StudyDesignAssays");
    }

    public TableInfo getTableInfoStudyDesignUnits()
    {
        return getSchema().getTable("StudyDesignUnits");
    }

    public TableInfo getTableInfoStudyDesignLabs()
    {
        return getSchema().getTable("StudyDesignLabs");
    }

    public TableInfo getTableInfoDoseAndRoute()
    {
        return getSchema().getTable("DoseAndRoute");
    }

    public TableInfo getTableInfoTreatmentVisitMap()
    {
        return getSchema().getTable("TreatmentVisitMap");
    }

    public TableInfo getTableInfoObjective()
    {
        return getSchema().getTable("Objective");
    }

    public TableInfo getTableInfoAssaySpecimen()
    {
        return getSchema().getTable("AssaySpecimen");
    }

    public TableInfo getTableInfoAssaySpecimenVisit()
    {
        return getSchema().getTable("AssaySpecimenVisit");
    }
}
