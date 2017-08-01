/*
 * Copyright (c) 2006-2017 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.security.User;
import org.labkey.api.study.SpecimenTablesTemplate;
import org.labkey.study.model.DatasetDefinition;
import org.labkey.study.model.DefaultSpecimenTablesTemplate;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.query.SpecimenTablesProvider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * User: brittp
 * Date: Jan 6, 2006
 * Time: 10:36:43 AM
 */
public class StudySchema
{
    private static final StudySchema instance = new StudySchema();
    private static final String SCHEMA_NAME = "study";

    private static SpecimenTablesTemplate _specimenTablesTemplate = new DefaultSpecimenTablesTemplate();

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

    public TableInfo getTableInfoSite(Container container)
    {
        return getTableInfoSite(container, null);
    }

    public TableInfo getTableInfoSite(Container container, User user)
    {
//        return getSchema().getTable("Site");
        SpecimenTablesProvider specimenTablesProvider = new SpecimenTablesProvider(container, user, _specimenTablesTemplate);
        return specimenTablesProvider.createTableInfo(SpecimenTablesProvider.LOCATION_TABLENAME);
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

    public TableInfo getTableInfoStudyDataVisible(StudyImpl study, @Nullable User user)
    {
        List<DatasetDefinition> defsAll = study.getDatasets();
        List<DatasetDefinition> defsVisible = new ArrayList<>(defsAll.size());
        for (DatasetDefinition def : defsAll)
        {
            if (!def.isShowByDefault())
                continue;
            if (null != user && !def.canRead(user))
                continue;
            defsVisible.add(def);
        }
        return new StudyUnionTableInfo(study, defsVisible, user, study.isDataspaceStudy());
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

    /*
     *  Provisioned tables: Specimen, Vial, SpecimenEvent
     */
    @NotNull
    public TableInfo getTableInfoVial(Container container)
    {
        return getTableInfoVial(container, null);
    }

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
    public TableInfo getTableInfoVial(Container container, User user)
    {
        SpecimenTablesProvider specimenTablesProvider = new SpecimenTablesProvider(container, user, _specimenTablesTemplate);
        return specimenTablesProvider.createTableInfo(SpecimenTablesProvider.VIAL_TABLENAME);
    }

    @Nullable
    public TableInfo getTableInfoVialIfExists(Container container)
    {
        SpecimenTablesProvider specimenTablesProvider = new SpecimenTablesProvider(container, null, _specimenTablesTemplate);
        return specimenTablesProvider.getTableInfoIfExists(SpecimenTablesProvider.VIAL_TABLENAME);
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

    @Nullable
    public TableInfo getTableInfoSpecimenIfExists(Container container)
    {
        SpecimenTablesProvider specimenTablesProvider = new SpecimenTablesProvider(container, null, _specimenTablesTemplate);
        return specimenTablesProvider.getTableInfoIfExists(SpecimenTablesProvider.SPECIMEN_TABLENAME);
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

    @Nullable
    public TableInfo getTableInfoSpecimenEventIfExists(Container container)
    {
        SpecimenTablesProvider specimenTablesProvider = new SpecimenTablesProvider(container, null, _specimenTablesTemplate);
        return specimenTablesProvider.getTableInfoIfExists(SpecimenTablesProvider.SPECIMENEVENT_TABLENAME);
    }

    public TableInfo getTableInfoSpecimenDetail(Container container)
    {
        return getSchema().getTable("SpecimenDetail");
    }

    public TableInfo getTableInfoSpecimenSummary()
    {
        return getSchema().getTable("SpecimenSummary");
    }

    public TableInfo getTableInfoSpecimenPrimaryType(Container container)
    {
        return getTableInfoSpecimenPrimaryType(container, null);
    }

    public TableInfo getTableInfoSpecimenPrimaryType(Container container, User user)
    {
//        return getSchema().getTable("SpecimenPrimaryType");
        SpecimenTablesProvider specimenTablesProvider = new SpecimenTablesProvider(container, user, _specimenTablesTemplate);
        return specimenTablesProvider.createTableInfo(SpecimenTablesProvider.PRIMARYTYPE_TABLENAME);
    }

    public TableInfo getTableInfoSpecimenAdditive(Container container)
    {
        return getTableInfoSpecimenAdditive(container, null);
    }

    public TableInfo getTableInfoSpecimenAdditive(Container container, User user)
    {
//        return getSchema().getTable("SpecimenAdditive");
        SpecimenTablesProvider specimenTablesProvider = new SpecimenTablesProvider(container, user, _specimenTablesTemplate);
        return specimenTablesProvider.createTableInfo(SpecimenTablesProvider.ADDITIVETYPE_TABLENAME);
    }

    public TableInfo getTableInfoSpecimenDerivative(Container container)
    {
        return getTableInfoSpecimenDerivative(container, null);
    }

    public TableInfo getTableInfoSpecimenDerivative(Container container, User user)
    {
//        return getSchema().getTable("SpecimenDerivative");
        SpecimenTablesProvider specimenTablesProvider = new SpecimenTablesProvider(container, user, _specimenTablesTemplate);
        return specimenTablesProvider.createTableInfo(SpecimenTablesProvider.DERIVATIVETYPE_TABLENAME);
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
        return CoreSchema.getInstance().getTableInfoQCState();
    }

    public TableInfo getTableInfoParticipantView()
    {
        return getSchema().getTable("ParticipantView");
    }

    public TableInfo getTableInfoSpecimenComment()
    {
        return getSchema().getTable("SpecimenComment");
    }

    public TableInfo getTableInfoSpecimenVialCount()
    {
        return getSchema().getTable("VialCounts");
    }

    public TableInfo getTableInfoSampleAvailabilityRule()
    {
        return getSchema().getTable("SampleAvailabilityRule");
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
