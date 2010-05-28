/*
 * Copyright (c) 2006-2010 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

    public DbSchema getDatasetSchema()
    {
        return DbSchema.get("studydataset");
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

    public TableInfo getTableInfoVial()
    {
        return getSchema().getTable("Vial");
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

    public TableInfo getTableInfoQCState()
    {
        return getSchema().getTable("QCState");
    }

    public TableInfo getTableInfoParticipantView()
    {
        return getSchema().getTable("ParticipantView");
    }

    public TableInfo getTableInfoSpecimenComment()
    {
        return getSchema().getTable("SpecimenComment");
    }

    public TableInfo getTableInfoPrimaryType()
    {
        return getSchema().getTable("SpecimenPrimaryType");
    }

    public TableInfo getTableInfoDerivativeType()
    {
        return getSchema().getTable("SpecimenDerivative");
    }

    public TableInfo getTableInfoAdditiveType()
    {
        return getSchema().getTable("SpecimenAdditive");
    }

    public TableInfo getTableInfoSpecimenVialCount()
    {
        return getSchema().getTable("VialCounts");
    }

    public TableInfo getTableInfoSampleAvailabilityRule()
    {
        return getSchema().getTable("SampleAvailabilityRule");
    }
}
