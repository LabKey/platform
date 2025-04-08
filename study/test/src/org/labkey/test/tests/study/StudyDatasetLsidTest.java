package org.labkey.test.tests.study;

import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.remoteapi.CommandException;
import org.labkey.remoteapi.Connection;
import org.labkey.remoteapi.domain.CreateDomainCommand;
import org.labkey.remoteapi.domain.PropertyDescriptor;
import org.labkey.remoteapi.query.InsertRowsCommand;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.categories.Daily;
import org.labkey.test.params.FieldDefinition;
import org.labkey.test.util.StudyHelper;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@Category({Daily.class})
public class StudyDatasetLsidTest extends BaseWebDriverTest
{
    private static final String VISIT_STUDY = "Visit based study";
    private static final String DATE_STUDY = "Date based study";

    // datasets
    private static final String DEMOGRAPHICS_DATASET = "DemograpicsDataset";
    private static final String NON_DEMOGRAPHICS_DATASET = "NonDemographicsDataset";
    private static final String ADDITIONAL_KEY_FIELD = "AdditionalKey";
    private static final String MANAGED_KEY_FIELD = "ManagedKey";
    private static final String TIME_PORTION_OF_DATE = "TimePortionOfDate";

    @BeforeClass
    public static void setupProject() throws Exception
    {
        StudyDatasetLsidTest initTest = getCurrentTest();
        initTest.doSetup();
    }

    private void doSetup()
    {
        _containerHelper.createProject(getProjectName());

        log("Creating date and visit studies");
        _containerHelper.createSubfolder(getProjectName(), VISIT_STUDY, "Study");
        _studyHelper.startCreateStudy()
                .setTimepointType(StudyHelper.TimepointType.VISIT)
                .createStudy();

        _containerHelper.createSubfolder(getProjectName(), DATE_STUDY, "Study");
        _studyHelper.startCreateStudy()
                .setTimepointType(StudyHelper.TimepointType.CONTINUOUS)
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
        final String kindName = "StudyDatasetVisit";
        Connection conn = createDefaultConnection();

        createDataset(conn,
                kindName,
                DEMOGRAPHICS_DATASET,
                containerPath,
                List.of(
                        new FieldDefinition("Name", FieldDefinition.ColumnType.String),
                        new FieldDefinition("Age", FieldDefinition.ColumnType.Decimal)
                ),
                Map.of("demographics", true)
        );
        createDataset(conn,
                kindName,
                NON_DEMOGRAPHICS_DATASET,
                containerPath,
                List.of(
                        new FieldDefinition("StringField", FieldDefinition.ColumnType.String),
                        new FieldDefinition("IntegerField", FieldDefinition.ColumnType.Decimal)
                ),
                Collections.emptyMap()
        );
        createDataset(conn,
                kindName,
                ADDITIONAL_KEY_FIELD,
                containerPath,
                List.of(
                        new FieldDefinition("StringField", FieldDefinition.ColumnType.String),
                        new FieldDefinition("IntegerField", FieldDefinition.ColumnType.Decimal)
                ),
                Map.of("keyPropertyName", "IntegerField")
        );
        createDataset(conn,
                kindName,
                MANAGED_KEY_FIELD,
                containerPath,
                List.of(
                        new FieldDefinition("StringField", FieldDefinition.ColumnType.String),
                        new FieldDefinition("ManagedField", FieldDefinition.ColumnType.Decimal)
                ),
                Map.of(
                    "keyPropertyName", "ManagedField",
                    "keyPropertyManaged", true)
        );

        log("Validate inserting rows into the demographics dataset");
        expectSuccess(conn, DEMOGRAPHICS_DATASET, containerPath,
            List.of(
                Map.of("Ptid", "111", "Name", "Bob", "Age", 20),
                Map.of("Ptid", "222", "Name", "Mark", "Age", 40),
                Map.of("Ptid", "333", "Name", "Ave", "Age", 60)
        ));
        expectFail(conn, DEMOGRAPHICS_DATASET, containerPath,
                List.of(Map.of("Ptid", "111", "Name", "DifferentName", "Age", 2)));
        expectSuccess(conn, DEMOGRAPHICS_DATASET, containerPath,
                List.of(Map.of("Ptid", "444", "Name", "Bob", "Age", 20)));

        log("Validate inserting into the non-demographics dataset");
        expectSuccess(conn, NON_DEMOGRAPHICS_DATASET, containerPath,
                List.of(
                        Map.of("Ptid", "111", "Visit", 1),
                        Map.of("Ptid", "222", "Visit", 2),
                        Map.of("Ptid", "333", "Visit", 3)
                ));
        expectFail(conn, NON_DEMOGRAPHICS_DATASET, containerPath,
                List.of(Map.of("Ptid", "111", "Visit", 1)));
        expectSuccess(conn, NON_DEMOGRAPHICS_DATASET, containerPath,
                List.of(Map.of("Ptid", "111", "Visit", 2)));

        log("Validate inserting into a dataset with a third key field");
        expectSuccess(conn, ADDITIONAL_KEY_FIELD, containerPath,
                List.of(
                        Map.of("Ptid", "111", "Visit", 1, "IntegerField", 10),
                        Map.of("Ptid", "111", "Visit", 1, "IntegerField", 20),
                        Map.of("Ptid", "111", "Visit", 1)
                ));
        expectFail(conn, ADDITIONAL_KEY_FIELD, containerPath,
                List.of(Map.of("Ptid", "111", "Visit", 1, "IntegerField", 10)));
        expectFail(conn, ADDITIONAL_KEY_FIELD, containerPath,
                List.of(Map.of("Ptid", "111", "Visit", 1)));
        expectSuccess(conn, ADDITIONAL_KEY_FIELD, containerPath,
                List.of(Map.of("Ptid", "111", "Visit", 1, "IntegerField", 40)));

        log("Validate inserting into a dataset with a managed key field");
        expectSuccess(conn, MANAGED_KEY_FIELD, containerPath,
                List.of(
                        Map.of("Ptid", "111", "Visit", 1),
                        Map.of("Ptid", "111", "Visit", 1),
                        Map.of("Ptid", "111", "Visit", 1)
                ));
        expectSuccess(conn, ADDITIONAL_KEY_FIELD, containerPath,
                List.of(Map.of("Ptid", "222", "Visit", 1)));
    }

    @Test
    public void testDateBasedStudy() throws Exception
    {
        log("Creating date study datasets");

        final String containerPath = String.format("%s/%s", getProjectName(), DATE_STUDY);
        final String kindName = "StudyDatasetDate";
        Connection conn = createDefaultConnection();

        createDataset(conn,
                kindName,
                DEMOGRAPHICS_DATASET,
                containerPath,
                List.of(
                        new FieldDefinition("Name", FieldDefinition.ColumnType.String),
                        new FieldDefinition("Age", FieldDefinition.ColumnType.Decimal)
                ),
                Map.of("demographics", true)
        );
        createDataset(conn,
                kindName,
                NON_DEMOGRAPHICS_DATASET,
                containerPath,
                List.of(
                        new FieldDefinition("StringField", FieldDefinition.ColumnType.String),
                        new FieldDefinition("IntegerField", FieldDefinition.ColumnType.Decimal)
                ),
                Collections.emptyMap()
        );
        createDataset(conn,
                kindName,
                ADDITIONAL_KEY_FIELD,
                containerPath,
                List.of(
                        new FieldDefinition("StringField", FieldDefinition.ColumnType.String),
                        new FieldDefinition("IntegerField", FieldDefinition.ColumnType.Decimal)
                ),
                Map.of("keyPropertyName", "IntegerField")
        );
        createDataset(conn,
                kindName,
                MANAGED_KEY_FIELD,
                containerPath,
                List.of(
                        new FieldDefinition("StringField", FieldDefinition.ColumnType.String),
                        new FieldDefinition("ManagedField", FieldDefinition.ColumnType.Decimal)
                ),
                Map.of(
                        "keyPropertyName", "ManagedField",
                        "keyPropertyManaged", true)
        );
        createDataset(conn,
                kindName,
                TIME_PORTION_OF_DATE,
                containerPath,
                List.of(
                        new FieldDefinition("StringField", FieldDefinition.ColumnType.String),
                        new FieldDefinition("IntegerField", FieldDefinition.ColumnType.Decimal)
                ),
                Map.of("useTimeKeyField", true)
        );

        log("Validate inserting rows into the demographics dataset");
        expectSuccess(conn, DEMOGRAPHICS_DATASET, containerPath,
                List.of(
                        Map.of("Ptid", "111", "Name", "Bob", "Age", 20),
                        Map.of("Ptid", "222", "Name", "Mark", "Age", 40),
                        Map.of("Ptid", "333", "Name", "Ave", "Age", 60)
                ));
        expectFail(conn, DEMOGRAPHICS_DATASET, containerPath,
                List.of(Map.of("Ptid", "111", "Name", "DifferentName", "Age", 2)));
        expectSuccess(conn, DEMOGRAPHICS_DATASET, containerPath,
                List.of(Map.of("Ptid", "444", "Name", "Bob", "Age", 20)));

        log("Validate inserting into the non-demographics dataset");
        expectSuccess(conn, NON_DEMOGRAPHICS_DATASET, containerPath,
                List.of(
                        Map.of("Ptid", "111", "Date", "2025-04-15"),
                        Map.of("Ptid", "222", "Date", "2025-04-15"),
                        Map.of("Ptid", "333", "Date", "2025-04-15")
                ));
        expectFail(conn, NON_DEMOGRAPHICS_DATASET, containerPath,
                List.of(Map.of("Ptid", "111", "Date", "2025-04-15")));
        expectSuccess(conn, NON_DEMOGRAPHICS_DATASET, containerPath,
                List.of(Map.of("Ptid", "111", "Date", "2025-04-16")));

        log("Validate inserting into a dataset with a third key field");
        expectSuccess(conn, ADDITIONAL_KEY_FIELD, containerPath,
                List.of(
                        Map.of("Ptid", "111", "Date", "2025-1-10", "IntegerField", 10),
                        Map.of("Ptid", "111", "Date", "2025-1-10", "IntegerField", 20),
                        Map.of("Ptid", "111", "Date", "2025-1-10")
                ));
        expectFail(conn, ADDITIONAL_KEY_FIELD, containerPath,
                List.of(Map.of("Ptid", "111", "Date", "2025-1-10", "IntegerField", 10)));
        expectFail(conn, ADDITIONAL_KEY_FIELD, containerPath,
                List.of(Map.of("Ptid", "111", "Date", "2025-1-10")));
        expectSuccess(conn, ADDITIONAL_KEY_FIELD, containerPath,
                List.of(Map.of("Ptid", "111", "Date", "2025-1-10", "IntegerField", 40)));

        log("Validate inserting into a dataset with a managed key field");
        expectSuccess(conn, MANAGED_KEY_FIELD, containerPath,
                List.of(
                        Map.of("Ptid", "111", "Date", "2025-05-01"),
                        Map.of("Ptid", "111", "Date", "2025-05-01"),
                        Map.of("Ptid", "111", "Date", "2025-05-01")
                ));

        log("Validate inserting into a dataset using the time portion of the date field as a third key");
        expectSuccess(conn, TIME_PORTION_OF_DATE, containerPath,
                List.of(
                        Map.of("Ptid", "111", "Date", "2025-05-01 1:00pm"),
                        Map.of("Ptid", "111", "Date", "2025-05-01 1:15pm"),
                        Map.of("Ptid", "111", "Date", "2025-05-01 2:00am")
                ));
        expectFail(conn, TIME_PORTION_OF_DATE, containerPath,
                List.of(Map.of("Ptid", "111", "Date", "2025-05-01 1:15pm")));
    }

    // Create datasets via the java api
    private void createDataset(Connection conn, String kindName, String datasetName, String folderName, List<PropertyDescriptor> fields, Map<String, Object> options) throws Exception
    {
        CreateDomainCommand cmd = new CreateDomainCommand(kindName, datasetName);
        cmd.getDomainDesign().setFields(fields);
        cmd.setOptions(options);
        cmd.execute(conn, folderName);
    }

    private void expectSuccess(Connection conn, String datasetName, String containerPath, List<Map<String, Object>> rows) throws Exception
    {
        validateInsertRows(conn, datasetName, containerPath, rows, false);
    }

    private void expectFail(Connection conn, String datasetName, String containerPath, List<Map<String, Object>> rows) throws Exception
    {
        validateInsertRows(conn, datasetName, containerPath, rows, true);
    }

    private void validateInsertRows(Connection conn, String datasetName, String containerPath, List<Map<String, Object>> rows, boolean fail) throws Exception
    {
        InsertRowsCommand cmd = new InsertRowsCommand("Study", datasetName);
        cmd.setRows(rows);
        try
        {
            cmd.execute(conn, containerPath);
        }
        catch (CommandException e)
        {
            if (fail)
                // error message may vary depending on single or multi row inserts
                assertTrue(String.format("Expected a duplicate key error but was : %s", e.getMessage()),
                        e.getMessage().contains("Only one row is allowed for each Participant") || e.getMessage().contains("duplicate key value violates unique constraint"));
            else
                Assert.fail(String.format("Expected the insert to succeed but instead it failed : %s", e.getMessage()));
            return;
        }

        assertFalse("Expected the insert to fail.", fail);
    }
}
