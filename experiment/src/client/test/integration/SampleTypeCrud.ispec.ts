import { ExperimentCRUDUtils, hookServer, RequestOptions, selectRandomN, successfulResponse } from '@labkey/test';
import mock from 'mock-fs';
import {
    checkDomainName,
    checkLackDesignerOrReaderPerm,
    createSample,
    deleteSampleType,
    getSampleTypeRowIdByName,
    initProject,
    verifyRequiredLineageInsertUpdate
} from './utils';
import { caseInsensitive, SAMPLE_TYPE_DESIGNER_ROLE } from '@labkey/components';
const { importSample, insertRows } = ExperimentCRUDUtils;

const server = hookServer(process.env);
const PROJECT_NAME = 'SampleTypeCrudJestProject';

const SAMPLE_ALIQUOT_IMPORT_TYPE_NAME = "SampleType_Aliquots_Import";
const SAMPLE_ALIQUOT_REQ_IMPORT_TYPE_NAME = "Aliquot_Import_RequiredProp";
const SAMPLE_ALIQUOT_IMPORT_NO_NAME_PATTERN_NAME = "SampleType_Aliquots_Import_NoExpression";
let aliquotImportDomain, aliquotReqImportDomain, aliquotNoExpressionImportDomain;

let readerUser, readerUserOptions;
let editorUser, editorUserOptions;
let designer, designerOptions;
let designerReader, designerReaderOptions;
let designerEditor, designerEditorOptions;
let admin, adminOptions;

let topFolderOptions: RequestOptions;
let subfolder1Options;
let subfolder2Options;

async function getSampleTypeRowId(sampleType: string, folderOptions: RequestOptions) {
    return getSampleTypeRowIdByName(server, sampleType, folderOptions);
}

async function createASample(sampleTypeName: string, sampleName: string, folderOptions: RequestOptions) {
    return createSample(server, sampleTypeName, sampleName, folderOptions, editorUserOptions);
}

async function deleteSampleTypeByRowId(sampleTypeRowId: number, folderOptions: RequestOptions, userOptions: RequestOptions) {
    return deleteSampleType(server, sampleTypeRowId, folderOptions, userOptions);
}

beforeAll(async () => {
    const options = await initProject(server, PROJECT_NAME, SAMPLE_TYPE_DESIGNER_ROLE);

    topFolderOptions = options.topFolderOptions;
    subfolder1Options = options.subfolder1Options;
    subfolder2Options = options.subfolder2Options;
    readerUser = options.readerUser;
    readerUserOptions = options.readerUserOptions;

    editorUser = options.editorUser;
    editorUserOptions = options.editorUserOptions;

    designer = options.designer;
    designerOptions = options.designerOptions;

    designerReader = options.designerReader;
    designerReaderOptions = options.designerReaderOptions;

    designerEditor = options.designerEditor;
    designerEditorOptions = options.designerReaderOptions;

    admin = options.admin;
    adminOptions = options.adminOptions;

    const fields = [
        { name: 'str', required: false},
        { name: 'int', rangeURI: 'http://www.w3.org/2001/XMLSchema#int'},
        { name: 'Myparentcol'},
        { name: 'Myaliquotcol', derivationDataScope: 'ChildOnly'},
        { name: 'Myindependentcol', derivationDataScope: 'All'},
        { name: 'Name' },
    ];
    let createPayload = {
        kind: 'SampleSet',
        domainDesign: {
            name: SAMPLE_ALIQUOT_IMPORT_TYPE_NAME,
            fields,
        },
        options: {
            name: SAMPLE_ALIQUOT_IMPORT_TYPE_NAME,
            aliquotNameExpression: "${${AliquotedFrom}-:withCounter}",
            nameExpression: "SAI_${genId}",
            metricUnit: 'mL'
        }
    };
    await server.post('property', 'createDomain', createPayload, {...topFolderOptions, ...designerReaderOptions}).expect((result) => {
        aliquotImportDomain = JSON.parse(result.text);
        return true;
    });

    createPayload = {
        kind: 'SampleSet',
        domainDesign: {
            name: SAMPLE_ALIQUOT_IMPORT_NO_NAME_PATTERN_NAME,
            fields,
        },
        options: {
            name: SAMPLE_ALIQUOT_IMPORT_NO_NAME_PATTERN_NAME,
            aliquotNameExpression: "",
            nameExpression: "",
            metricUnit: 'g'
        }
    };
    await server.post('property', 'createDomain', createPayload, {...topFolderOptions, ...designerReaderOptions}).expect((result) => {
        aliquotNoExpressionImportDomain = JSON.parse(result.text);
        return true;
    });

    fields.push({name: 'Requiredprops', required: true});
    createPayload = {
        kind: 'SampleSet',
        domainDesign: {
            name: SAMPLE_ALIQUOT_REQ_IMPORT_TYPE_NAME,
            fields
        },
        options: {
            name: SAMPLE_ALIQUOT_REQ_IMPORT_TYPE_NAME, // test aliquot expression without sample expression
            aliquotNameExpression: "${${MaterialInputs/" + SAMPLE_ALIQUOT_REQ_IMPORT_TYPE_NAME + "}-:withCounter}", // test parent input as aliquot expression
            nameExpression: "",
            metricUnit: 'mL'
        }
    };
    await server.post('property', 'createDomain', createPayload, {...topFolderOptions, ...designerReaderOptions}).expect((result) => {
        aliquotReqImportDomain = JSON.parse(result.text);
        return true;
    });
});

afterAll(async () => {
    return server.teardown();
});

afterEach(() => {
    mock.restore();
});

describe('Sample Type Designer', () => {
    it('Lack designer or Reader permission', async () => {
        await checkLackDesignerOrReaderPerm(server, 'SampleSet', topFolderOptions, readerUserOptions, editorUserOptions, designerOptions);
    });

    describe('Create/update/delete designs', () => {
        it('Sample type name and field name validation', async () => {
            await checkDomainName(server, 'SampleSet', true, topFolderOptions, designerReaderOptions);
        });

        it('Designer can create, update and delete empty design, reader and editors cannot create/update/delete design', async () => {
            const sampleType = "ToDelete";
            let domainId = -1, domainURI = '';
            const createPayload = {
                kind: 'SampleSet',
                domainDesign: { name: sampleType, fields: [{ name: 'Name' }] },
                options: {
                    name: sampleType,
                    importAliases: {
                        'legacy': 'materialInputs/' + sampleType,
                        'newAlias': {
                            inputType: 'materialInputs/' + sampleType,
                            required: false,
                        }
                    }

                }
            };

            await server.post('property', 'createDomain', createPayload, {...topFolderOptions, ...readerUserOptions}).expect(403);
            await server.post('property', 'createDomain', createPayload, {...topFolderOptions, ...editorUserOptions}).expect(403);

            await server.post('property', 'createDomain', createPayload, {...topFolderOptions, ...designerReaderOptions}).expect((result) => {
                const domain = JSON.parse(result.text);
                domainId = domain.domainId;
                domainURI = domain.domainURI;
                return true;
            });

            const sampleTypeRowId = await getSampleTypeRowId(sampleType, topFolderOptions);

            const updatePayload = {
                domainId,
                domainDesign: { name: sampleType, domainId, domainURI },
                options: {
                    rowId: sampleTypeRowId,
                    name: sampleType,
                    metricUnit: "mg",
                    importAliases: {
                        'legacyupdate': 'materialInputs/' + sampleType,
                        'newAliasUpdate': {
                            inputType: 'materialInputs/' + sampleType,
                            required: false,
                        }
                    }
                }
            };

            await server.post('property', 'saveDomain', updatePayload, {...topFolderOptions, ...readerUserOptions}).expect(403);
            await server.post('property', 'saveDomain', updatePayload, {...topFolderOptions, ...editorUserOptions}).expect(403);
            await server.post('property', 'saveDomain', updatePayload, {...topFolderOptions, ...designerReaderOptions}).expect(successfulResponse);

            let deleteResult = await deleteSampleTypeByRowId(sampleTypeRowId, topFolderOptions, readerUserOptions);
            expect(deleteResult.status).toEqual(403);
            deleteResult = await deleteSampleTypeByRowId(sampleTypeRowId, topFolderOptions, editorUserOptions);
            expect(deleteResult.status).toEqual(403);

            deleteResult = await deleteSampleTypeByRowId(sampleTypeRowId, topFolderOptions, designerReaderOptions);
            expect(deleteResult.status).toEqual(302);

            const removedSampleType = await getSampleTypeRowId(sampleType, topFolderOptions);

            expect(removedSampleType).toEqual(0);
        });

        it('Designer can update non-empty design but cannot delete non-empty design, admin can delete non-empty design', async () => {
            const sampleType = "FailedDelete";
            let domainId = -1, domainURI = '';
            await server.post('property', 'createDomain', {
                kind: 'SampleSet',
                domainDesign: { name: sampleType, fields: [{ name: 'Name' }] },
                options: {
                    name: sampleType,
                }
            }, {...topFolderOptions, ...designerReaderOptions}).expect((result) => {
                const domain = JSON.parse(result.text);
                domainId = domain.domainId;
                domainURI = domain.domainURI;
                return true;
            });

            const sampleTypeRowId = await getSampleTypeRowId(sampleType, topFolderOptions);

            // create samples in child folder
            const createdSampleId = await createASample(sampleType, 'SampleData1', subfolder1Options);
            expect(createdSampleId === 0).toBeFalsy();

            const updatedDomainPayload = {
                domainId,
                domainDesign: { name: sampleType, domainId, domainURI },
                options: {
                    name: sampleType,
                    metricUnit: "kg",
                    importAliases: {
                        'legacyupdate': 'materialInputs/' + sampleType,
                        'newAliasUpdate': {
                            inputType: 'materialInputs/' + sampleType,
                            required: false,
                        }
                    }
                }
            };

            await server.post('property', 'saveDomain', updatedDomainPayload, {...topFolderOptions, ...designerReaderOptions}).expect(successfulResponse);

            // verify cannot add a required import alias with existing data that don't have such parent
            updatedDomainPayload.options.importAliases = {
                'legacyupdate': 'MaterialInputs/' + sampleType,
                'newAliasUpdate': {
                    inputType: 'MaterialInputs/' + sampleType, // verify MaterialInputs/ and materialInputs/
                    required: true,
                }
            };

            let requiredNotAllowedResp = await server.post('property', 'saveDomain', updatedDomainPayload, {...topFolderOptions, ...designerReaderOptions});
            expect(requiredNotAllowedResp['body']['success']).toBeFalsy();
            expect(requiredNotAllowedResp['body']['exception']).toBe("'FailedDelete' cannot be required as a parent type when there are existing samples without a parent of this type.");

            updatedDomainPayload.options.importAliases = {
                'legacyupdate': 'materialInputs/' + sampleType,
                'newAliasUpdate': {
                    inputType: 'materialInputs/' + sampleType,
                    required: true,
                }
            };
            requiredNotAllowedResp = await server.post('property', 'saveDomain', updatedDomainPayload, {...topFolderOptions, ...designerReaderOptions});
            expect(requiredNotAllowedResp['body']['success']).toBeFalsy();
            expect(requiredNotAllowedResp['body']['exception']).toBe("'FailedDelete' cannot be required as a parent type when there are existing samples without a parent of this type.");

            // verify data exist in child prevent designer from delete design
            let deleteResult = await deleteSampleTypeByRowId(sampleTypeRowId, topFolderOptions, designerReaderOptions);
            expect(deleteResult.status).toEqual(403);
            deleteResult = await deleteSampleTypeByRowId(sampleTypeRowId, topFolderOptions, readerUserOptions);
            expect(deleteResult.status).toEqual(403);
            deleteResult = await deleteSampleTypeByRowId(sampleTypeRowId, topFolderOptions, editorUserOptions);
            expect(deleteResult.status).toEqual(403);

            let failedRemovedSampleType = await getSampleTypeRowId(sampleType, topFolderOptions);
            expect(failedRemovedSampleType).toEqual(sampleTypeRowId);

            // create more samples in top folder
            await createASample(sampleType, 'SampleData2', {...topFolderOptions, ...editorUserOptions});

            // verify data exist in Top prevent designer from delete design
            deleteResult = await deleteSampleTypeByRowId(sampleTypeRowId, topFolderOptions, designerEditorOptions);
            expect(deleteResult.status).toEqual(403);
            deleteResult = await deleteSampleTypeByRowId(sampleTypeRowId, topFolderOptions, readerUserOptions);
            expect(deleteResult.status).toEqual(403);
            deleteResult = await deleteSampleTypeByRowId(sampleTypeRowId, topFolderOptions, editorUserOptions);
            expect(deleteResult.status).toEqual(403);

            failedRemovedSampleType = await getSampleTypeRowId(sampleType, topFolderOptions);
            expect(failedRemovedSampleType).toEqual(sampleTypeRowId);

            //admin can delete design with data
            deleteResult = await deleteSampleTypeByRowId(sampleTypeRowId, topFolderOptions, adminOptions);
            expect(deleteResult.status).toEqual(302);

            const removedSampleType = await getSampleTypeRowId(sampleType, topFolderOptions);
            expect(removedSampleType).toEqual(0);
        });
    });

});

describe('Import with update / merge', () => {
    it ("Issue 52922: Blank sample id in the file are getting ignored in update from file", async () => {
        const BLANK_KEY_UPDATE_ERROR = 'Name value not provided';
        const BLANK_KEY_MERGE_ERROR_NO_EXPRESSION = 'SampleID or Name is required for sample';
        const BOGUS_KEY_UPDATE_ERROR = 'Sample does not exist: bogus.';
        const CROSS_FOLDER_UPDATE_NOT_SUPPORTED_ERROR = "Sample does not belong to ";

        const dataType = SAMPLE_ALIQUOT_IMPORT_NO_NAME_PATTERN_NAME;
        const dataName = "Data1";
        await ExperimentCRUDUtils.insertRows(server, [{
            name: dataName,
            description: 'created'
        }], 'samples', dataType, topFolderOptions, editorUserOptions);

        // Issue 52922: Blank / bogus  id in the file are getting ignored in update from file
        let blankKeyProvidedError = await ExperimentCRUDUtils.importSample(server, "Name\tDescription\nData1\tNotblank\n\tisBlank", dataType, "UPDATE", topFolderOptions, editorUserOptions);
        expect(blankKeyProvidedError.text.indexOf(BLANK_KEY_UPDATE_ERROR) > -1).toBeTruthy();
        blankKeyProvidedError = await ExperimentCRUDUtils.importSample(server, "Name\tDescription\nData1\tNotblank\n\tisBlank", dataType, "UPDATE", subfolder1Options, editorUserOptions);
        expect(blankKeyProvidedError.text.indexOf(BLANK_KEY_UPDATE_ERROR) > -1).toBeTruthy();
        blankKeyProvidedError = await ExperimentCRUDUtils.importSample(server, "Name\tDescription\n\tisBlank", dataType, "UPDATE", topFolderOptions, editorUserOptions);
        expect(blankKeyProvidedError.text.indexOf(BLANK_KEY_UPDATE_ERROR) > -1).toBeTruthy();
        blankKeyProvidedError = await ExperimentCRUDUtils.importSample(server, "Name\tDescription\nData1\tNotblank\n\tisBlank", dataType, "MERGE", topFolderOptions, editorUserOptions);
        expect(blankKeyProvidedError.text.indexOf(BLANK_KEY_MERGE_ERROR_NO_EXPRESSION) > -1).toBeTruthy();
        blankKeyProvidedError = await ExperimentCRUDUtils.importSample(server, "Name\tDescription\nData1\tNotblank\n\tisBlank", dataType, "MERGE", subfolder1Options, editorUserOptions);
        expect(blankKeyProvidedError.text.indexOf(BLANK_KEY_MERGE_ERROR_NO_EXPRESSION) > -1).toBeTruthy();
        blankKeyProvidedError = await ExperimentCRUDUtils.importSample(server, "Name\tDescription\n\tisBlank", dataType, "MERGE", topFolderOptions, editorUserOptions);
        expect(blankKeyProvidedError.text.indexOf(BLANK_KEY_MERGE_ERROR_NO_EXPRESSION) > -1).toBeTruthy();
        // bogus name
        let bogusKeyProvidedError = await ExperimentCRUDUtils.importSample(server, "Name\tDescription\nbogus\tisBogus", dataType, "UPDATE", topFolderOptions, editorUserOptions);
        expect(bogusKeyProvidedError.text.indexOf(BOGUS_KEY_UPDATE_ERROR) > -1).toBeTruthy();
        bogusKeyProvidedError = await ExperimentCRUDUtils.importSample(server, "Name\tDescription\nData1\tNotblank\nbogus\tisBogus", dataType, "UPDATE", topFolderOptions, editorUserOptions);
        expect(bogusKeyProvidedError.text.indexOf(BOGUS_KEY_UPDATE_ERROR) > -1).toBeTruthy();

        const dataTypeWithExpression = SAMPLE_ALIQUOT_IMPORT_TYPE_NAME;
        await ExperimentCRUDUtils.insertRows(server, [{
            name: dataName,
            description: 'created'
        }], 'samples', dataTypeWithExpression, topFolderOptions, editorUserOptions);

        // blank name
        blankKeyProvidedError = await ExperimentCRUDUtils.importSample(server, "Name\tDescription\nData1\tNotblank\n\tisBlank", dataTypeWithExpression, "UPDATE", topFolderOptions, editorUserOptions);
        expect(blankKeyProvidedError.text.indexOf(BLANK_KEY_UPDATE_ERROR) > -1).toBeTruthy();
        blankKeyProvidedError = await ExperimentCRUDUtils.importSample(server, "Name\tDescription\nData1\tNotblank\n\tisBlank", dataTypeWithExpression, "UPDATE", subfolder1Options, editorUserOptions);
        expect(blankKeyProvidedError.text.indexOf(BLANK_KEY_UPDATE_ERROR) > -1).toBeTruthy();
        blankKeyProvidedError = await ExperimentCRUDUtils.importSample(server, "Name\tDescription\n\tisBlank", dataTypeWithExpression, "UPDATE", topFolderOptions, editorUserOptions);
        expect(blankKeyProvidedError.text.indexOf(BLANK_KEY_UPDATE_ERROR) > -1).toBeTruthy();

        // merge with blank name for data type with naming expression should not fail
        let successResp = await ExperimentCRUDUtils.importSample(server, "Name\tDescription\nData1\tNotblank\n\tisBlank", dataTypeWithExpression, "MERGE", topFolderOptions, editorUserOptions);
        expect(successResp.text.indexOf('"success" : true') > -1).toBeTruthy();
        successResp = await ExperimentCRUDUtils.importSample(server, "Name\tDescription\n\tisBlank", dataTypeWithExpression, "MERGE", topFolderOptions, editorUserOptions);
        expect(successResp.text.indexOf('"success" : true') > -1).toBeTruthy();
        successResp = await ExperimentCRUDUtils.importSample(server, "Name\tDescription\n\tisBlank", dataTypeWithExpression, "MERGE", subfolder1Options, editorUserOptions);
        expect(successResp.text.indexOf('"success" : true') > -1).toBeTruthy();

        // cross folder update not supported when folder type is "Collaboration"
        let crossFolderUpdateErrorResp = await ExperimentCRUDUtils.importSample(server, "Name\tDescription\nData1\tNotblank", dataTypeWithExpression, "UPDATE", subfolder1Options, editorUserOptions);
        expect(crossFolderUpdateErrorResp.text.indexOf(CROSS_FOLDER_UPDATE_NOT_SUPPORTED_ERROR) > -1).toBeTruthy();
        let crossFolderMergeErrorResp = await ExperimentCRUDUtils.importSample(server, "Name\tDescription\nData1\tNotblank\n\tisBlank", dataTypeWithExpression, "MERGE", subfolder1Options, editorUserOptions);
        expect(crossFolderMergeErrorResp.text.indexOf(CROSS_FOLDER_UPDATE_NOT_SUPPORTED_ERROR) > -1).toBeTruthy();

        // bogus name
        bogusKeyProvidedError = await ExperimentCRUDUtils.importSample(server, "Name\tDescription\nbogus\tisBogus", dataTypeWithExpression, "UPDATE", topFolderOptions, editorUserOptions);
        expect(bogusKeyProvidedError.text.indexOf(BOGUS_KEY_UPDATE_ERROR) > -1).toBeTruthy();
        bogusKeyProvidedError = await ExperimentCRUDUtils.importSample(server, "Name\tDescription\nData1\tNotblank\nbogus\tisBogus", dataTypeWithExpression, "UPDATE", topFolderOptions, editorUserOptions);
        expect(bogusKeyProvidedError.text.indexOf(BOGUS_KEY_UPDATE_ERROR) > -1).toBeTruthy();

        // merge with bogus name should create a new data and not fail
        successResp = await ExperimentCRUDUtils.importSample(server, "Name\tDescription\nData1\tNotblank\nbogusShouldCreate\tisBogus", dataTypeWithExpression, "MERGE", topFolderOptions, editorUserOptions);
        expect(successResp.text.indexOf('"success" : true') > -1).toBeTruthy();

    });
    it('Support RowId lookup and renaming', async () => {
        const dataType = SAMPLE_ALIQUOT_IMPORT_NO_NAME_PATTERN_NAME;
        const initialName = 'RowIdLookupTest';
        const newName = 'RenamedViaRowId';

        const rows = await insertRows(server, [{
            name: initialName,
            description: 'Original Description'
        }], 'samples', dataType, topFolderOptions, editorUserOptions);

        // Capture the generated RowId from the insert response
        // Note: Assuming standard response structure where 'rows' array contains the returned data
        const rowId = caseInsensitive(rows[0], 'rowId');
        expect(rowId).toBeDefined();

        // Test: Update Description using ONLY RowId (Name column omitted)
        // This validates that the importer can lookup by RowId alone
        let updateTsv = `RowId\tDescription\n${rowId}\tUpdated Description via RowId`;
        let resp = await importSample(server, updateTsv, dataType, 'UPDATE', topFolderOptions, editorUserOptions);
        expect(resp.text.indexOf('"success" : true') > -1).toBeTruthy();

        // Test: Update Name using RowId + Name (Renaming)
        // This validates that providing both keys looks up by RowId and updates the Name
        updateTsv = `RowId\tName\n${rowId}\t${newName}`;
        resp = await importSample(server, updateTsv, dataType, 'UPDATE', topFolderOptions, editorUserOptions);
        expect(resp.text.indexOf('"success" : true') > -1).toBeTruthy();

        // Verify Rename: Attempt to update using the NEW Name
        // If the rename worked, looking up by the new Name should succeed
        updateTsv = `Name\tDescription\n${newName}\tDescription after rename`;
        resp = await importSample(server, updateTsv, dataType, 'UPDATE', topFolderOptions, editorUserOptions);
        expect(resp.text.indexOf('"success" : true') > -1).toBeTruthy();

        // Verify Rename: Attempt to update using the OLD Name
        // This should now fail because the record was renamed, proving the old key is gone
        updateTsv = `Name\tDescription\n${initialName}\tShould fail`;
        resp = await importSample(server, updateTsv, dataType, 'UPDATE', topFolderOptions, editorUserOptions);
        expect(resp.text.indexOf('Sample does not exist') > -1).toBeTruthy();
    });
    it('Error when supplying RowId during MERGE', async () => {
        const dataType = SAMPLE_ALIQUOT_IMPORT_NO_NAME_PATTERN_NAME;
        const sampleName = 'MergeRowIdErrorTest';

        const rows = await insertRows(server, [{
            name: sampleName,
            description: 'created'
        }], 'samples', dataType, topFolderOptions, editorUserOptions);

        const rowId = caseInsensitive(rows[0], 'rowId');
        expect(rowId).toBeDefined();

        // MERGE with RowId should fail
        // Even if the name matches and rowId is correct, the presence of the column should trigger the error
        const mergeTsv = `RowId\tName\tDescription\n${rowId}\t${sampleName}\tShould fail`;
        const resp = await importSample(server, mergeTsv, dataType, 'MERGE', topFolderOptions, editorUserOptions);

        // Check for the specific error message
        expect(resp.text.indexOf('RowId is not accepted when merging samples. Specify only the sample name instead.') > -1).toBeTruthy();
    });
    it('Error when supplying LSID without RowId or Name', async () => {
        const dataType = SAMPLE_ALIQUOT_IMPORT_NO_NAME_PATTERN_NAME;
        const sampleName = 'LsidKeyErrorTest';
        const LSID_UPDATE_ERROR = "LSID is no longer accepted as a key for sample update. Specify a RowId or Name instead.";
        const LSID_MERGE_ERROR = "LSID is no longer accepted as a key for sample merge. Specify a RowId or Name instead.";

        const rows = await insertRows(server, [{
            name: sampleName,
            description: 'created'
        }], 'samples', dataType, topFolderOptions, editorUserOptions);

        const lsid = caseInsensitive(rows[0], 'lsid');
        expect(lsid).toBeDefined();

        // UPDATE: LSID provided as key (Name/RowId missing)
        const tsv = `LSID\tDescription\n${lsid}\tShould fail`;
        let resp = await importSample(server, tsv, dataType, 'UPDATE', topFolderOptions, editorUserOptions);
        expect(resp.text.indexOf(LSID_UPDATE_ERROR) > -1).toBeTruthy();

        // MERGE: LSID provided as key (Name/RowId missing)
        resp = await importSample(server, tsv, dataType, 'MERGE', topFolderOptions, editorUserOptions);
        expect(resp.text.indexOf(LSID_MERGE_ERROR) > -1).toBeTruthy();
    });
    it('Cross-type update should not be accepted', async () => {
        // Arrange
        const firstSampleType = SAMPLE_ALIQUOT_IMPORT_TYPE_NAME;
        const secondSampleType = SAMPLE_ALIQUOT_IMPORT_NO_NAME_PATTERN_NAME;
        const [firstSample] = await insertRows(server, [{ name: 'FL-1', description: 'Yolo' }], 'samples', firstSampleType, topFolderOptions, editorUserOptions);
        const [secondSample] = await insertRows(server, [{ name: 'SP-10', description: 'Hello' }], 'samples', secondSampleType, topFolderOptions, editorUserOptions);
        const firstRowId = caseInsensitive(firstSample, 'rowId');
        const secondRowId = caseInsensitive(secondSample, 'rowId');

        let tsv = 'RowId\tSampleType\tDescription\n';
        tsv += `${firstRowId}\t${firstSampleType}\tShould be FL-1\n`;
        tsv += `${secondRowId}\t${secondSampleType}\tShould be SP-10\n`;

        // Act
        const resp = await importSample(server, tsv, firstSampleType, 'UPDATE', topFolderOptions, editorUserOptions);

        // Assert
        // Verify that these rows are not updated
        expect(resp.body.success).toEqual(false);
        expect(resp.body.exception).toContain('Sample does not exist: (RowId)');
    })
});

describe('Aliquot crud', () => {
    describe("SMAliquotImportExportTest", () => {
        const aliquotQueryCols = 'name, rowid, lsid, description, str, int, isAliquot, AliquotedFromLsid/name, rootmaterialrowid, Myparentcol, Myaliquotcol, Myindependentcol';

        async function verifyImportingWithNameValue(parentSampleName: string, sampleType: string) {
            const parentInsertRow = {
                name: parentSampleName,
                description: 'testImportingWithNameValue parent'
            }
            const parentSampleRows = await ExperimentCRUDUtils.insertSamples(server, [parentInsertRow], sampleType, topFolderOptions, editorUserOptions);
            const parentSampleRow = parentSampleRows[0];
            const parentSampleRowId = caseInsensitive(parentSampleRow, 'RowId');

            const aliquot1Name = parentSampleName + "-101";
            const formattedDescription = "Formatted aliquot name but with different index.";

            const aliquot2Name = "John_Galt";
            const specialDescription = "Aliquot with a 'non-traditional' name.";

            // Because importing is batched, the auto-naming of the aliquot will start with 1.
            const aliquot3Name = parentSampleName + "-1";
            const aliDescription = "Simple aliquot.";

            let importText = "Name\tDescription\tAliquotedFrom\n";
            importText += aliquot1Name + "\t" + formattedDescription + "\t" + parentSampleName + "\n";
            importText += aliquot2Name + "\t" + specialDescription + "\t" + parentSampleName + "\n";
            importText += /*blank name*/"\t" + aliDescription + "\t" + parentSampleName + "\n";
            await ExperimentCRUDUtils.importSample(server, importText, sampleType, 'IMPORT', topFolderOptions, editorUserOptions);
            const parentDataAfterAliquot = await ExperimentCRUDUtils.getSamplesData(server, [parentSampleRowId], sampleType, 'aliquotcount', topFolderOptions, editorUserOptions);
            expect(caseInsensitive(parentDataAfterAliquot[0], 'aliquotcount')).toEqual(3);
            const aliquots = await ExperimentCRUDUtils.getAliquotsByRootId(server, parentSampleRowId, sampleType, aliquotQueryCols, topFolderOptions, readerUserOptions);
            aliquots.sort((a, b) => {
                return caseInsensitive(a, 'rowId') - caseInsensitive(b, 'rowId');
            })
            expect(aliquots.length).toEqual(3);
            expect(caseInsensitive(aliquots[0], 'name')).toEqual(aliquot1Name);
            expect(caseInsensitive(aliquots[0], 'description')).toEqual(formattedDescription);
            expect(caseInsensitive(aliquots[1], 'name')).toEqual(aliquot2Name);
            expect(caseInsensitive(aliquots[1], 'description')).toEqual(specialDescription);
            expect(caseInsensitive(aliquots[2], 'name')).toEqual(aliquot3Name);
            expect(caseInsensitive(aliquots[2], 'description')).toEqual(aliDescription);

            // testImportWithUpdate
            const updatedDescriptionUsingMerge = 'Why did the chicken cross the road?';
            importText = "Name\tDescription\tAliquotedFrom\n";
            importText += aliquot1Name + "\t" + updatedDescriptionUsingMerge + "\t" + parentSampleName + "\n";
            await ExperimentCRUDUtils.importSample(server, importText, sampleType, 'MERGE', topFolderOptions, editorUserOptions);
            const parentDataAfterAliquotMerge = await ExperimentCRUDUtils.getSamplesData(server, [parentSampleRowId], sampleType, 'aliquotcount', topFolderOptions, editorUserOptions);
            expect(caseInsensitive(parentDataAfterAliquotMerge[0], 'aliquotcount')).toEqual(3);
            const aliquotsAfterMerge = await ExperimentCRUDUtils.getAliquotsByRootId(server, parentSampleRowId, sampleType, aliquotQueryCols, topFolderOptions, readerUserOptions);
            aliquotsAfterMerge.forEach(aliquot => {
                const aliquotName = caseInsensitive(aliquot, 'name');
                const aliquotDescription = caseInsensitive(aliquot, 'description');
                expect(caseInsensitive(aliquot, 'isaliquot')).toBeTruthy();
                if (aliquotName === aliquot1Name)
                    expect(aliquotDescription).toEqual(updatedDescriptionUsingMerge);
                if (aliquotName === aliquot2Name)
                    expect(aliquotDescription).toEqual(specialDescription);
                if (aliquotName === aliquot3Name)
                    expect(aliquotDescription).toEqual(aliDescription);
            })

            // testImportWithUpdate
            /**
             * <p>
             *     Use import to update the description field of an aliquot.
             * </p>
             * <p>
             *     This test will:
             *     <ul>
             *         <li>Import some aliquots (no validation other than success message).</li>
             *         <li>Use import to update description for one aliquot.</li>
             *         <li>Validate that only one aliquot was updated.</li>
             *     </ul>
             * </p>
             */
            const updatedDescriptionUsingUpdate = 'To get to the other side.';
            importText = "Name\tDescription\n";
            importText += aliquot2Name + "\t" + updatedDescriptionUsingUpdate + "\n";
            await ExperimentCRUDUtils.importSample(server, importText, sampleType, 'UPDATE', topFolderOptions, editorUserOptions);
            const parentDataAfterAliquotUpdate = await ExperimentCRUDUtils.getSamplesData(server, [parentSampleRowId], sampleType, 'aliquotcount', topFolderOptions, editorUserOptions);
            expect(caseInsensitive(parentDataAfterAliquotUpdate[0], 'aliquotcount')).toEqual(3);
            const aliquotsAfterUpdate = await ExperimentCRUDUtils.getAliquotsByRootId(server, parentSampleRowId, sampleType, aliquotQueryCols, topFolderOptions, readerUserOptions);
            aliquotsAfterUpdate.forEach(aliquot => {
                const aliquotName = caseInsensitive(aliquot, 'name');
                const aliquotDescription = caseInsensitive(aliquot, 'description');
                expect(caseInsensitive(aliquot, 'isaliquot')).toBeTruthy();
                if (aliquotName === aliquot1Name)
                    expect(aliquotDescription).toEqual(updatedDescriptionUsingMerge);
                if (aliquotName === aliquot2Name)
                    expect(aliquotDescription).toEqual(updatedDescriptionUsingUpdate);
                if (aliquotName === aliquot3Name)
                    expect(aliquotDescription).toEqual(aliDescription);
            })
        }

        /**
         * <p>
         *     Simple import test for aliquots.
         * </p>
         * <p>
         *     This test will:
         *     <ul>
         *         <li>Import two aliquots with only the description and 'Aliquoted From' fields set.</li>
         *         <li>Validate names are as expected.</li>
         *         <li>Validate that the Str and Int fields have the value from the root sample.</li>
         *         <li>Description is as expected.</li>
         *     </ul>
         * </p>
         */
        it('testImportingHappyPath', async () => {
            const parentSampleName = 'parentHappyPath1';
            const parentInsertRow = {
                name: parentSampleName,
                str: 'parentstr',
                int: 121,
                myparentcol: 'parentVal',
                myaliquotcol: 'ignored',
                myindependentcol: 'can override',
                description: 'Happy path import parent'
            }
            const parentSampleRows = await ExperimentCRUDUtils.insertSamples(server, [parentInsertRow], SAMPLE_ALIQUOT_IMPORT_TYPE_NAME, topFolderOptions, editorUserOptions);
            const parentSampleRow = parentSampleRows[0];
            const parentSampleRowId = caseInsensitive(parentSampleRow, 'RowId');
            expect(caseInsensitive(parentSampleRow, 'rootmaterialrowid')).toEqual(parentSampleRowId);
            expect(caseInsensitive(parentSampleRow, 'description')).toEqual(parentInsertRow.description);
            expect(caseInsensitive(parentSampleRow, 'str')).toEqual(parentInsertRow.str);
            expect(caseInsensitive(parentSampleRow, 'int')).toEqual(parentInsertRow.int);
            expect(caseInsensitive(parentSampleRow, 'myparentcol')).toEqual(parentInsertRow.myparentcol);
            expect(caseInsensitive(parentSampleRow, 'myaliquotcol')).toBeNull();
            expect(caseInsensitive(parentSampleRow, 'myindependentcol')).toEqual(parentInsertRow.myindependentcol)
            expect(caseInsensitive(parentSampleRow, 'isaliquot')).toBeFalsy()
            expect(caseInsensitive(parentSampleRow, 'aliquotcount')).toEqual(0);

            let importText = "Description\tAliquotedFrom\n";
            const aliquotDes = 'Happy path import aliquot.';
            for (let i = 0; i < 2; i++)
                importText += aliquotDes + "\t" + parentSampleName + "\n";
            await ExperimentCRUDUtils.importSample(server, importText, SAMPLE_ALIQUOT_IMPORT_TYPE_NAME, 'IMPORT', topFolderOptions, editorUserOptions);
            // verify parent rollup
            const  parentDataAfterAliquot = await ExperimentCRUDUtils.getSamplesData(server, [parentSampleRowId], SAMPLE_ALIQUOT_IMPORT_TYPE_NAME, 'AliquotedFromLsid/name,aliquotcount', topFolderOptions, editorUserOptions);
            expect(caseInsensitive(parentDataAfterAliquot[0], 'AliquotedFromLsid/name')).toBeNull();
            expect(caseInsensitive(parentDataAfterAliquot[0], 'aliquotcount')).toEqual(2);
            // verify aliquot created
            const aliquots = await ExperimentCRUDUtils.getAliquotsByRootId(server, parentSampleRowId, SAMPLE_ALIQUOT_IMPORT_TYPE_NAME, aliquotQueryCols, topFolderOptions, readerUserOptions);
            expect(aliquots.length).toEqual(2);
            aliquots.forEach(aliquot => {
                expect(caseInsensitive(aliquot, 'AliquotedFromLsid/name')).toEqual(parentSampleName);
                expect(caseInsensitive(aliquot, 'rootmaterialrowid')).toEqual(parentSampleRowId);
                expect(caseInsensitive(aliquot, 'description')).toEqual(aliquotDes);
                expect(caseInsensitive(aliquot, 'str')).toEqual(parentInsertRow.str);
                expect(caseInsensitive(aliquot, 'int')).toEqual(parentInsertRow.int);
                expect(caseInsensitive(aliquot, 'myparentcol')).toEqual(parentInsertRow.myparentcol);
                expect(caseInsensitive(aliquot, 'myaliquotcol')).toBeNull();
                expect(caseInsensitive(aliquot, 'myindependentcol')).toBeNull();
                expect(caseInsensitive(aliquot, 'isaliquot')).toBeTruthy();
            })
        });

        /**
         * <p>
         *     Import some aliquots with the name values set.
         * </p>
         * <p>
         *     Using one import file this test will:
         *     <ul>
         *         <li>Have an aliquot with the name set to some string.</li>
         *         <li>An aliquot with the name formatted as an aliquot (SAI_1-101).</li>
         *         <li>And have an aliquot w/o a name set.</li>
         *         <li>Validate that the names are as expected.</li>
         *         <li>Issue 53419: Aliquot parent with number like names that starts with leading zeroes aren't resolved during import</li>
         *     </ul>
         *     Because importing is batched the imported aliquot without an explicit name set it should have the next
         *     index (default behavior).
         * </p>
         */

        const parentSampleName = ['S-1', '123', '0001', '0002', 'With Space', '+ -_.&)(:'];


        it('(Fuzz Test) testImportingWithNameValue - with naming patten ', async () => {
            // also include scenarios from testImportWithUpdate
            await verifyImportingWithNameValue(selectRandomN(parentSampleName, 1)[0], SAMPLE_ALIQUOT_IMPORT_TYPE_NAME);
        });

        /**
         * <p>
         *     Validate that import will work for a SampleType that does not have a name expression set.
         * </p>
         * <p>
         *     This test has one import file and will:
         *     <ul>
         *         <li>Import two aliquots with a name value set.</li>
         *         <li>Import one aliquot without the name value set.</li>
         *         <li>Validate that the aliquot names are as expected.</li>
         *         <li>The fields for the aliquots are as expected.</li>
         *     </ul>
         * </p>
         */
        it('(Fuzz Test) testImportingWithNameValue - without naming patten', async () => {
            await verifyImportingWithNameValue(selectRandomN(parentSampleName, 1)[0], SAMPLE_ALIQUOT_IMPORT_NO_NAME_PATTERN_NAME);
        });

        async function verifyMultipleRootsAndAliquots(parentSampleName1: string, parentSampleName2: string, sampleType: string) {
            const insertRows = [{
                name: parentSampleName1,
                description: 'testMultipleRootsAndAliquots parent1'
            },{
                name: parentSampleName2,
                description: 'testMultipleRootsAndAliquots parent2'
            }]
            const parentSampleRows = await ExperimentCRUDUtils.insertSamples(server, insertRows, sampleType, topFolderOptions, editorUserOptions);
            const parentSampleRow = parentSampleRows[0];
            const parent1RowId = caseInsensitive(parentSampleRows[0], 'rowId');
            const parent2RowId = caseInsensitive(parentSampleRows[1], 'rowId')

            const description01 = "This is an aliquot from the first root.";
            const description02 = "This is an aliquot from the second root.";
            let importText = "Description\tAliquotedFrom\n";
            for (let i = 0; i < 2; i++)
                importText += description01 + "\t" + parentSampleName1 + "\n";
            for (let i = 0; i < 2; i++)
                importText += description02 + "\t" + parentSampleName2 + "\n";
            await ExperimentCRUDUtils.importSample(server, importText, sampleType, 'IMPORT', topFolderOptions, editorUserOptions);

            const  parent1DataAfterAliquot = await ExperimentCRUDUtils.getSamplesData(server, [parent1RowId], sampleType, 'AliquotedFromLsid/name,aliquotcount', topFolderOptions, editorUserOptions);
            expect(caseInsensitive(parent1DataAfterAliquot[0], 'aliquotcount')).toEqual(2);
            const  parent2DataAfterAliquot = await ExperimentCRUDUtils.getSamplesData(server, [parent2RowId], sampleType, 'AliquotedFromLsid/name,aliquotcount', topFolderOptions, editorUserOptions);
            expect(caseInsensitive(parent2DataAfterAliquot[0], 'aliquotcount')).toEqual(2);

            // verify aliquot created
            const aliquots1 = await ExperimentCRUDUtils.getAliquotsByRootId(server, parent1RowId, sampleType, aliquotQueryCols, topFolderOptions, readerUserOptions);
            expect(aliquots1.length).toEqual(2);
            aliquots1.forEach(aliquot => {
                expect(caseInsensitive(aliquot, 'AliquotedFromLsid/name')).toEqual(parentSampleName1);
                expect(caseInsensitive(aliquot, 'rootmaterialrowid')).toEqual(parent1RowId);
                expect(caseInsensitive(aliquot, 'description')).toEqual(description01);
                expect(caseInsensitive(aliquot, 'isaliquot')).toBeTruthy();
            })

            const aliquots2 = await ExperimentCRUDUtils.getAliquotsByRootId(server, parent2RowId, sampleType, aliquotQueryCols, topFolderOptions, readerUserOptions);
            expect(aliquots2.length).toEqual(2);
            aliquots2.forEach(aliquot => {
                expect(caseInsensitive(aliquot, 'AliquotedFromLsid/name')).toEqual(parentSampleName2);
                expect(caseInsensitive(aliquot, 'rootmaterialrowid')).toEqual(parent2RowId);
                expect(caseInsensitive(aliquot, 'description')).toEqual(description02);
                expect(caseInsensitive(aliquot, 'isaliquot')).toBeTruthy();
            })


        }
        /**
         * <p>
         *     In a single import file have aliquots that have different root samples.
         * </p>
         * <p>
         *     With one import file this test will:
         *     <ul>
         *         <li>Create two aliquots from a root sample.</li>
         *         <li>Create two more aliquots from a different root sample.</li>
         *         <li>Validate fields of the aliquots.</li>
         *     </ul>
         * </p>
         */
        it('testMultipleRootsAndAliquots', async () => {
            const parentSampleName1 = 'testMultipleRootsParent1';
            const parentSampleName2 = 'testMultipleRootsParent2';
            await verifyMultipleRootsAndAliquots(parentSampleName1, parentSampleName2, SAMPLE_ALIQUOT_IMPORT_TYPE_NAME);
        });

        it('testMultipleRootsAndAliquots - no naming expression', async () => {
            const parentSampleName1= 'testMultipleRootsParentNoExp1';
            const parentSampleName2= 'testMultipleRootsParentNoExp2';
            await verifyMultipleRootsAndAliquots(parentSampleName1, parentSampleName2, SAMPLE_ALIQUOT_IMPORT_NO_NAME_PATTERN_NAME);
        });

        it('testCreateRootAliquotAndSubAliquot', async () => {
            const rootSampleName = "Harpo-Marx";
            const intData = 1719;
            const strData = "A chain of aliquots.";
            const aliquot01 = rootSampleName + "-1";
            const aliquotDesc = "The aliquot description.";

            const aliquot01sub01 = aliquot01 + "-1";
            const subAliquotDesc = "The sub-aliquot description.";

            let importText = "Name\tStr\tInt\tDescription\tAliquotedFrom\n";
            importText += rootSampleName + "\t" + strData + "\t" + intData + "\n";
            importText += aliquot01 + "\t\t\t" + aliquotDesc + "\t" + rootSampleName + "\n";
            importText += aliquot01sub01 + "\t\t\t" + subAliquotDesc + "\t" + aliquot01 + "\n";
            await ExperimentCRUDUtils.importSample(server, importText, SAMPLE_ALIQUOT_IMPORT_TYPE_NAME, 'IMPORT', topFolderOptions, editorUserOptions, false);
            // verify parent rollup
            const rootSample = await ExperimentCRUDUtils.getSampleDataByName(server, rootSampleName, SAMPLE_ALIQUOT_IMPORT_TYPE_NAME, 'rowId', topFolderOptions, readerUserOptions);
            const rootSampleId = caseInsensitive(rootSample, 'rowId');
            const  rootDataAfterAliquot = await ExperimentCRUDUtils.getSamplesData(server, [rootSampleId], SAMPLE_ALIQUOT_IMPORT_TYPE_NAME, 'AliquotedFromLsid/name,aliquotcount', topFolderOptions, editorUserOptions);
            expect(caseInsensitive(rootDataAfterAliquot[0], 'AliquotedFromLsid/name')).toBeNull();
            expect(caseInsensitive(rootDataAfterAliquot[0], 'aliquotcount')).toEqual(2);

            const aliquots = await ExperimentCRUDUtils.getAliquotsByRootId(server, rootSampleId, SAMPLE_ALIQUOT_IMPORT_TYPE_NAME, aliquotQueryCols, topFolderOptions, readerUserOptions);
            expect(aliquots.length).toEqual(2);
            aliquots.sort((a, b) => caseInsensitive(a, 'rowId') - caseInsensitive(b, 'rowId'));
            aliquots.forEach((aliquot, ind) => {
                expect(caseInsensitive(aliquot, 'AliquotedFromLsid/name')).toEqual(ind === 0 ? rootSampleName : aliquot01);
                expect(caseInsensitive(aliquot, 'rootmaterialrowid')).toEqual(rootSampleId);
                expect(caseInsensitive(aliquot, 'str')).toEqual(strData);
                expect(caseInsensitive(aliquot, 'int')).toEqual(intData);
                expect(caseInsensitive(aliquot, 'description')).toEqual(ind === 0 ?  aliquotDesc : subAliquotDesc);
                expect(caseInsensitive(aliquot, 'isaliquot')).toBeTruthy();
            })

        });

        it('testInvalidImportCases', async () => {
            const parentSampleName = "testInvalidImportCasesParent1";
            const parentSampleRows = await ExperimentCRUDUtils.insertSamples(server, [{name: parentSampleName}], SAMPLE_ALIQUOT_IMPORT_TYPE_NAME, topFolderOptions, editorUserOptions);
            const parentSampleRowId = caseInsensitive(parentSampleRows[0], 'rowId');
            const aliquotDesc = "Cannot change my root sample.";
            let importText = "Description\tAliquotedFrom\n";
            for (let i = 0; i < 2; i++)
                importText += aliquotDesc + "\t" + parentSampleName + "\n";
            await ExperimentCRUDUtils.importSample(server, importText, SAMPLE_ALIQUOT_IMPORT_TYPE_NAME, 'IMPORT', topFolderOptions, editorUserOptions);

            const  parentDataAfterAliquot = await ExperimentCRUDUtils.getSamplesData(server, [parentSampleRowId], SAMPLE_ALIQUOT_IMPORT_TYPE_NAME  , 'AliquotedFromLsid/name,aliquotcount', topFolderOptions, editorUserOptions);
            expect(caseInsensitive(parentDataAfterAliquot[0], 'aliquotcount')).toEqual(2);

            const aliquot01 = parentSampleName + "-1";
            const aliquot02 = parentSampleName + "-2";
            const absentRootSample = "Absent_Root";
            importText = "Description\tAliquotedFrom\n";
            importText += aliquotDesc + "\t" + absentRootSample + "\n";
            let resp = await ExperimentCRUDUtils.importSample(server, importText, SAMPLE_ALIQUOT_IMPORT_TYPE_NAME, "IMPORT", topFolderOptions, editorUserOptions);
            expect(resp.text.indexOf("Aliquot parent 'Absent_Root' not found.") > -1).toBeTruthy();
            const invalidRootSample = "Not_This_Root";
            await ExperimentCRUDUtils.insertSamples(server, [{name: invalidRootSample}], SAMPLE_ALIQUOT_IMPORT_TYPE_NAME, topFolderOptions, editorUserOptions)

            importText = "Name\tDescription\tAliquotedFrom\n";
            importText += aliquot01 + "\t" + aliquotDesc + "\t" + invalidRootSample + "\n";
            // Validate that if the AliquotedFrom field has an invalid value the import fails.
            resp = await ExperimentCRUDUtils.importSample(server, importText, SAMPLE_ALIQUOT_IMPORT_TYPE_NAME, "IMPORT", topFolderOptions, editorUserOptions);
            expect(resp.text.indexOf("duplicate key") > -1).toBeTruthy();

            // Validate that the AliquotedFrom field of an aliquot cannot be updated.
            resp = await ExperimentCRUDUtils.importSample(server, importText, SAMPLE_ALIQUOT_IMPORT_TYPE_NAME, "MERGE", topFolderOptions, editorUserOptions);
            expect(resp.text.indexOf("Aliquot parents cannot be updated for sample testInvalidImportCasesParent1-1.") > -1).toBeTruthy();

            // AliquotedFrom is ignored for UPDATE option
            resp = await ExperimentCRUDUtils.importSample(server, importText, SAMPLE_ALIQUOT_IMPORT_TYPE_NAME, "UPDATE", topFolderOptions, editorUserOptions);
            expect(resp.text.indexOf("Aliquot parents cannot be updated for sample testInvalidImportCasesParent1-1.") === -1).toBeTruthy();
            const aliquotAfterUpdate = await ExperimentCRUDUtils.getSampleDataByName(server, aliquot01, SAMPLE_ALIQUOT_IMPORT_TYPE_NAME, 'AliquotedFromLsid/name,isAliquot', topFolderOptions, readerUserOptions);
            expect(caseInsensitive(aliquotAfterUpdate, 'AliquotedFromLsid/name')).toEqual(parentSampleName);
            expect(caseInsensitive(aliquotAfterUpdate, 'isaliquot')).toBeTruthy();

            // Validate that an aliquot cannot be update using merge without the '%s' field present.
            importText = "Name\tDescription\n";
            importText += aliquot01 + "\tAliquotedFrom is missing\n";
            resp = await ExperimentCRUDUtils.importSample(server, importText, SAMPLE_ALIQUOT_IMPORT_TYPE_NAME, "MERGE", topFolderOptions, editorUserOptions);
            expect(resp.text).toContain("Aliquots are present but 'AliquotedFrom' column is missing.");

            // Validate that a sample cannot be changed to an aliquot.
            importText = "Name\tAliquotedFrom\n";
            importText += invalidRootSample + "\t" + parentSampleName + "\n";
            resp = await ExperimentCRUDUtils.importSample(server, importText, SAMPLE_ALIQUOT_IMPORT_TYPE_NAME, "MERGE", topFolderOptions, editorUserOptions);
            expect(resp.text.indexOf("Unable to change sample to aliquot Not_This_Root.") > -1).toBeTruthy();
        });

        /**
         * <p>
         *     Validate that import will work for a SampleType that has a required property
         *     (Issue 43647: SM: creating aliquots for a sample type with a required field gives an error)
         * </p>
         * <p>
         *     This test has one import file and will:
         *     <ul>
         *         <li>Import root samples with RequireProp populated.</li>
         *         <li>Import an aliquot with RequireProp provided, which will be ignored.</li>
         *         <li>Import an aliquot with RequireProp blank, which won't cause import to fail.</li>
         *         <li>Required columns must be included in the import file</li>
         *         <li>Required columns must not be blank for root samples.</li>
         *     </ul>
         * </p>
         */
        it('testImportWithRequiredField', async () => {
            const parentSampleName = "testImportWithRequiredFieldParent1";
            const parentReq = "parentreqvalue";
            const parentSampleRows = await ExperimentCRUDUtils.insertSamples(server, [{name: parentSampleName, requiredprops: parentReq}], SAMPLE_ALIQUOT_REQ_IMPORT_TYPE_NAME, topFolderOptions, editorUserOptions);
            const parentSampleRowId = caseInsensitive(parentSampleRows[0], 'rowId');

            const aliquotRequiredOverrideVal = "OverrideRequiredProps";
            const description = "Aliquots in a SampleType with required prop.";

            let importText = "Name\tDescription\tAliquotedFrom\trequiredprops\n";
            importText += "\t" + description + "\t" + parentSampleName + "\t" + aliquotRequiredOverrideVal + "\n";
            importText += "\t" + description + "\t" + parentSampleName + "\n";
            await ExperimentCRUDUtils.importSample(server, importText, SAMPLE_ALIQUOT_REQ_IMPORT_TYPE_NAME, 'IMPORT', topFolderOptions, editorUserOptions);

            const aliquots = await ExperimentCRUDUtils.getAliquotsByRootId(server, parentSampleRowId, SAMPLE_ALIQUOT_REQ_IMPORT_TYPE_NAME, aliquotQueryCols + ',requiredprops', topFolderOptions, readerUserOptions);
            expect(aliquots.length).toEqual(2);
            aliquots.sort((a, b) => caseInsensitive(a, 'rowId') - caseInsensitive(b, 'rowId'));
            aliquots.forEach((aliquot, ind) => {
                expect(caseInsensitive(aliquot, 'AliquotedFromLsid/name')).toEqual(parentSampleName);
                expect(caseInsensitive(aliquot, 'rootmaterialrowid')).toEqual(parentSampleRowId);
                expect(caseInsensitive(aliquot, 'requiredprops')).toEqual(parentReq);
                expect(caseInsensitive(aliquot, 'description')).toEqual(description);
                expect(caseInsensitive(aliquot, 'isaliquot')).toBeTruthy();
            });

            // Required columns must be included in the import file
            importText = "Name\tDescription\tAliquotedFrom\n";
            importText += "\t" + description + "\t" + parentSampleName+ "\n";
            importText += "AnotherRoot\t" + description + "\t" + parentSampleName + "\n";
            let resp = await ExperimentCRUDUtils.importSample(server, importText, SAMPLE_ALIQUOT_REQ_IMPORT_TYPE_NAME, 'IMPORT', topFolderOptions, editorUserOptions);
            expect(resp.text).toContain("Data does not contain required field: Requiredprops");
            resp = await ExperimentCRUDUtils.importSample(server, importText, SAMPLE_ALIQUOT_REQ_IMPORT_TYPE_NAME, 'MERGE', topFolderOptions, editorUserOptions);
            expect(resp.text).toContain("Data does not contain required field: Requiredprops");

            // Required columns must not be blank for root samples.
            importText = "Name\tDescription\tAliquotedFrom\trequiredprops\n";
            importText += "ReqNotProvided\t" + description + "\t\t\n";
            resp = await ExperimentCRUDUtils.importSample(server, importText, SAMPLE_ALIQUOT_REQ_IMPORT_TYPE_NAME, 'IMPORT', topFolderOptions, editorUserOptions);
            expect(resp.text).toContain("Missing value for required property: Requiredprops");
            resp = await ExperimentCRUDUtils.importSample(server, importText, SAMPLE_ALIQUOT_REQ_IMPORT_TYPE_NAME, 'MERGE', topFolderOptions, editorUserOptions);
            expect(resp.text).toContain("Missing value for required property: Requiredprops");

            // Blank required column for aliquots is accepted for update
            importText = "Name\tDescription\tAliquotedFrom\trequiredprops\n";
            importText += parentSampleName + "-1\t" + description + "updated\t" + parentSampleName + "\t" + aliquotRequiredOverrideVal + "\n";
            importText += parentSampleName + "-2\t" + description + "updated\t" + parentSampleName + "\n";
            resp = await ExperimentCRUDUtils.importSample(server, importText, SAMPLE_ALIQUOT_REQ_IMPORT_TYPE_NAME, 'MERGE', topFolderOptions, editorUserOptions);
            expect(resp.text.indexOf('error')).toBe(-1);
            const aliquotsAfterUpdate = await ExperimentCRUDUtils.getAliquotsByRootId(server, parentSampleRowId, SAMPLE_ALIQUOT_REQ_IMPORT_TYPE_NAME, aliquotQueryCols + ',requiredprops', topFolderOptions, readerUserOptions);
            aliquotsAfterUpdate.forEach((aliquot, ind) => {
                expect(caseInsensitive(aliquot, 'requiredprops')).toEqual(parentReq);
                expect(caseInsensitive(aliquot, 'description')).toEqual(description + "updated");
            });
        });

        async function verifyIgnoreParentFields(parentInsertRow: any, parentSampleRowId: number, suffix: string = '', insertOption: string = 'IMPORT') {
            let importText = (insertOption === 'IMPORT' ? '' : 'Name\t') + "Str\tInt\tMyparentcol\tMyaliquotcol\tMyindependentcol\tDescription\tAliquotedFrom\tIsAliquot\tAliquotCount\tAliquotVolume\n";
            importText += (insertOption === 'IMPORT' ? '' : parentInsertRow.name + '-1\t') + "childstr\t55\tinvalidparentval\taliquotval" + suffix + "\toverridden!" + suffix + "\taliquotdes" + suffix + "\t" + parentInsertRow.name + "\tfalse\t5\t12.3\n";
            await ExperimentCRUDUtils.importSample(server, importText, SAMPLE_ALIQUOT_IMPORT_TYPE_NAME, insertOption, topFolderOptions, editorUserOptions);
            const aliquots = await ExperimentCRUDUtils.getAliquotsByRootId(server, parentSampleRowId, SAMPLE_ALIQUOT_IMPORT_TYPE_NAME, aliquotQueryCols + ',AliquotCount,AliquotVolume', topFolderOptions, readerUserOptions);
            expect(aliquots.length).toEqual(1);
            let aliquot = aliquots[0];
            expect(caseInsensitive(aliquot, 'AliquotedFromLsid/name')).toEqual(parentInsertRow.name);
            expect(caseInsensitive(aliquot, 'rootmaterialrowid')).toEqual(parentSampleRowId);
            expect(caseInsensitive(aliquot, 'isaliquot')).toBeTruthy();
            expect(caseInsensitive(aliquot, 'str')).toEqual(parentInsertRow.str);
            expect(caseInsensitive(aliquot, 'int')).toEqual(parentInsertRow.int);
            expect(caseInsensitive(aliquot, 'myparentcol')).toEqual(parentInsertRow.myparentcol);
            expect(caseInsensitive(aliquot, 'Myaliquotcol')).toEqual('aliquotval' + suffix);
            expect(caseInsensitive(aliquot, 'Myindependentcol')).toEqual('overridden!' + suffix);
            expect(caseInsensitive(aliquot, 'description')).toEqual('aliquotdes' + suffix);
            expect(caseInsensitive(aliquot, 'AliquotCount')).toBeNull();
            expect(caseInsensitive(aliquot, 'AliquotVolume')).toBeNull();

        }

        /**
         * <p>
         *     Validate that the appropriate aliquot fields are ignored when importing/updating.
         * </p>
         * <p>
         *     For aliquot ignore these fields during creation and update:
         *     <ul>
         *         <li>Str and Int fields.</li>
         *         <li>ParentOnly</li>
         *         <li>Is Aliquot</li>
         *         <li>Aliquots Created</li>
         *         <li>Total Aliquot Volume</li>
         *     </ul>
         *     for a sample ignore these fields during creation and update:
         *     <ul>
         *         <li>AliquotOnly</li>
         *         <li>Is Aliquot</li>
         *         <li>Aliquots Created</li>
         *         <li>Total Aliquot Volume</li>
         *     </ul>
         *     Also check for timeline event detail for import with merge with ignored fields
         * </p>
         */
        it('testIgnoreFieldsOnImport', async () => {
            const parentSampleName = 'testIgnoreFieldsOnImportParent1';
            const parentInsertRow = {
                name: parentSampleName,
                str: 'parentstr',
                int: 99,
                myparentcol: 'parentVal',
                myaliquotcol: 'ignored',
                myindependentcol: 'can override',
                description: 'testIgnoreFieldsOnImport parent'
            }
            const parentSampleRows = await ExperimentCRUDUtils.insertSamples(server, [parentInsertRow], SAMPLE_ALIQUOT_IMPORT_TYPE_NAME, topFolderOptions, editorUserOptions);
            const parentSampleRow = parentSampleRows[0];
            const parentSampleRowId = caseInsensitive(parentSampleRow, 'RowId');

            await verifyIgnoreParentFields(parentInsertRow, parentSampleRowId, '', 'IMPORT');
            await verifyIgnoreParentFields(parentInsertRow, parentSampleRowId, '-up', 'UPDATE');
            await verifyIgnoreParentFields(parentInsertRow, parentSampleRowId, '-merge', 'MERGE');

            const sampleName = 'ignoreAliquotFieldSample';
            let importText = "Name\tStr\tInt\tIsAliquot\tAliquotCount\tAliquotVolume\tMyaliquotcol\n";
            importText += sampleName + "\tparentStr\t55\ttrue\t20\t12.3\tignored\n";
            await ExperimentCRUDUtils.importSample(server, importText, SAMPLE_ALIQUOT_IMPORT_TYPE_NAME, 'IMPORT', topFolderOptions, editorUserOptions);
            let sampleData = await ExperimentCRUDUtils.getSampleDataByName(server, sampleName, SAMPLE_ALIQUOT_IMPORT_TYPE_NAME, 'Str,Int,AliquotCount,AliquotVolume,Myaliquotcol,isAliquot', topFolderOptions, readerUserOptions);
            expect(caseInsensitive(sampleData, 'Str')).toEqual('parentStr');
            expect(caseInsensitive(sampleData, 'AliquotCount')).toEqual(0);
            expect(caseInsensitive(sampleData, 'AliquotVolume')).toEqual(0);
            expect(caseInsensitive(sampleData, 'IsAliquot')).toBeFalsy();
            expect(caseInsensitive(sampleData, 'Myaliquotcol')).toBeNull();

            importText = "Name\tInt\tIsAliquot\tAliquotCount\tAliquotVolume\tMyaliquotcol\n";
            importText += sampleName + "\t66\ttrue\t20\t12.3\tignored\n";
            await ExperimentCRUDUtils.importSample(server, importText, SAMPLE_ALIQUOT_IMPORT_TYPE_NAME, 'UPDATE', topFolderOptions, editorUserOptions);
            sampleData = await ExperimentCRUDUtils.getSampleDataByName(server, sampleName, SAMPLE_ALIQUOT_IMPORT_TYPE_NAME, 'Str,Int,AliquotCount,AliquotVolume,Myaliquotcol,isAliquot', topFolderOptions, readerUserOptions);
            expect(caseInsensitive(sampleData, 'Str')).toEqual('parentStr');
            expect(caseInsensitive(sampleData, 'Int')).toEqual(66);
            expect(caseInsensitive(sampleData, 'AliquotCount')).toEqual(0);
            expect(caseInsensitive(sampleData, 'AliquotVolume')).toEqual(0);
            expect(caseInsensitive(sampleData, 'IsAliquot')).toBeFalsy();
            expect(caseInsensitive(sampleData, 'Myaliquotcol')).toBeNull();

            importText = "Name\tStr\tInt\tIsAliquot\tAliquotCount\tAliquotVolume\tMyaliquotcol\n";
            importText += sampleName + "\tupdatedStr\t77\ttrue\t20\t12.3\tignored\n";
            await ExperimentCRUDUtils.importSample(server, importText, SAMPLE_ALIQUOT_IMPORT_TYPE_NAME, 'MERGE', topFolderOptions, editorUserOptions);
            sampleData = await ExperimentCRUDUtils.getSampleDataByName(server, sampleName, SAMPLE_ALIQUOT_IMPORT_TYPE_NAME, 'Str,Int,AliquotCount,AliquotVolume,Myaliquotcol,isAliquot', topFolderOptions, readerUserOptions);
            expect(caseInsensitive(sampleData, 'Str')).toEqual('updatedStr');
            expect(caseInsensitive(sampleData, 'Int')).toEqual(77);
            expect(caseInsensitive(sampleData, 'AliquotCount')).toEqual(0);
            expect(caseInsensitive(sampleData, 'AliquotVolume')).toEqual(0);
            expect(caseInsensitive(sampleData, 'IsAliquot')).toBeFalsy();
            expect(caseInsensitive(sampleData, 'Myaliquotcol')).toBeNull();

            const response = await server.post('query', 'selectRows', {
                schemaName: 'auditlog',
                queryName: 'sampletimelineevent',
                'query.columns': 'rowid,newvalues',
            }, { ...topFolderOptions, ...adminOptions }).expect(successfulResponse);
            const audits = response.body.rows;
            audits.sort((a, b) => {
                return caseInsensitive(b, 'rowId') - caseInsensitive(a, 'rowId');
            });
            const lastAuditChanges = caseInsensitive(audits[0], 'newvalues');
            const fields = lastAuditChanges.split('&');
            const changeFields = [];
            fields.forEach(field => {
                const parts = field.split('=');
                changeFields.push(parts[0].toLowerCase());
            });
            changeFields.sort((a, b) => {
                return a.localeCompare(b);
            })
            expect(changeFields).toEqual(['int', 'str']);
        });
    })

    describe('Sample Type - Required Lineage', () => {
        it('Test sample type with required dataclass parents', async () => {
            await verifyRequiredLineageInsertUpdate(server, false, true, topFolderOptions, subfolder1Options, adminOptions /* so user can create both dataclass and sample type*/, readerUserOptions, editorUserOptions, adminOptions);
        });

        it('Test sample type with required sample parents', async () => {
            await verifyRequiredLineageInsertUpdate(server, true, true, topFolderOptions, subfolder1Options, designerReaderOptions, readerUserOptions, editorUserOptions, adminOptions);
        });
    });

});

describe('Amount/Unit CRUD', () => {
    it ("Test Amounts/Units validation on insert/import/update/merge", async () => {
        const dataType = SAMPLE_ALIQUOT_IMPORT_NO_NAME_PATTERN_NAME;
        const NO_UNIT_ERROR = 'A \'Units\' value must be provided when \'Amounts\' are provided.';
        const NO_AMOUNT_ERROR = 'An \'Amount\' value must be provided when \'Units\' are provided.';
        const INCOMPATIBLE_ERROR = 'Units value (L) is not compatible with the ' + dataType + ' display units (g).';
        const NEGATIVE_ERROR = "Value '-1.1' for field 'Amount' is invalid. Amounts must be non-negative.";

        const dataName = "S-amountCrud";

         let errorMsg = await ExperimentCRUDUtils.importSample(server, "Name\tStoredAmount\nData1\t1", dataType, "INSERT", topFolderOptions, editorUserOptions);
        expect(errorMsg.text).toContain(NO_UNIT_ERROR);
        errorMsg = await ExperimentCRUDUtils.importSample(server, "Name\tUnits\nData1\tkg", dataType, "INSERT", topFolderOptions, editorUserOptions);
        expect(errorMsg.text).toContain(NO_AMOUNT_ERROR);
        errorMsg = await ExperimentCRUDUtils.importSample(server, "Name\tStoredAmount\tUnits\nData1\t1.1\tL", dataType, "INSERT", topFolderOptions, editorUserOptions);
        expect(errorMsg.text).toContain(INCOMPATIBLE_ERROR);
        errorMsg = await ExperimentCRUDUtils.importSample(server, "Name\tStoredAmount\tUnits\nData1\t1.1\tunit", dataType, "INSERT", topFolderOptions, editorUserOptions);
        expect(errorMsg.text).toContain('Units value (unit) is not compatible with the ' + dataType + ' display units (g).');
        errorMsg = await ExperimentCRUDUtils.importSample(server, "Name\tStoredAmount\tUnits\nData1\t1.1\tcells", dataType, "INSERT", topFolderOptions, editorUserOptions);
        expect(errorMsg.text).toContain('Units value (cells) is not compatible with the ' + dataType + ' display units (g).');
        errorMsg = await ExperimentCRUDUtils.importSample(server, "Name\tStoredAmount\tUnits\nData1\t1.1\tbogus", dataType, "INSERT", topFolderOptions, editorUserOptions);
        expect(errorMsg.text).toContain('Unsupported Units value (bogus). Supported values are: kg, g, mg, ug, ng.');
        errorMsg = await ExperimentCRUDUtils.importSample(server, "Name\tStoredAmount\tUnits\nData1\t-1.1\tkg", dataType, "INSERT", topFolderOptions, editorUserOptions);
        expect(errorMsg.text).toContain(NEGATIVE_ERROR);
        errorMsg = await ExperimentCRUDUtils.importCrossTypeData(server, "Name\tStoredAmount\tUnits\tSampleType\nData1\t-1.1\tkg\t" + dataType ,'IMPORT', topFolderOptions, adminOptions, true);
        expect(errorMsg.text).toContain(NEGATIVE_ERROR);

        await server.post('query', 'insertRows', {
            schemaName: 'samples',
            queryName: dataType,
            rows: [{
                name: dataName,
                amount: -1.1,
                units: 'kg',
            }]
        }, { ...topFolderOptions, ...editorUserOptions }).expect((result) => {
            const errorResp = JSON.parse(result.text);
            expect(errorResp['exception']).toContain(NEGATIVE_ERROR);
        });
        const sampleRows = await ExperimentCRUDUtils.insertRows(server, [{
            name: dataName,
            amount: 123,
            units: 'kg',
        }], 'samples', dataType, topFolderOptions, editorUserOptions);

        const sampleRowId = caseInsensitive(sampleRows[0], 'rowId');

        errorMsg = await ExperimentCRUDUtils.importSample(server, "Name\tAmount\n" + dataName + "\t321", dataType, "UPDATE", topFolderOptions, editorUserOptions);
        expect(errorMsg.text).toContain(NO_UNIT_ERROR);
        errorMsg = await ExperimentCRUDUtils.importSample(server, "Name\tStoredAmount\n" + dataName + "\t321", dataType, "MERGE", topFolderOptions, editorUserOptions);
        expect(errorMsg.text).toContain(NO_UNIT_ERROR);
        await server.post('query', 'updateRows', {
            schemaName: 'samples',
            queryName: dataType,
            rows: [{
                Amount: 321,
                rowId: sampleRowId
            }]
        }, { ...topFolderOptions, ...editorUserOptions }).expect((result) => {
            const errorResp = JSON.parse(result.text);
            expect(errorResp['exception']).toContain(NO_UNIT_ERROR);
        });
        await server.post('query', 'updateRows', {
            schemaName: 'samples',
            queryName: dataType,
            rows: [{
                StoredAmount: 321,
                rowId: sampleRowId
            }]
        }, { ...topFolderOptions, ...editorUserOptions }).expect((result) => {
            const errorResp = JSON.parse(result.text);
            expect(errorResp['exception']).toContain(NO_UNIT_ERROR);
        });


        errorMsg = await ExperimentCRUDUtils.importSample(server, "Name\tUnits\n" + dataName + "\tg", dataType, "UPDATE", topFolderOptions, editorUserOptions);
        expect(errorMsg.text).toContain(NO_AMOUNT_ERROR);
        errorMsg = await ExperimentCRUDUtils.importSample(server, "Name\tUnits\n" + dataName + "\tg", dataType, "MERGE", topFolderOptions, editorUserOptions);
        expect(errorMsg.text).toContain(NO_AMOUNT_ERROR);
        await server.post('query', 'updateRows', {
            schemaName: 'samples',
            queryName: dataType,
            rows: [{
                Units: 'kg',
                rowId: sampleRowId
            }]
        }, { ...topFolderOptions, ...editorUserOptions }).expect((result) => {
            const errorResp = JSON.parse(result.text);
            expect(errorResp['exception']).toContain(NO_AMOUNT_ERROR);
        });

        errorMsg = await ExperimentCRUDUtils.importSample(server, "Name\tAmount\tUnits\n" + dataName + "\t321\tL", dataType, "UPDATE", topFolderOptions, editorUserOptions);
        expect(errorMsg.text).toContain(INCOMPATIBLE_ERROR);
        errorMsg = await ExperimentCRUDUtils.importSample(server, "Name\tStoredAmount\tUnits\n" + dataName + "\t321\tL", dataType, "MERGE", topFolderOptions, editorUserOptions);
        expect(errorMsg.text).toContain(INCOMPATIBLE_ERROR);
        await server.post('query', 'updateRows', {
            schemaName: 'samples',
            queryName: dataType,
            rows: [{
                Amount: 321,
                Units: 'L',
                rowId: sampleRowId
            }]
        }, { ...topFolderOptions, ...editorUserOptions }).expect((result) => {
            const errorResp = JSON.parse(result.text);
            expect(errorResp['exception']).toContain(INCOMPATIBLE_ERROR);
        });

        errorMsg = await ExperimentCRUDUtils.importSample(server, "Name\tAmount\tUnits\n" + dataName + "\t-1.1\tkg", dataType, "UPDATE", topFolderOptions, editorUserOptions);
        expect(errorMsg.text).toContain(NEGATIVE_ERROR);
        errorMsg = await ExperimentCRUDUtils.importSample(server, "Name\tStoredAmount\tUnits\n" + dataName + "\t-1.1\tkg", dataType, "MERGE", topFolderOptions, editorUserOptions);
        expect(errorMsg.text).toContain(NEGATIVE_ERROR);

        // Using row-by-row
        await server.post('query', 'updateRows', {
            schemaName: 'samples',
            queryName: dataType,
            rows: [{
                Amount: -1,
                Units: 'kg',
                rowId: sampleRowId,
            },{
                rowId: sampleRowId,
            }]
        }, { ...topFolderOptions, ...editorUserOptions }).expect((result) => {
            const errorResp = JSON.parse(result.text);
            expect(errorResp['exception']).toContain("Value '-1' for field 'Amount' is invalid. Amounts must be non-negative.");
        });

        // Using data iterator
        await server.post('query', 'updateRows', {
            schemaName: 'samples',
            queryName: dataType,
            rows: [{
                Amount: -1,
                Units: 'kg',
                rowId: sampleRowId,
            }]
        }, { ...topFolderOptions, ...editorUserOptions }).expect((result) => {
            const errorResp = JSON.parse(result.text);
            expect(errorResp['exception']).toContain("Value '-1' for field 'Amount' is invalid. Amounts must be non-negative.");
        });

        errorMsg = await ExperimentCRUDUtils.importCrossTypeData(server, "Name\tStoredAmount\tUnits\tSampleType\nData1\t-1.1\tkg\t" + dataType ,'UPDATE', topFolderOptions, adminOptions, true);
        expect(errorMsg.text).toContain(NEGATIVE_ERROR);
        errorMsg = await ExperimentCRUDUtils.importCrossTypeData(server, "Name\tStoredAmount\tUnits\tSampleType\nData1\t-1.1\tkg\t" + dataType ,'MERGE', topFolderOptions, adminOptions, true);
        expect(errorMsg.text).toContain(NEGATIVE_ERROR);
    });

    it ("Test units conversion on insert/update", async () => {
        const sampleTypeMass = 'SampleTypeWithMassUnits';
        const sampleTypeVolume = 'SampleTypeWithVolumeUnits';
        const sampleTypeCount = 'SampleTypeWithCountUnits';

        const sampleTypeUnits = {
            [sampleTypeMass]: 'ug',
            [sampleTypeVolume]: 'L',
            [sampleTypeCount]: 'unit'
        };

        for (const [dataType, unit] of Object.entries(sampleTypeUnits)) {
            const createPayload = {
                kind: 'SampleSet',
                domainDesign: { name: dataType, fields: [{ name: 'Name' }] },
                options: {
                    name: dataType,
                    metricUnit: unit
                }
            };
            await server.post('property', 'createDomain', createPayload, {...topFolderOptions, ...designerReaderOptions}).expect(successfulResponse);
        }

        let sampleRowsWithUnits = await ExperimentCRUDUtils.insertRows(server, [
            {name: 'S-ng', amount: 4.56, units: 'ng'},
            {name: 'S-ug', amount: 4.56, units: 'ug'},
            {name: 'S-mg', amount: 4.56, units: 'mg'},
            {name: 'S-g', amount: 4.56, units: 'g'},
            {name: 'S-kg', amount: 4.56, units: 'kg'},
        ], 'samples', sampleTypeMass, topFolderOptions, editorUserOptions);

        // check for raw amount in g and display amount in ug
        let expectedRawAmounts : {} = {
            'S-ng': 4.56e-9,
            'S-ug': 4.56e-6,
            'S-mg': 0.00456,
            'S-g': 4.56,
            'S-kg': 4560,
        };
        let expectedStoredAmounts : {} = {
            'S-ng': 4.56e-3,
            'S-ug': 4.56,
            'S-mg': 4560,
            'S-g': 4.56e6,
            'S-kg': 4.56e9,
        };

        for (const sampleRow of sampleRowsWithUnits) {
            const sampleName = caseInsensitive(sampleRow, 'name');
            let sampleData = await ExperimentCRUDUtils.getSampleDataByName(server, sampleName, sampleTypeMass, 'StoredAmount,Units,RawAmount,RawUnits', topFolderOptions, readerUserOptions);
            expect(caseInsensitive(sampleData, 'RawAmount')).toBeCloseTo(expectedRawAmounts[sampleName]);
            expect(caseInsensitive(sampleData, 'StoredAmount')).toBeCloseTo(expectedStoredAmounts[sampleName]);
            expect(caseInsensitive(sampleData, 'RawUnits')).toEqual('g');
            expect(caseInsensitive(sampleData, 'Units')).toEqual('ug');
            await server.post('query', 'updateRows', {
                schemaName: 'samples',
                queryName: sampleTypeMass,
                rows: [{
                    amount: 6.54,
                    units: sampleName.substring(2),
                    rowId: caseInsensitive(sampleRow, 'rowId')
                }]
            }, { ...topFolderOptions, ...editorUserOptions }).expect(successfulResponse);
        }

        expectedRawAmounts = {
            'S-ng': 6.54e-9,
            'S-ug': 6.54e-6,
            'S-mg': 0.00654,
            'S-g': 6.54,
            'S-kg': 6540,
        };
        expectedStoredAmounts = {
            'S-ng': 6.54e-3,
            'S-ug': 6.54,
            'S-mg': 6540,
            'S-g': 6.54e6,
            'S-kg': 6.54e9,
        };
        for (const sampleRow of sampleRowsWithUnits) {
            const sampleName = caseInsensitive(sampleRow, 'name');
            let sampleData = await ExperimentCRUDUtils.getSampleDataByName(server, sampleName, sampleTypeMass, 'StoredAmount,Units,RawAmount,RawUnits', topFolderOptions, readerUserOptions);
            expect(caseInsensitive(sampleData, 'RawAmount')).toBeCloseTo(expectedRawAmounts[sampleName]);
            expect(caseInsensitive(sampleData, 'StoredAmount')).toBeCloseTo(expectedStoredAmounts[sampleName]);
            expect(caseInsensitive(sampleData, 'RawUnits')).toEqual('g');
            expect(caseInsensitive(sampleData, 'Units')).toEqual('ug');
        }

        sampleRowsWithUnits = await ExperimentCRUDUtils.insertRows(server, [
            {name: 'S-L', amount: 4.56, units: 'L'},
            {name: 'S-mL', amount: 4.56, units: 'mL'},
            {name: 'S-uL', amount: 4.56, units: 'uL'},
        ], 'samples', sampleTypeVolume, topFolderOptions, editorUserOptions);

        // check for storedamount in mL
        expectedRawAmounts = {
            'S-L': 4560,
            'S-mL': 4.56,
            'S-uL': 0.00456,
        };
        // stored amount is in L
        expectedStoredAmounts = {
            'S-L': 4.56,
            'S-mL': 0.00456,
            'S-uL': 4.56e-6,
        }
        for (const sampleRow of sampleRowsWithUnits) {
            const sampleName = caseInsensitive(sampleRow, 'name');
            const sampleData = await ExperimentCRUDUtils.getSampleDataByName(server, sampleName, sampleTypeVolume, 'StoredAmount,Units,RawAmount,RawUnits', topFolderOptions, readerUserOptions);
            expect(caseInsensitive(sampleData, 'RawAmount')).toBeCloseTo(expectedRawAmounts[sampleName]);
            expect(caseInsensitive(sampleData, 'StoredAmount')).toBeCloseTo(expectedStoredAmounts[sampleName]);
            expect(caseInsensitive(sampleData, 'RawUnits')).toEqual('mL');
            expect(caseInsensitive(sampleData, 'Units')).toEqual('L');
        }

        const countRows = [
            {name: 'S-unit', amount: 4.56, units: 'unit'},
            {name: 'S-pieces', amount: 4.56, units: 'pieces'},
            {name: 'S-kits', amount: 4.56, units: 'kits'},
            {name: 'S-cells', amount: 4.56, units: 'cells'}
        ]
        sampleRowsWithUnits = await ExperimentCRUDUtils.insertRows(server, countRows, 'samples', sampleTypeCount, topFolderOptions, editorUserOptions);

        for (const sampleRow of sampleRowsWithUnits) {
            const sampleName = caseInsensitive(sampleRow, 'name');
            const usedUnit = sampleName.substring(2);
            const sampleData = await ExperimentCRUDUtils.getSampleDataByName(server, sampleName, sampleTypeCount, 'StoredAmount,Units,RawAmount,RawUnits', topFolderOptions, readerUserOptions);
            expect(caseInsensitive(sampleData, 'RawAmount')).toBeCloseTo(4.56);
            expect(caseInsensitive(sampleData, 'StoredAmount')).toBeCloseTo(4.56);
            expect(caseInsensitive(sampleData, 'RawUnits')).toEqual(usedUnit);
            expect(caseInsensitive(sampleData, 'Units')).toEqual(usedUnit);

            await server.post('query', 'updateRows', {
                schemaName: 'samples',
                queryName: sampleTypeCount,
                rows: [{
                    amount: 6.54,
                    units: usedUnit,
                    rowId: caseInsensitive(sampleRow, 'rowId')
                }]
            }, { ...topFolderOptions, ...editorUserOptions }).expect(successfulResponse);
        }

        for (const sampleRow of sampleRowsWithUnits) {
            const sampleName = caseInsensitive(sampleRow, 'name');
            const usedUnit = sampleName.substring(2);
            const sampleData = await ExperimentCRUDUtils.getSampleDataByName(server, sampleName, sampleTypeCount, 'StoredAmount,Units,RawAmount,RawUnits', topFolderOptions, readerUserOptions);
            expect(caseInsensitive(sampleData, 'RawAmount')).toBeCloseTo(6.54);
            expect(caseInsensitive(sampleData, 'StoredAmount')).toBeCloseTo(6.54);
            expect(caseInsensitive(sampleData, 'RawUnits')).toEqual(usedUnit);
            expect(caseInsensitive(sampleData, 'Units')).toEqual(usedUnit);
        }

    })

    async function verifyCountTypeAliquotRollup(sampleTypeName: string, hasSampleTypeDisplayUnit: boolean) {
        const dataRows = [
            {name: 'S-no-amount'},
            {AliquotedFrom: 'S-no-amount', name: 'S-no-pcs1', amount: 2, units: 'pieces'},
            {AliquotedFrom: 'S-no-amount', name: 'S-no-pcs2', amount: 2, units: 'pieces'},
            {name: 'S-unit', amount: 1, units: 'unit'},
            {AliquotedFrom: 'S-unit', name: 'S-unit-unit1', amount: 2, units: 'unit'},
            {AliquotedFrom: 'S-unit', name: 'S-unit-unit2', amount: 2, units: 'unit'},
            {name: 'S-pieces', amount: 1, units: 'pieces'},
            {AliquotedFrom: 'S-pieces', name: 'S-pcs-pcs1', amount: 2, units: 'pieces'},
            {AliquotedFrom: 'S-pieces', name: 'S-pcs-pcs2', amount: 2, units: 'pieces'},
            {name: 'S-kits', amount: 1, units: 'kits'},
            {AliquotedFrom: 'S-kits', name: 'S-kit-pcs1', amount: 2, units: 'pieces'},
            {AliquotedFrom: 'S-kits', name: 'S-kit-pcs2', amount: 2, units: 'pieces'},
            {name: 'S-cells', amount: 1, units: 'cells'},
            {AliquotedFrom: 'S-cells', name: 'S-cells-pcs1', amount: 2, units: 'pieces'},
            {AliquotedFrom: 'S-cells', name: 'S-cells-cells2', amount: 2, units: 'cells'},
        ]

        const insertedResults = await ExperimentCRUDUtils.insertRows(server, dataRows, 'samples', sampleTypeName, topFolderOptions, editorUserOptions);
        const insertedMap = {};
        for (const row of insertedResults) {
            insertedMap[caseInsensitive(row, 'name')] = row;
        }

        let expectedAliquotUnit = {
            'S-no-amount': 'pieces',
            'S-unit': 'unit',
            'S-pieces': 'pieces',
            'S-kits': 'pieces',
            'S-cells': hasSampleTypeDisplayUnit ? 'unit' : 'cells',
        };

        // for each expectedRollupAmounts
        for (const [sampleName, expectedAliquotUnitValue] of Object.entries(expectedAliquotUnit)) {
            let parentUnit = sampleName.substring(2);
            if (parentUnit === 'no-amount') {
                parentUnit = null;
            }
            const sampleData = await ExperimentCRUDUtils.getSampleDataByName(server, sampleName, sampleTypeName, 'Units,RawUnits,AliquotVolume,AliquotCount,AliquotUnit', topFolderOptions, readerUserOptions);
            expect(caseInsensitive(sampleData, 'RawUnits')).toEqual(parentUnit);
            expect(caseInsensitive(sampleData, 'Units')).toEqual(parentUnit);
            expect(caseInsensitive(sampleData, 'AliquotVolume')).toEqual(4);
            expect(caseInsensitive(sampleData, 'AliquotCount')).toEqual(2);
            expect(caseInsensitive(sampleData, 'AliquotUnit')).toEqual(expectedAliquotUnitValue);

        }
    }

    it ("Test aliquot rollup for count display unit", async () => {
        let dataType = 'SampleTypeAliquotWithCountUnit';
        let createPayload : {} = {
            kind: 'SampleSet',
            domainDesign: { name: dataType, fields: [{ name: 'Name' }] },
            options: {
                name: dataType,
                metricUnit: 'unit'
            }
        };
        await server.post('property', 'createDomain', createPayload, {...topFolderOptions, ...designerReaderOptions}).expect(successfulResponse);
        await verifyCountTypeAliquotRollup(dataType, true);

        dataType = 'SampleTypeAliquoNoDisplayUnit';
        createPayload = {
            kind: 'SampleSet',
            domainDesign: { name: dataType, fields: [{ name: 'Name' }] },
            options: {
                name: dataType,
            }
        };
        await server.post('property', 'createDomain', createPayload, {...topFolderOptions, ...designerReaderOptions}).expect(successfulResponse);
        await verifyCountTypeAliquotRollup(dataType, false);

    })

});

