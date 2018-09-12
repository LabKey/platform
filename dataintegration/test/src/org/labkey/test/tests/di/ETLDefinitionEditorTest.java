package org.labkey.test.tests.di;

import org.jetbrains.annotations.Nullable;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.remoteapi.CommandException;
import org.labkey.test.categories.DailyB;
import org.labkey.test.categories.Data;
import org.labkey.test.categories.ETL;
import org.labkey.test.pages.di.ConfirmDeletePage;
import org.labkey.test.pages.di.DefinitionPage;
import org.labkey.test.pages.di.DefinitionsQueryView;

import java.io.IOException;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * User: tgaluhn
 * Date: 6/26/2018
 */
@Category({DailyB.class, Data.class, ETL.class})
public class ETLDefinitionEditorTest extends ETLAbstractTest
{
    private final String DEFINITION_NAME = "User Defined ETL 1";
    private final String DEFINITION_XML = "<etl xmlns=\"http://labkey.org/etl/xml\">\n" +
            "\t<name>User Defined ETL 1</name>\n" +
            "\t<description>User Defined ETL</description>\n" +
            "\t<transforms>\n" +
            "\t\t<transform id=\"step1\" type=\"org.labkey.di.pipeline.TransformTask\">\n" +
            "\t\t\t<description>Copy to target</description>\n" +
            "\t\t\t<source schemaName=\"etltest\" queryName=\"source\" />\n" +
            "\t\t\t<destination schemaName=\"etltest\" queryName=\"target\" />\n" +
            "\t\t</transform>\n" +
            "\t</transforms>\n" +
            "\t<incrementalFilter className=\"ModifiedSinceFilterStrategy\" timestampColumnName=\"modified\"/>\n" +
            "\t<schedule>\n" +
            "\t\t<poll interval=\"5s\" />\n" +
            "\t</schedule>\n" +
            "</etl>";

    private final String DEFINITION_NAME_2 = "User Defined ETL 2";
    private final String NEW_STEP_NAME = "step1forEditTest";
    private final String DEFINITION_XML_2 = DEFINITION_XML.replace(DEFINITION_NAME, DEFINITION_NAME_2).replace("step1", NEW_STEP_NAME);

    private final String DEFINITION_NAME_3 = "User Defined ETL 3";
    private final String DEFINITION_XML_3 = DEFINITION_XML.replace(DEFINITION_NAME, DEFINITION_NAME_3);

    private final String DEFINITION_NAME_4 = "User Defined ETL 4";
    private final String DEFINITION_XML_4 = DEFINITION_XML.replace(DEFINITION_NAME, DEFINITION_NAME_4);

    private final String DEFINITION_NAME_5 = "User Defined ETL 5";
    private final String DEFINITION_XML_5 = DEFINITION_XML.replace(DEFINITION_NAME, DEFINITION_NAME_5);

    private final String DEFINITION_NAME_6 = "User Defined ETL 6";
    private final String DEFINITION_XML_6 = DEFINITION_XML.replace(DEFINITION_NAME, DEFINITION_NAME_6);

    private final String BAD_DEFINITION_NAME = "Bad User Defined ETL";
    private final String BAD_DEFINITION_XML = DEFINITION_XML.replace(DEFINITION_NAME, BAD_DEFINITION_NAME).replace("<transforms>", "<transforms><BadXml/>");

    @BeforeClass
    public static void setupProject()
    {
        ETLDefinitionEditorTest init = (ETLDefinitionEditorTest) getCurrentTest();
        init.doSetup();
    }

    @Override
    protected void doSetup()
    {
        _etlHelper.doBasicSetup();
    }

    @Override
    protected @Nullable String getProjectName()
    {
        return this.getClass().getSimpleName() + " Project";
    }


    @Override
    public void preTest() throws Exception
    {
        super.preTest();
        deleteAllRows(getProjectName(), ETLHelper.DATAINTEGRATION_SCHEMA, "etlDef");
    }

    @Test
    public void testBasicCRUD()
    {
        goToProjectHome();

        log("Create a new ETL definition");
        DefinitionsQueryView defsView = DefinitionsQueryView.beginAt(this);
        defsView.createNew(DEFINITION_XML);
        defsView.assertEtlPresent(DEFINITION_NAME);

        log("Verify name must be unique in folder");
        defsView.createNewWithNameConflict(DEFINITION_XML);
        DefinitionPage defPage = new DefinitionPage(getDriver());
        defPage.cancel();

        log("Verify xml is validated");
        defsView = DefinitionsQueryView.beginAt(this);
        defsView.editAndSave(DEFINITION_NAME, BAD_DEFINITION_XML, "Document does not conform to its XML schema");

        log("Verify the malformed xml is not thrown away on refresh");
        defPage = new DefinitionPage(getDriver());
        assertTrue(defPage.getDefinitionXml().contains(BAD_DEFINITION_XML));
        defPage.cancel();

        log("Verify edit button from the details page");
        defsView = DefinitionsQueryView.beginAt(this);
        defsView.details(DEFINITION_NAME).edit().setDefinitionXml(DEFINITION_XML_3).cancel();

        log("Verify deletions");
        defsView.createNew(DEFINITION_XML_2);
        ConfirmDeletePage deletePage = defsView.delete(DEFINITION_NAME_2);
        deletePage.confirmDelete();

        defsView.assertEtlNotPresent(DEFINITION_NAME_2);
    }

    @Test
    public void testSaveOptions()
    {
        goToProjectHome();

        log("Create a new ETL definition");
        DefinitionsQueryView defsView = DefinitionsQueryView.beginAt(this);
        defsView.createNew(DEFINITION_XML_4);
        defsView.assertEtlPresent(DEFINITION_NAME_4);

        log("Change the name of the same definition and overwrite");
        DefinitionPage defPage = defsView.edit(DEFINITION_NAME_4);
        defPage.setDefinitionXml(DEFINITION_XML_5).saveChangedName(false);
        defsView.assertEtlPresent(DEFINITION_NAME_5);
        defsView.assertEtlNotPresent(DEFINITION_NAME_4);

        log("Change the name of the same definition and save as new");
        defPage = defsView.edit(DEFINITION_NAME_5);
        defPage.setDefinitionXml(DEFINITION_XML_4).saveChangedName(true);
        defsView.assertEtlPresent(DEFINITION_NAME_5);
        defsView.assertEtlPresent(DEFINITION_NAME_4);
    }

    @Test
    public void testCopyFromExisting()
    {
        goToProjectHome();
        DefinitionsQueryView defsView = DefinitionsQueryView.beginAt(this);
        if (!defsView.isEtlPresent(DEFINITION_NAME_5))
            defsView.createNew(DEFINITION_XML_5);

        log("Create a new ETL definition");
        defsView.createNew(DEFINITION_XML_6);
        defsView.assertEtlPresent(DEFINITION_NAME_6);

        log("Copy xml from an existing ETL definition");
        DefinitionPage defPage = defsView.edit(DEFINITION_NAME_6);
        defPage.copyFromExisting(getCurrentContainerPath(), DEFINITION_NAME_5);

        log("Ensure the XML was copied over properly");
        assertTrue(defPage.getDefinitionXml().contains(DEFINITION_XML_5));

    }

    @Test
    public void testCacheAndScheduler() throws Exception
    {
        goToProjectHome();

        final String SOURCE_ROW_NAME = "NameForEtlTesting";

        log("Create a new ETL definition");
        DefinitionsQueryView defsView = DefinitionsQueryView.beginAt(this);
        defsView.createNew(DEFINITION_XML);
        final String transformId = "{DataIntegration}/User_Defined_EtlDefId_" + defsView.getRowPk(DEFINITION_NAME);

        log("Add a source row");
        final String SOURCE_ROW_NAME_1 = SOURCE_ROW_NAME + 1;
        _etlHelper.insertSourceRow("1", SOURCE_ROW_NAME_1, null);
        log("Enable the ETL");
        // Doing this through the UI also ensures the cache got updated
        goToModule(ETLHelper.DATAINTEGRATION_MODULE);
        _etlHelper.enableScheduledRun(DEFINITION_NAME);
        log("Verify it ran successfully");
        _etlHelper.waitForStatus(transformId, ETLHelper.COMPLETE, 30000);;
        _etlHelper.assertInTarget1_Api(SOURCE_ROW_NAME_1);

        log("Verify edit behavior");
        defsView = DefinitionsQueryView.beginAt(this);
        DefinitionPage defPage = defsView.edit(DEFINITION_NAME);
        assertTextPresent("Warning: This ETL has been enabled and is scheduled to run.");
        log("Change definition, later we verify the change propagates through cache.");
        defPage.setDefinitionXml(DEFINITION_XML.replace("queryName=\"target\"", "queryName=\"target2\""));
        defPage.save();
        log("Verify we really did disable");
        assertFalse("ETL is still enabled to run", _etlHelper.getDiHelper().getTransformEnabled(transformId));
        log("Verify we really did remove from scheduler");
        final String SOURCE_ROW_NAME_2 = SOURCE_ROW_NAME + 2;
        _etlHelper.insertSourceRow("2", SOURCE_ROW_NAME_2, null);
        sleep(10000);
        // verify not in either target or target2. Whether or not change propagated, if scheduled run happened one of these asserts would fail
        _etlHelper.assertNotInTarget1_Api(SOURCE_ROW_NAME_2);
        _etlHelper.assertNotInTarget2_Api(SOURCE_ROW_NAME_2);

        log("Verify delete confirmation for unscheduled etls");
        defsView = DefinitionsQueryView.beginAt(this);
        ConfirmDeletePage deletePage = defsView.deleteWithEnabledCheck(DEFINITION_NAME, false);
        deletePage.cancel();

        log("Verify change propagated - reenable the ETL, let it run.");
        _etlHelper.getDiHelper().updateTransformConfiguration(transformId, null, true);
        waitFor(()->
                {
                    log("Waiting for insertion of new target text.");
                    try
                    {
                        return _etlHelper.isInTarget2(SOURCE_ROW_NAME_2);
                    }
                    catch (IOException | CommandException e)
                    {
                        return false;
                    }

                }, 20000
        );
        log("Target text found.");

        log("Verify delete behavior for scheduled etls");
        defsView = DefinitionsQueryView.beginAt(this);
        deletePage = defsView.deleteWithEnabledCheck(DEFINITION_NAME, true);
        deletePage.confirmDelete();
        log("Verify we really did disable");
        assertFalse("ETL is still enabled to run", _etlHelper.getDiHelper().getTransformEnabled(transformId));
        log("Verify we really did remove from scheduler");
        final String SOURCE_ROW_NAME_3 = SOURCE_ROW_NAME + 3;
        _etlHelper.insertSourceRow("3", SOURCE_ROW_NAME_3, null);
        sleep(10000);
        _etlHelper.assertNotInTarget2_Api(SOURCE_ROW_NAME);
        log("Verify deleted etl removed from available transforms");
        goToModule(ETLHelper.DATAINTEGRATION_MODULE);
        assertTextNotPresent(DEFINITION_NAME);
    }
}
