/*
 * Copyright (c) 2021-2026 LabKey Corporation
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
package org.labkey.api.specimen;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.security.User;
import org.labkey.api.specimen.model.SpecimenTablesProvider;
import org.labkey.api.study.SpecimenTablesTemplate;
import org.labkey.api.study.StudyService;

import static org.labkey.api.specimen.model.SpecimenTablesProvider.SPECIMEN_COMMENTS_TABLE_NAME;

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
        return getSchema().getTable(SPECIMEN_COMMENTS_TABLE_NAME);
    }

    public TableInfo getTableInfoLocation(Container container)
    {
        return getTableInfoLocation(container, null);
    }

    public TableInfo getTableInfoLocation(Container container, User user)
    {
        SpecimenTablesProvider specimenTablesProvider = new SpecimenTablesProvider(container, user, SPECIMEN_TABLES_TEMPLATE);
        return specimenTablesProvider.createTableInfo(SpecimenTablesProvider.LOCATION_TABLE_NAME);
    }

    /*
     *  Provisioned tables: Specimen, Vial, SpecimenEvent
     */
    private static SpecimenTablesTemplate _specimenTablesTemplate = new DefaultSpecimenTablesTemplate();

    // TODO: Template gets set globally... for ALL threads! Switch to an instance-based or at least thread local approach
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
        return specimenTablesProvider.createTableInfo(SpecimenTablesProvider.VIAL_TABLE_NAME);
    }

    @Nullable
    public TableInfo getTableInfoVialIfExists(Container container)
    {
        SpecimenTablesProvider specimenTablesProvider = new SpecimenTablesProvider(container, null, _specimenTablesTemplate);
        return specimenTablesProvider.getTableInfoIfExists(SpecimenTablesProvider.VIAL_TABLE_NAME);
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
        return specimenTablesProvider.createTableInfo(SpecimenTablesProvider.SPECIMEN_TABLE_NAME);
    }

    @Nullable
    public TableInfo getTableInfoSpecimenIfExists(Container container)
    {
        SpecimenTablesProvider specimenTablesProvider = new SpecimenTablesProvider(container, null, _specimenTablesTemplate);
        return specimenTablesProvider.getTableInfoIfExists(SpecimenTablesProvider.SPECIMEN_TABLE_NAME);
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
        return specimenTablesProvider.createTableInfo(SpecimenTablesProvider.SPECIMEN_EVENT_TABLE_NAME);
    }

    @Nullable
    public TableInfo getTableInfoSpecimenEventIfExists(Container container)
    {
        SpecimenTablesProvider specimenTablesProvider = new SpecimenTablesProvider(container, null, _specimenTablesTemplate);
        return specimenTablesProvider.getTableInfoIfExists(SpecimenTablesProvider.SPECIMEN_EVENT_TABLE_NAME);
    }

    public TableInfo getTableInfoSpecimenDetail(Container container)
    {
        return getSchema().getTable("SpecimenDetail");
    }

    public TableInfo getTableInfoSpecimenPrimaryType(Container container)
    {
        return getTableInfoSpecimenPrimaryType(container, null);
    }

    public TableInfo getTableInfoSpecimenPrimaryType(Container container, User user)
    {
        SpecimenTablesProvider specimenTablesProvider = new SpecimenTablesProvider(container, user, _specimenTablesTemplate);
        return specimenTablesProvider.createTableInfo(SpecimenTablesProvider.PRIMARY_TYPE_TABLE_NAME);
    }

    public TableInfo getTableInfoSpecimenAdditive(Container container)
    {
        return getTableInfoSpecimenAdditive(container, null);
    }

    public TableInfo getTableInfoSpecimenAdditive(Container container, User user)
    {
        SpecimenTablesProvider specimenTablesProvider = new SpecimenTablesProvider(container, user, _specimenTablesTemplate);
        return specimenTablesProvider.createTableInfo(SpecimenTablesProvider.ADDITIVE_TYPE_TABLE_NAME);
    }

    public TableInfo getTableInfoSpecimenDerivative(Container container)
    {
        return getTableInfoSpecimenDerivative(container, null);
    }

    public TableInfo getTableInfoSpecimenDerivative(Container container, User user)
    {
        SpecimenTablesProvider specimenTablesProvider = new SpecimenTablesProvider(container, user, _specimenTablesTemplate);
        return specimenTablesProvider.createTableInfo(SpecimenTablesProvider.DERIVATIVE_TYPE_TABLE_NAME);
    }

    // The tables below are managed by study, not by specimen, but lots of specimen code interacts with them

    public TableInfo getTableInfoParticipantVisit()
    {
        return getSchema().getTable("ParticipantVisit");
    }

    public TableInfo getTableInfoParticipant()
    {
        return getSchema().getTable("Participant");
    }

    public TableInfo getTableInfoParticipantGroupMap()
    {
        return getSchema().getTable("ParticipantGroupMap");
    }

    public TableInfo getTableInfoVisit()
    {
        return getSchema().getTable("Visit");
    }
}
