package org.labkey.study;

import org.labkey.api.data.DbSchema;
import org.labkey.api.data.SqlDialect;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.study.model.StudyManager;

/**
 * User: brittp
 * Date: Jan 6, 2006
 * Time: 10:36:43 AM
 */
public class StudySchema
{
    private static StudySchema instance = null;

    public static StudySchema getInstance()
    {
        if (null == instance)
            instance = new StudySchema();

        return instance;
    }

    private StudySchema()
    {
    }

    public DbSchema getSchema()
    {
        return StudyManager.getSchema();
    }


    /** NOTE we depend on study and exp being in the same scope **/
    public DbSchema getExpSchema()
    {
        return ExperimentService.get().getSchema();
    }


    public SqlDialect getSqlDialect()
    {
        return getSchema().getSqlDialect();
    }

    public TableInfo getTableInfoStudy()
    {
        return getSchema().getTable("Study");
    }

    public TableInfo getTableInfoVisit()
    {
        return getSchema().getTable("Visit");
    }

    public TableInfo getTableInfoDataSet()
    {
        return getSchema().getTable("DataSet");
    }

    public TableInfo getTableInfoSite()
    {
        return getSchema().getTable("Site");
    }

    public TableInfo getTableInfoVisitMap()
    {
        return getSchema().getTable("VisitMap");
    }

    public TableInfo getTableInfoStudyData()
    {
        //assert getExpSchema().getScope() == getSchema().getScope();
        return getSchema().getTable("StudyData");
    }

    public TableInfo getTableInfoParticipant()
    {
        return getSchema().getTable("Participant");
    }

    public TableInfo getTableInfoParticipantVisit()
    {
        return getSchema().getTable("ParticipantVisit");
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

    public TableInfo getTableInfoSpecimen()
    {
        return getSchema().getTable("Specimen");
    }

    public TableInfo getTableInfoSpecimenEvent()
    {
        return getSchema().getTable("SpecimenEvent");
    }

    public TableInfo getTableInfoSpecimenDetail()
    {
        return getSchema().getTable("SpecimenDetail");
    }

    public TableInfo getTableInfoSpecimenSummary()
    {
        return getSchema().getTable("SpecimenSummary");
    }

    public TableInfo getTableInfoSpecimenPrimaryType()
    {
        return getSchema().getTable("SpecimenPrimaryType");
    }
    public TableInfo getTableInfoSpecimenAdditive()
    {
        return getSchema().getTable("SpecimenAdditive");
    }
    public TableInfo getTableInfoSpecimenDerivative()
    {
        return getSchema().getTable("SpecimenDerivative");
    }

    public TableInfo getTableInfoUploadLog()
    {
        return getSchema().getTable("UploadLog");
    }

    public TableInfo getTableInfoPlate()
    {
        return getSchema().getTable("Plate");
    }

    public TableInfo getTableInfoWellGroup()
    {
        return getSchema().getTable("WellGroup");
    }

    public TableInfo getTableInfoWell()
    {
        return getSchema().getTable("Well");
    }

    public TableInfo getTableInfoCohort()
    {
        return getSchema().getTable("Cohort");
    }
}
