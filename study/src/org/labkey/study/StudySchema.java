/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.security.User;
import org.labkey.study.model.DatasetDefinition;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;

import java.util.Collection;

public class StudySchema
{
    private static final StudySchema instance = new StudySchema();
    private static final String SCHEMA_NAME = "study";

    public static StudySchema getInstance()
    {
        return instance;
    }

    private StudySchema()
    {
    }

    public String getSchemaName()
    {
        return SCHEMA_NAME;
    }

    public DbSchema getSchema()
    {
        return DbSchema.get(SCHEMA_NAME, DbSchemaType.Module);
    }

    public DbScope getScope()
    {
        return getSchema().getScope();
    }

    public String getDatasetSchemaName()
    {
        return "studydataset";
    }

    public DbSchema getDatasetSchema()
    {
        return DbSchema.get(getDatasetSchemaName(), DbSchemaType.Provisioned);
    }

    public String getStudyDesignSchemaName()
    {
        return "studydesign";
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

    public TableInfo getTableInfoVisitAliases()
    {
        return getSchema().getTable("VisitAliases");
    }

    public TableInfo getTableInfoDataset()
    {
        return getSchema().getTable("DataSet");
    }

    public TableInfo getTableInfoVisitMap()
    {
        return getSchema().getTable("VisitMap");
    }

    public TableInfo getTableInfoVisitTagMap()
    {
        return getSchema().getTable("VisitTagMap");
    }

    public TableInfo getTableInfoStudyData(StudyImpl study, @Nullable User user)
    {
        return new StudyUnionTableInfo(study, StudyManager.getInstance().getDatasetDefinitions(study), user, study.isDataspaceStudy());
    }

    public TableInfo getTableInfoStudyDataFiltered(StudyImpl study, Collection<DatasetDefinition> defs, User user)
    {
        return new StudyUnionTableInfo(study, defs, user, study.isDataspaceStudy());
    }

    public TableInfo getTableInfoParticipant()
    {
        return getSchema().getTable("Participant");
    }

    public TableInfo getTableInfoParticipantVisit()
    {
        return getSchema().getTable("ParticipantVisit");
    }

    public TableInfo getTableInfoUploadLog()
    {
        return getSchema().getTable("UploadLog");
    }

    public TableInfo getTableInfoCohort()
    {
        return getSchema().getTable("Cohort");
    }

    public TableInfo getTableInfoParticipantView()
    {
        return getSchema().getTable("ParticipantView");
    }

    public TableInfo getTableInfoParticipantCategory()
    {
        return getSchema().getTable("ParticipantCategory");
    }

    public TableInfo getTableInfoParticipantGroup()
    {
        return getSchema().getTable("ParticipantGroup");
    }

    public TableInfo getTableInfoParticipantGroupMap()
    {
        return getSchema().getTable("ParticipantGroupMap");
    }

    public TableInfo getTableInfoStudySnapshot()
    {
        return getSchema().getTable("StudySnapshot");
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

    public TableInfo getTableInfoStudyDesignUnits()
    {
        return getSchema().getTable("StudyDesignUnits");
    }

    public TableInfo getTableInfoStudyDesignAssays()
    {
        return getSchema().getTable("StudyDesignAssays");
    }

    public TableInfo getTableInfoStudyDesignLabs()
    {
        return getSchema().getTable("StudyDesignLabs");
    }

    public TableInfo getTableInfoTreatmentVisitMap()
    {
        return getSchema().getTable("TreatmentVisitMap");
    }

    public TableInfo getTableInfoObjective()
    {
        return getSchema().getTable("Objective");
    }

    public TableInfo getTableInfoVisitTag()
    {
        return getSchema().getTable("VisitTag");
    }

    public TableInfo getTableInfoAssaySpecimen()
    {
        return getSchema().getTable("AssaySpecimen");
    }

    public TableInfo getTableInfoAssaySpecimenVisit()
    {
        return getSchema().getTable("AssaySpecimenVisit");
    }

    public TableInfo getTableInfoDoseAndRoute()
    {
        return getSchema().getTable("DoseAndRoute");
    }
}
