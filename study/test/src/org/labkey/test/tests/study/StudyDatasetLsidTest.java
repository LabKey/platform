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
package org.labkey.test.tests.study;

import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.remoteapi.CommandException;
import org.labkey.remoteapi.Connection;
import org.labkey.remoteapi.query.InsertRowsCommand;
import org.labkey.remoteapi.query.RowsResponse;
import org.labkey.remoteapi.query.SelectRowsCommand;
import org.labkey.remoteapi.query.SelectRowsResponse;
import org.labkey.remoteapi.query.UpdateRowsCommand;
import org.labkey.test.categories.Daily;
import org.labkey.test.params.FieldDefinition;
import org.labkey.test.params.study.DatasetDefinition;
import org.labkey.test.tests.MissingValueIndicatorsTest;
import org.labkey.test.util.StudyHelper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@Category({Daily.class})
public class StudyDatasetLsidTest extends MissingValueIndicatorsTest
{
    private static final String VISIT_STUDY = "Visit based study";
    private static final String DATE_STUDY = "Date based study";

    // datasets
    private static final String DEMOGRAPHICS_DATASET = "DemographicsDataset";
    private static final String NON_DEMOGRAPHICS_DATASET = "NonDemographicsDataset";
    private static final String ADDITIONAL_KEY_FIELD = "AdditionalKey";
    private static final String MANAGED_KEY_FIELD = "ManagedKey";
    private static final String TIME_PORTION_OF_DATE = "TimePortionOfDate";
    private static final String MV_INDICATOR_DATASET = "MvIndicatorDataset";

    @BeforeClass
    public static void setupProject() throws Exception
    {
        StudyDatasetLsidTest initTest = getCurrentTest();
        initTest.doSetup();
    }

    private void doSetup()
    {
        _containerHelper.createProject(getProjectName());
        setupMVIndicators();

        log("Creating date and visit studies");
        _containerHelper.createSubfolder(getProjectName(), VISIT_STUDY, "Study");
        _studyHelper.startCreateStudy()
                .setTimepointType(StudyHelper.TimepointType.VISIT)
                .createStudy();

        _containerHelper.createSubfolder(getProjectName(), DATE_STUDY, "Study");
        _studyHelper.startCreateStudy()
                .setTimepointType(StudyHelper.TimepointType.DATE)
                .createStudy();
    }

    @Override
    protected @Nullable String getProjectName()
    {
        return "Study Dataset LSID Test";
    }

    @Override
    public List<String> getAssociatedModules()
    {
        return Collections.emptyList();
    }

    @Test
    public void testVisitBasedStudy() throws Exception
    {
        log("Creating visit study datasets");

        final String containerPath = String.format("%s/%s", getProjectName(), VISIT_STUDY);
        Connection conn = createDefaultConnection();

        DatasetDefinition.create(DEMOGRAPHICS_DATASET)
                .setKindName(DatasetDefinition.VISIT_BASED_STUDY)
                .setFields(List.of(
                        new FieldDefinition("Name", FieldDefinition.ColumnType.String),
                        new FieldDefinition("IntField", FieldDefinition.ColumnType.Integer)
                ))
                .setDemographics(true)
                .create(conn, containerPath);

        DatasetDefinition.create(NON_DEMOGRAPHICS_DATASET)
                .setKindName(DatasetDefinition.VISIT_BASED_STUDY)
                .setFields(List.of(
                        new FieldDefinition("StringField", FieldDefinition.ColumnType.String),
                        new FieldDefinition("IntField", FieldDefinition.ColumnType.Integer)
                ))
                .create(conn, containerPath);

        DatasetDefinition.create(ADDITIONAL_KEY_FIELD)
                .setKindName(DatasetDefinition.VISIT_BASED_STUDY)
                .setFields(List.of(
                        new FieldDefinition("StringField", FieldDefinition.ColumnType.String),
                        new FieldDefinition("IntField", FieldDefinition.ColumnType.Integer),
                        new FieldDefinition("AdditionalField", FieldDefinition.ColumnType.Integer)
                ))
                .setKeyPropertyName("AdditionalField")
                .create(conn, containerPath);

        DatasetDefinition.create(MANAGED_KEY_FIELD)
                .setKindName(DatasetDefinition.VISIT_BASED_STUDY)
                .setFields(List.of(
                        new FieldDefinition("StringField", FieldDefinition.ColumnType.String),
                        new FieldDefinition("ManagedField", FieldDefinition.ColumnType.Integer),
                        new FieldDefinition("IntField", FieldDefinition.ColumnType.Integer)
                ))
                .setKeyPropertyName("ManagedField", true)
                .create(conn, containerPath);

        log("Validate inserting rows into the demographics dataset");
        expectSuccess(conn, DEMOGRAPHICS_DATASET, containerPath,
                List.of(
                        Map.of("Ptid", "111", "Name", "Bob", "IntField", 20),
                        Map.of("Ptid", "222", "Name", "Mark", "IntField", 40),
                        Map.of("Ptid", "333", "Name", "Ave", "IntField", 60)
                ));
        expectFail(conn, DEMOGRAPHICS_DATASET, containerPath,
                List.of(Map.of("Ptid", "111", "Name", "DifferentName", "IntField", 2)));
        expectSuccess(conn, DEMOGRAPHICS_DATASET, containerPath,
                List.of(Map.of("Ptid", "444", "Name", "Bob", "IntField", 20)));

        log("Validate inserting into the non-demographics dataset");
        expectSuccess(conn, NON_DEMOGRAPHICS_DATASET, containerPath,
                List.of(
                        Map.of("Ptid", "111", "Visit", 1, "IntField", 1),
                        Map.of("Ptid", "222", "Visit", 2, "IntField", 1),
                        Map.of("Ptid", "333", "Visit", 3, "IntField", 1)
                ));
        expectFail(conn, NON_DEMOGRAPHICS_DATASET, containerPath,
                List.of(Map.of("Ptid", "111", "Visit", 1, "IntField", 1)));
        expectSuccess(conn, NON_DEMOGRAPHICS_DATASET, containerPath,
                List.of(Map.of("Ptid", "111", "Visit", 2, "IntField", 1)));

        log("Validate inserting into a dataset with a third key field");
        expectSuccess(conn, ADDITIONAL_KEY_FIELD, containerPath,
                List.of(
                        Map.of("Ptid", "111", "Visit", 1, "AdditionalField", 10),
                        Map.of("Ptid", "111", "Visit", 1, "AdditionalField", 20),
                        Map.of("Ptid", "111", "Visit", 1)
                ));
        expectFail(conn, ADDITIONAL_KEY_FIELD, containerPath,
                List.of(Map.of("Ptid", "111", "Visit", 1, "AdditionalField", 10)));
        expectFail(conn, ADDITIONAL_KEY_FIELD, containerPath,
                List.of(Map.of("Ptid", "111", "Visit", 1)));
        expectSuccess(conn, ADDITIONAL_KEY_FIELD, containerPath,
                List.of(Map.of("Ptid", "111", "Visit", 1, "AdditionalField", 40)));

        log("Validate inserting into a dataset with a managed key field");
        expectSuccess(conn, MANAGED_KEY_FIELD, containerPath,
                List.of(
                        Map.of("Ptid", "111", "Visit", 1, "IntField", 1),
                        Map.of("Ptid", "111", "Visit", 1, "IntField", 1),
                        Map.of("Ptid", "111", "Visit", 1, "IntField", 1)
                ));
        expectSuccess(conn, ADDITIONAL_KEY_FIELD, containerPath,
                List.of(Map.of("Ptid", "222", "Visit", 1, "IntField", 1)));

        log("Validate participant/visit generation");
        validateParticipantVisits(conn, containerPath, 9);

        // test that generation of special columns is as expected in the update case
        validateUpdateOfGeneratedColumns(conn, containerPath, DEMOGRAPHICS_DATASET);
        validateUpdateOfGeneratedColumns(conn, containerPath, NON_DEMOGRAPHICS_DATASET);
        validateUpdateOfGeneratedColumns(conn, containerPath, ADDITIONAL_KEY_FIELD);
        validateUpdateOfGeneratedColumns(conn, containerPath, MANAGED_KEY_FIELD);
    }

    @Test
    public void testMVIndicator() throws Exception
    {
        log("Testing inserting and updating MV indicator fields");

        final String containerPath = String.format("%s/%s", getProjectName(), VISIT_STUDY);
        Connection conn = createDefaultConnection();

        DatasetDefinition.create(MV_INDICATOR_DATASET)
                .setKindName(DatasetDefinition.VISIT_BASED_STUDY)
                .setFields(List.of(
                        new FieldDefinition("IntField", FieldDefinition.ColumnType.Integer).setMvEnabled(true),
                        new FieldDefinition("StringField", FieldDefinition.ColumnType.String).setMvEnabled(true)
                ))
                .create(conn, containerPath);

        log("Validate inserting into the MV indicator dataset");
        RowsResponse response = expectSuccess(conn, MV_INDICATOR_DATASET, containerPath,
                List.of(
                        Map.of("Ptid", "111", "Visit", 1, "IntField", "N", "StringField", "Q"),
                        Map.of("Ptid", "222", "Visit", 2, "IntField", 1, "StringField", "StringValue"),
                        Map.of("Ptid", "333", "Visit", 3, "IntField", 1, "IntFieldMvIndicator", "Z")
                ));

        List<Map<String, Object>> rows = response.getRows();
        validateValuesPresent(rows.get(0), Map.of("IntFieldMvIndicator", "N",  "StringFieldMvIndicator", "Q"));
        validateValuesPresent(rows.get(1), Map.of("IntField", 1,  "StringField", "StringValue"));
        validateValuesPresent(rows.get(2), Map.of("IntField", 1,  "IntFieldMvIndicator", "Z"));

        log("Validate updating into the MV indicator dataset");
        List<Map<String, Object>> updated = new ArrayList<>();
        updated.add(Map.of("lsid", rows.get(0).get("lsid"), "IntField", "Z"));
        updated.add(Map.of("lsid", rows.get(1).get("lsid"), "StringField", "Q"));
        updated.add(Map.of("lsid", rows.get(2).get("lsid"), "IntField", 2, "IntFieldMvIndicator", "N"));

        UpdateRowsCommand cmd = new UpdateRowsCommand("Study", MV_INDICATOR_DATASET);
        cmd.setRows(updated);
        try
        {
            RowsResponse resp = cmd.execute(conn, containerPath);
            List<Map<String, Object>> updatedRows = resp.getRows();
            validateValuesPresent(updatedRows.get(0), Map.of("IntFieldMvIndicator", "Z", "StringFieldMvIndicator", "Q"));
            validateValuesPresent(updatedRows.get(1), Map.of("IntField", 1, "StringFieldMvIndicator", "Q"));
            validateValuesPresent(updatedRows.get(2), Map.of("IntField", 2, "IntFieldMvIndicator", "N"));
        }
        catch (CommandException e)
        {
            Assert.fail(String.format("Update failed for MV indicator dataset : %s", e.getMessage()));
        }
    }

    private void validateValuesPresent(Map<String, Object> row, Map<String, Object> expectedValues)
    {
        for (Map.Entry<String, Object> entry : expectedValues.entrySet())
        {
            assertEquals("Values are not equal", entry.getValue(), row.get(entry.getKey()));
        }
    }

    @Test
    public void testDateBasedStudy() throws Exception
    {
        log("Creating date study datasets");

        final String containerPath = String.format("%s/%s", getProjectName(), DATE_STUDY);
        Connection conn = createDefaultConnection();

        DatasetDefinition.create(DEMOGRAPHICS_DATASET)
                .setKindName(DatasetDefinition.DATE_BASED_STUDY)
                .setFields(List.of(
                        new FieldDefinition("Name", FieldDefinition.ColumnType.String),
                        new FieldDefinition("IntField", FieldDefinition.ColumnType.Integer)
                ))
                .setDemographics(true)
                .create(conn, containerPath);

        DatasetDefinition.create(NON_DEMOGRAPHICS_DATASET)
                .setKindName(DatasetDefinition.DATE_BASED_STUDY)
                .setFields(List.of(
                        new FieldDefinition("StringField", FieldDefinition.ColumnType.String),
                        new FieldDefinition("IntField", FieldDefinition.ColumnType.Integer)
                ))
                .create(conn, containerPath);

        DatasetDefinition.create(ADDITIONAL_KEY_FIELD)
                .setKindName(DatasetDefinition.DATE_BASED_STUDY)
                .setFields(List.of(
                        new FieldDefinition("StringField", FieldDefinition.ColumnType.String),
                        new FieldDefinition("IntField", FieldDefinition.ColumnType.Integer),
                        new FieldDefinition("AdditionalField", FieldDefinition.ColumnType.Integer)
                ))
                .setKeyPropertyName("AdditionalField")
                .create(conn, containerPath);

        DatasetDefinition.create(MANAGED_KEY_FIELD)
                .setKindName(DatasetDefinition.DATE_BASED_STUDY)
                .setFields(List.of(
                        new FieldDefinition("StringField", FieldDefinition.ColumnType.String),
                        new FieldDefinition("ManagedField", FieldDefinition.ColumnType.Integer),
                        new FieldDefinition("IntField", FieldDefinition.ColumnType.Integer)
                ))
                .setKeyPropertyName("ManagedField", true)
                .create(conn, containerPath);

        DatasetDefinition.create(TIME_PORTION_OF_DATE)
                .setKindName(DatasetDefinition.DATE_BASED_STUDY)
                .setFields(List.of(
                        new FieldDefinition("StringField", FieldDefinition.ColumnType.String),
                        new FieldDefinition("IntField", FieldDefinition.ColumnType.Integer)
                ))
                .setTimeKeyField(true)
                .create(conn, containerPath);

        log("Validate inserting rows into the demographics dataset");
        expectSuccess(conn, DEMOGRAPHICS_DATASET, containerPath,
                List.of(
                        Map.of("Ptid", "111", "Name", "Bob", "IntField", 20),
                        Map.of("Ptid", "222", "Name", "Mark", "IntField", 40),
                        Map.of("Ptid", "333", "Name", "Ave", "IntField", 60)
                ));
        expectFail(conn, DEMOGRAPHICS_DATASET, containerPath,
                List.of(Map.of("Ptid", "111", "Name", "DifferentName", "IntField", 2)));
        expectSuccess(conn, DEMOGRAPHICS_DATASET, containerPath,
                List.of(Map.of("Ptid", "444", "Name", "Bob", "IntField", 20)));

        log("Validate inserting into the non-demographics dataset");
        expectSuccess(conn, NON_DEMOGRAPHICS_DATASET, containerPath,
                List.of(
                        Map.of("Ptid", "111", "Date", "2025-04-15", "IntField", 1),
                        Map.of("Ptid", "222", "Date", "2025-04-15", "IntField", 1),
                        Map.of("Ptid", "333", "Date", "2025-04-15", "IntField", 1)
                ));
        expectFail(conn, NON_DEMOGRAPHICS_DATASET, containerPath,
                List.of(Map.of("Ptid", "111", "Date", "2025-04-15", "IntField", 1)));
        expectSuccess(conn, NON_DEMOGRAPHICS_DATASET, containerPath,
                List.of(Map.of("Ptid", "111", "Date", "2025-04-16", "IntField", 1)));

        log("Validate inserting into a dataset with a third key field");
        expectSuccess(conn, ADDITIONAL_KEY_FIELD, containerPath,
                List.of(
                        Map.of("Ptid", "111", "Date", "2025-1-10", "AdditionalField", 10),
                        Map.of("Ptid", "111", "Date", "2025-1-10", "AdditionalField", 20),
                        Map.of("Ptid", "111", "Date", "2025-1-10")
                ));
        expectFail(conn, ADDITIONAL_KEY_FIELD, containerPath,
                List.of(Map.of("Ptid", "111", "Date", "2025-1-10", "AdditionalField", 10)));
        expectFail(conn, ADDITIONAL_KEY_FIELD, containerPath,
                List.of(Map.of("Ptid", "111", "Date", "2025-1-10")));
        expectSuccess(conn, ADDITIONAL_KEY_FIELD, containerPath,
                List.of(Map.of("Ptid", "111", "Date", "2025-1-10", "AdditionalField", 40)));

        log("Validate inserting into a dataset with a managed key field");
        expectSuccess(conn, MANAGED_KEY_FIELD, containerPath,
                List.of(
                        Map.of("Ptid", "111", "Date", "2025-05-01", "IntField", 1),
                        Map.of("Ptid", "111", "Date", "2025-05-01", "IntField", 1),
                        Map.of("Ptid", "111", "Date", "2025-05-01", "IntField", 1)
                ));

        log("Validate inserting into a dataset using the time portion of the date field as a third key");
        expectSuccess(conn, TIME_PORTION_OF_DATE, containerPath,
                List.of(
                        Map.of("Ptid", "111", "Date", "2025-05-01 1:00pm", "IntField", 1),
                        Map.of("Ptid", "111", "Date", "2025-05-01 1:15pm", "IntField", 1),
                        Map.of("Ptid", "111", "Date", "2025-05-01 2:00am", "IntField", 1)
                ));
        expectFail(conn, TIME_PORTION_OF_DATE, containerPath,
                List.of(Map.of("Ptid", "111", "Date", "2025-05-01 1:15pm")));

        log("Validate participant/visit generation");
        validateParticipantVisits(conn, containerPath, 10);

        // test that generation of special columns is as expected in the update case
        validateUpdateOfGeneratedColumns(conn, containerPath, DEMOGRAPHICS_DATASET);
        validateUpdateOfGeneratedColumns(conn, containerPath, NON_DEMOGRAPHICS_DATASET);
        validateUpdateOfGeneratedColumns(conn, containerPath, ADDITIONAL_KEY_FIELD);
        validateUpdateOfGeneratedColumns(conn, containerPath, MANAGED_KEY_FIELD);
        validateUpdateOfGeneratedColumns(conn, containerPath, TIME_PORTION_OF_DATE);
    }

    private RowsResponse expectSuccess(Connection conn, String datasetName, String containerPath, List<Map<String, Object>> rows) throws Exception
    {
        return validateInsertRows(conn, datasetName, containerPath, rows, false);
    }

    private RowsResponse expectFail(Connection conn, String datasetName, String containerPath, List<Map<String, Object>> rows) throws Exception
    {
        return validateInsertRows(conn, datasetName, containerPath, rows, true);
    }

    private RowsResponse validateInsertRows(Connection conn, String datasetName, String containerPath, List<Map<String, Object>> rows, boolean fail) throws Exception
    {
        RowsResponse resp = null;
        InsertRowsCommand cmd = new InsertRowsCommand("Study", datasetName);
        cmd.setRows(rows);
        try
        {
            resp = cmd.execute(conn, containerPath);
        }
        catch (CommandException e)
        {
            if (fail)
                // error message may vary depending on single or multi row inserts, postgres or sql server
                assertTrue(String.format("Expected a duplicate key error but was : %s", e.getMessage()),
                        e.getMessage().contains("Only one row is allowed for each Participant") ||
                        e.getMessage().contains("duplicate key value violates unique constraint") ||
                        e.getMessage().contains("Cannot insert duplicate key row"));
            else
                Assert.fail(String.format("Expected the insert to succeed but instead it failed : %s", e.getMessage()));
            return resp;
        }

        assertFalse("Expected the insert to fail.", fail);
        return resp;
    }

    private void validateParticipantVisits(Connection conn, String containerPath, int expectedCount) throws Exception
    {
        SelectRowsCommand cmd = new SelectRowsCommand("Study", "ParticipantVisit");
        SelectRowsResponse resp = cmd.execute(conn, containerPath);
        assertEquals(String.format("Expecting exactly %d distinct participant visits", expectedCount), expectedCount, resp.getRowCount().intValue());
    }

    private void validateUpdateOfGeneratedColumns(Connection conn, String containerPath, String queryName) throws IOException
    {
        List<String> fieldsToCheck = List.of("ParticipantId", "Lsid", "ParticipantSequenceNum", "SequenceNum");
        List<String> selectFields = new ArrayList<>(fieldsToCheck);
        selectFields.add("IntField");
        Map<String, Map<String, Object>> existingValues = new HashMap<>();
        List<Map<String, Object>> rowsToUpdate = new ArrayList<>();

        try
        {
            // get the existing values
            SelectRowsCommand cmd = new SelectRowsCommand("Study", queryName);
            cmd.setColumns(selectFields);
            SelectRowsResponse resp = cmd.execute(conn, containerPath);
            for (Map<String, Object> row : resp.getRows())
            {
                String lsid = String.valueOf(row.get("Lsid"));
                existingValues.computeIfAbsent(lsid, p -> new CaseInsensitiveHashMap<>()).putAll(row);

                // update a value that shouldn't result in any of the computed fields to change
                rowsToUpdate.add(Map.of("Lsid", lsid, "IntField", 5));
            }

            // update all rows and then recheck fields again
            UpdateRowsCommand updateRowsCommand = new UpdateRowsCommand("Study", queryName);
            updateRowsCommand.setRows(rowsToUpdate);
            updateRowsCommand.execute(conn, containerPath);

            // re-invoke the select rows to get the updated values
            cmd = new SelectRowsCommand("Study", queryName);
            cmd.setColumns(selectFields);
            resp = cmd.execute(conn, containerPath);
            for (Map<String, Object> row : resp.getRows())
            {
                String lsid = (String) row.get("Lsid");
                Map<String, Object> existingRow = existingValues.get(lsid);
                assertNotNull(String.format("Unable to find previous row with lsid : %s", lsid), existingRow);

                // validate computed fields
                for (String field : fieldsToCheck)
                    assertEquals(String.format("Value for field : %s should not have changed", field), row.get(field), existingRow.get(field));
            }
        }
        catch (CommandException e)
        {
            fail(e.getMessage());
        }
    }
}
