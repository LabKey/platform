package org.labkey.test.tests.experiment;

import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.remoteapi.CommandException;
import org.labkey.remoteapi.assay.Run;
import org.labkey.remoteapi.assay.SaveAssayBatchCommand;
import org.labkey.remoteapi.assay.SaveAssayRunsCommand;
import org.labkey.remoteapi.assay.SaveAssayRunsResponse;
import org.labkey.remoteapi.domain.DomainResponse;
import org.labkey.remoteapi.domain.PropertyDescriptor;
import org.labkey.remoteapi.query.InsertRowsCommand;
import org.labkey.remoteapi.query.SelectRowsCommand;
import org.labkey.remoteapi.query.SelectRowsResponse;
import org.labkey.test.Locator;
import org.labkey.test.categories.Daily;
import org.labkey.test.components.CustomizeView;
import org.labkey.test.components.html.Table;
import org.labkey.test.params.experiment.SampleTypeDefinition;
import org.labkey.test.util.DataRegionTable;
import org.labkey.test.util.ListHelper;
import org.labkey.test.util.PortalHelper;
import org.labkey.test.util.SampleTypeHelper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.CoreMatchers.hasItem;

@Category({Daily.class})
public class VocabularyViewSupportTest extends ProvenanceAssayHelper
{

    @BeforeClass
    public static void setupProject()
    {
        VocabularyViewSupportTest init = (VocabularyViewSupportTest) getCurrentTest();
        init.doSetup();
    }

    @Before
    public void preTest()
    {
        goToProjectHome();
    }

    private void doSetup()
    {
        PortalHelper portalHelper = new PortalHelper(this);
        _containerHelper.createProject(getProjectName(), null);
        projectMenu().navigateToProject(getProjectName());
        portalHelper.addWebPart("Sample Types");
    }

    @Override
    protected @Nullable String getProjectName()
    {
        return "VocabularyViewSupportTest Project";
    }

    @Override
    public List<String> getAssociatedModules()
    {
        return Arrays.asList("experiment","provenance");
    }

    @Test
    public void testSampleSetViewSupport() throws IOException, CommandException
    {
        String listName = "Locations";
        String cityName = "Torrance";

        createListForVocabPropertyLookup(listName);
        clickAndWait(Locator.linkWithText(listName));
        _listHelper.insertNewRow(Map.of("name", cityName));

        int listRow1RowId = 1;

        log("Create a vocabulary - one string prop, one number prop, one lookup to a list");
        String domainKind = "Vocabulary";
        String domainName = "CarProperties";
        String description = "Additional property set of cars.";

        String prop1Name = "Color";
        String rangeURI1 = "string";
        String prop1Value = "Black";
        PropertyDescriptor pd1 = new PropertyDescriptor(prop1Name, rangeURI1);

        String prop2Name = "Year";
        String rangeURI2 = "int";
        String prop2Value = "2019";
        PropertyDescriptor pd2 = new PropertyDescriptor(prop2Name, rangeURI2);

        String prop3Name = "Origin";
        String rangeURI3 = "int";
        PropertyDescriptor pd3 = new PropertyDescriptor(prop3Name, rangeURI3);
        pd3.setLookup("lists", listName, getCurrentContainer());

        List<PropertyDescriptor> fields = new ArrayList<>();
        fields.add(pd1);
        fields.add(pd2);
        fields.add(pd3);

        DomainResponse domainResponse = createDomain(domainKind, domainName, description, fields);
        int domainId = domainResponse.getDomain().getDomainId().intValue();

        log("Create a sampleset");
        String sampleSchemaName = "samples";
        String sampleSetName = "Cars";

        goToProjectHome();
        SampleTypeHelper sampleHelper = new SampleTypeHelper(this);
        sampleHelper.createSampleType(new SampleTypeDefinition(sampleSetName));

        log("call to insertRows with a voc property");
        int sampleSetRowCount = 0;
        InsertRowsCommand insertRowsCommand = new InsertRowsCommand(sampleSchemaName, sampleSetName);

        Map<String, Object> row = new HashMap<>();
        row.put("Name", "Tesla");
        // value for color vocabulary property
        row.put(domainResponse.getDomain().getFields().get(0).getPropertyURI(), prop1Value);
        // value for year vocabulary property
        row.put(domainResponse.getDomain().getFields().get(1).getPropertyURI(), prop2Value);
        // value for list lookup property
        row.put(domainResponse.getDomain().getFields().get(2).getPropertyURI(), listRow1RowId);

        insertRowsCommand.addRow(row);
        insertRowsCommand.execute(createDefaultConnection(), getProjectName());

        SelectRowsCommand cmdsel = new SelectRowsCommand(sampleSchemaName, sampleSetName);
        SelectRowsResponse srresp = cmdsel.execute(createDefaultConnection(), getProjectName());
        assert srresp.getRowCount().intValue() == sampleSetRowCount + 1;

        log("goto dataclass grid .. open customize grid panel");
        goToProjectHome();
        click(Locator.linkWithText("Sample Types"));
        sampleHelper.goToSampleType(sampleSetName);
        DataRegionTable drt = sampleHelper.getSamplesDataRegionTable();
        CustomizeView sampleSetCustomizeGrid = drt.openCustomizeGrid();
        sampleSetCustomizeGrid.showHiddenItems();

        String domainProperty = domainName + domainId;
        log("Verify vocabulary is present");
        Assert.assertTrue("Vocabulary is not present.", sampleSetCustomizeGrid.isColumnPresent(domainProperty));

        log("Verify vocab properties are present");
        Assert.assertTrue("Color Vocabulary Property is not present.", sampleSetCustomizeGrid.isColumnPresent(domainProperty + "/" + prop1Name));
        Assert.assertTrue("Year Vocabulary Property is not present.", sampleSetCustomizeGrid.isColumnPresent(domainProperty + "/" + prop2Name));
        Assert.assertTrue("List lookup Vocabulary Property is not present.", sampleSetCustomizeGrid.isLookupColumn(domainProperty + "/" + prop3Name));

        log("Verify property column is present");
        Assert.assertTrue("Properties is not present.", sampleSetCustomizeGrid.isColumnPresent("Properties"));

        sampleSetCustomizeGrid.addColumn(domainProperty + "/" + prop1Name);
        sampleSetCustomizeGrid.addColumn(domainProperty + "/" + prop2Name);
        sampleSetCustomizeGrid.addColumn(domainProperty + "/" + prop3Name);
        sampleSetCustomizeGrid.addColumn("Properties");

        sampleSetCustomizeGrid.applyCustomView();

        List<String> rowData = drt.getRowDataAsText(0);

        // Make the test more robust by removing a dependency on string order. Get the smaller table contained in the
        // 'Properties' and turn it into a map.
        Table table = new Table(getDriver(),
                Locator.tagWithClassContaining("table", "labkey-data-region")
                        .findElement(drt.findCell(0, "Properties")));
        List<String> propHeaders = table.getTableHeaderTexts();
        List<String> propertiesRowText = table.getRowAsText(0);

        Assert.assertEquals("How odd! The number of data columns in the smaller table do not match the number of header columns.",
                propHeaders.size(), propertiesRowText.size());

        Map<String, String> propertiesValue = new HashMap<>();
        for(int i = 0; i < propertiesRowText.size(); i++)
        {
            propertiesValue.put(propHeaders.get(i), propertiesRowText.get(i));
        }

        Map<String, String> expectedPropertiesValue = Map.of(prop1Name, prop1Value, prop2Name, prop2Value, prop3Name, cityName);

        Assert.assertThat("Row data does not contain color property value.", rowData, hasItem(prop1Value));
        Assert.assertThat("Row data does not contain year property value.", rowData, hasItem(String.valueOf(prop2Value)));
        Assert.assertThat("Row data does not contain list property value.", rowData, hasItem(cityName));
        Assert.assertEquals("Row data does not contain properties property value.", expectedPropertiesValue, propertiesValue);
    }

    @Test
    public void testAssayViewSupport() throws IOException, CommandException
    {
        String listName = "Lab Locations";
        String labLocation = "West wing";

        createListForVocabPropertyLookup(listName);
        clickAndWait(Locator.linkWithText(listName));
        _listHelper.insertNewRow(Map.of("name", labLocation));
        int listRow1RowId = 1;

        log("Create a vocabulary - one string prop, one lookup to a list");
        String domainKind = "Vocabulary";
        String domainName = "RunVocabularyProperties";
        String description = "Additional property set for Runs.";

        String propNameLab = "Lab name";
        String propValueLab = "Ocean";
        PropertyDescriptor pd1 = new PropertyDescriptor(propNameLab, "string");

        String propNameLocation = "Location";
        PropertyDescriptor pd2 = new PropertyDescriptor(propNameLocation, "int");
        pd2.setLookup("lists", listName, getCurrentContainer());

        List<PropertyDescriptor> fields = new ArrayList<>();
        fields.add(pd1);
        fields.add(pd2);

        DomainResponse domainResponse = createDomain(domainKind, domainName, description, fields);
        int domainId = domainResponse.getDomain().getDomainId().intValue();
        String domainProperty = domainName + domainId;

        String vocabDomainPropURI1 = domainResponse.getDomain().getFields().get(0).getPropertyURI();
        String vocabDomainPropURI2 = domainResponse.getDomain().getFields().get(1).getPropertyURI();

        Run run = new Run();
        run.setName("ViewSupportRun");
        run.setProperties(Map.of(vocabDomainPropURI1, propValueLab, vocabDomainPropURI2, listRow1RowId));

        SaveAssayRunsCommand saveAssayRunsCommand = new SaveAssayRunsCommand(SaveAssayBatchCommand.SAMPLE_DERIVATION_PROTOCOL, List.of(run));
        SaveAssayRunsResponse saveAssayRunsResponse = saveAssayRunsCommand.execute(createDefaultConnection(), getProjectName());

        log("Go to runs grid");
        goToProjectHome();
        goToSchemaBrowser();
        DataRegionTable runsTable = viewQueryData("exp","Runs");
        CustomizeView runsTableCustomizeView = runsTable.openCustomizeGrid();
        runsTableCustomizeView.showHiddenItems();
        runsTableCustomizeView.addColumn("Properties");
        runsTableCustomizeView.addColumn(domainProperty + "/" + propNameLab);
        runsTableCustomizeView.addColumn(domainProperty + "/" + propNameLocation);
        runsTableCustomizeView.applyCustomView();

        String propertiesValue = propNameLab + "\n" + propNameLocation + "\n" +
                propValueLab + " " + labLocation;
        List<String> rowData = runsTable.getRowDataAsText(0);

        Assert.assertTrue("Run does not contain properties property value.", rowData.contains(propertiesValue));

        Assert.assertEquals("Run does not contain " + propNameLab + " vocabulary property.", runsTable.getColumnDataAsText(domainProperty + "/" + propNameLab).get(0), propValueLab);
        Assert.assertEquals("Run does not contain " + propNameLab + " vocabulary property.", runsTable.getColumnDataAsText(domainProperty + "/" + propNameLocation).get(0), labLocation);
    }

    private void createListForVocabPropertyLookup(String listName)
    {
        log("Create a list for vocabulary property lookup");
        ListHelper.ListColumn[] columns = new ListHelper.ListColumn[] {
                new ListHelper.ListColumn("name", "Name", ListHelper.ListColumnType.String, "")
        };

        _listHelper.createList(getProjectName(), listName, ListHelper.ListColumnType.AutoInteger, "Key", columns);
        goToManageLists();
    }

}
