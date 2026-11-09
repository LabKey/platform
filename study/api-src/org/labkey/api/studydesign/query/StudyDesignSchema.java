/*
 * Copyright (c) 2025-2026 LabKey Corporation
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
