package org.labkey.test.tests.experiment;

import org.jetbrains.annotations.Nullable;
import org.labkey.remoteapi.CommandException;
import org.labkey.remoteapi.Connection;
import org.labkey.remoteapi.domain.CreateDomainCommand;
import org.labkey.remoteapi.domain.DomainResponse;
import org.labkey.remoteapi.domain.GetDomainCommand;
import org.labkey.remoteapi.domain.PropertyDescriptor;
import org.labkey.remoteapi.query.SelectRowsCommand;
import org.labkey.remoteapi.query.SelectRowsResponse;
import org.labkey.remoteapi.query.Sort;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.TestFileUtils;
import org.labkey.test.pages.ReactAssayDesignerPage;
import org.labkey.test.params.FieldDefinition;
import org.labkey.test.params.experiment.SampleTypeDefinition;
import org.labkey.test.util.SampleTypeHelper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public abstract class ProvenanceAssayHelper extends BaseWebDriverTest
{
    protected static final String PROVENANCE_DATA_FILE = "AssayImportProvenanceRun.xls";

    protected String uploadFile(String fileName) throws IOException, CommandException
    {
        goToModule("FileContent");
        File datFile = TestFileUtils.getSampleData(PROVENANCE_DATA_FILE);
        _fileBrowserHelper.uploadFile(datFile);
        SelectRowsCommand selectCmd = new SelectRowsCommand("exp", "Data");
        selectCmd.setColumns(List.of("LSID"));
        SelectRowsResponse selResp = selectCmd.execute(createDefaultConnection(), getProjectName());
        Map<String, Object> dataTableRow = selResp.getRows().get(0);
        return dataTableRow.get("LSID").toString();
    }

    protected void createSampleType(String typeName)
    {
        log("Creating an input sample type");
        goToModule("Experiment");
        SampleTypeHelper sampleHelper = new SampleTypeHelper(this);
        sampleHelper.createSampleType(new SampleTypeDefinition(typeName).setFields(
                List.of(new FieldDefinition("Field1", FieldDefinition.ColumnType.String))),
                "Name\tField1\n" +
                        "Sample1\tsome value\n" +
                        "Sample2\tsome other value\n" +
                        "Sample3\tvalueness\n" +
                        "Sample4\tinvaluable\n");

    }

    protected void createSimpleAssay(String assayName, @Nullable String sampleTypeName)
    {
        log("Creating a simple assay.");
        ReactAssayDesignerPage assayDesignerPage = _assayHelper.createAssayDesign("General", assayName);

        assayDesignerPage.setEditableResults(true);
        assayDesignerPage.setEditableRuns(true);

        if (sampleTypeName != null)
        {
            assayDesignerPage.goToResultsFields().addField(new FieldDefinition("inputSample", new FieldDefinition.LookupInfo(null, "samples", sampleTypeName)));
        }

        assayDesignerPage.clickFinish();
    }

    protected void populateAssay(String assayName, String runName, String runData)
    {
        log("Populate assay with data rows having provenance inputs.");
        clickAndWait(Locator.linkWithText(assayName));
        waitForElement(Locator.lkButton("Import Data"));
        clickAndWait(Locator.lkButton("Import Data"));
        waitForElement(Locator.tagWithName("select", "targetStudy"));
        clickAndWait(Locator.lkButton("Next"));

        setFormElement(Locator.name("name"), runName);
        click(Locator.xpath("//input[@value='textAreaDataProvider']"));
        setFormElement(Locator.id("TextAreaDataCollector.textArea"), runData);
        clickAndWait(Locator.lkButton("Save and Finish"));
    }

    protected DomainResponse createDomain(String domainKind, String domainName, String description, List<PropertyDescriptor> fields) throws IOException, CommandException
    {
        CreateDomainCommand domainCommand = new CreateDomainCommand(domainKind, domainName);
        domainCommand.getDomainDesign().setFields(fields);
        domainCommand.getDomainDesign().setDescription(description);

        DomainResponse domainResponse = domainCommand.execute(createDefaultConnection(), getProjectName());
        GetDomainCommand getDomainCommand = new GetDomainCommand(domainResponse.getDomain().getDomainId());
        return getDomainCommand.execute(createDefaultConnection(), getProjectName());
    }

    protected void createAndUploadAssayData() throws IOException, CommandException
    {
        String runData = "participantID\tinputSample\tprov:objectInputs" + "\n" +
                "P100\tSample1\t" + uploadFile(PROVENANCE_DATA_FILE);

        String assayName = "Provenance Assay";
        String runName = "ProvenanceAssayRun";
        String sampleTypeName = "ProvenanceSampleType";

        createSampleType(sampleTypeName);
        goToProjectHome(getProjectName());
        createSimpleAssay(assayName, sampleTypeName);
        goToProjectHome(getProjectName());
        populateAssay(assayName, runName, runData);
    }

    protected List<String> getInputLsid(Connection cn) throws IOException, CommandException
    {
        SelectRowsCommand selectCmd = new SelectRowsCommand("exp", "Materials");
        selectCmd.setColumns(List.of("LSID"));
        selectCmd.setSorts(List.of(new Sort("LSID")));
        SelectRowsResponse selResp = selectCmd.execute(cn, getProjectName());
        List<String> resultLsids = new ArrayList<>();

        // TODO: look why this returns [lsid],[lsid] and not lsid, lsid
        // return materialsRows.stream().map(Map::values).map(Object::toString).collect(Collectors.toList());

        selResp.getRows().forEach(materialRow -> materialRow.forEach((key, value) -> resultLsids.add(value.toString())));

        return resultLsids;
    }

    protected String getResultRowLsid(Connection cn) throws IOException, CommandException
    {
        SelectRowsCommand cmd = new SelectRowsCommand("assay.General.Provenance Assay", "Data");
        cmd.setColumns(List.of("LSID"));
        SelectRowsResponse response = cmd.execute(cn, getProjectName());
        Map<String, Object> resultRow = response.getRows().get(0);
        return resultRow.get("LSID").toString();
    }
}
