import { ExperimentCRUDUtils, hookServer, RequestOptions, successfulResponse } from '@labkey/test';
import mock from 'mock-fs';
import {
    checkDomainName,
    checkLackDesignerOrReaderPerm,
    createSource,
    deleteSourceType,
    getDataClassRowIdByName,
    initProject,
    verifyRequiredLineageInsertUpdate,
} from './utils';
import { caseInsensitive, DATA_CLASS_DESIGNER_ROLE } from '@labkey/components';
const server = hookServer(process.env);
const PROJECT_NAME = 'DataClassCrudJestProject';

let readerUser, readerUserOptions;
let editorUser, editorUserOptions;
let designer, designerOptions;
let designerReader, designerReaderOptions;
let designerEditor, designerEditorOptions;
let admin, adminOptions;

let topFolderOptions: RequestOptions;
let subfolder1Options;
let subfolder2Options;

async function getDataClassRowId(dataClassName: string, folderOptions: RequestOptions) {
    return getDataClassRowIdByName(server, dataClassName, folderOptions);
}

async function createData(dataClassName: string, name: string, folderOptions: RequestOptions) {
    return createSource(server, dataClassName, name, folderOptions, editorUserOptions);
}

async function deleteDataClass(dataTypeRowId: number, folderOptions: RequestOptions, userOptions: RequestOptions) {
    return deleteSourceType(server, dataTypeRowId, folderOptions, userOptions);
}

beforeAll(async () => {
    const options = await initProject(server, PROJECT_NAME, DATA_CLASS_DESIGNER_ROLE);

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
});

afterAll(async () => {
    return server.teardown();
});

afterEach(() => {
    mock.restore();
});

describe('Data Class Designer', () => {
    it('Lack designer or Reader permission', async () => {
        await checkLackDesignerOrReaderPerm(server, 'DataClass', topFolderOptions, readerUserOptions, editorUserOptions, designerOptions);
    });

    it('Data class name and field name validation', async () => {
        await checkDomainName(server, 'DataClass', true, topFolderOptions, designerReaderOptions);
    });

    describe('Create/update/delete designs', () => {
        it('Designer can create, update and delete empty design, reader and editors cannot create/update/delete design', async () => {
            const dataType = "ToDelete";
            let domainId = -1, domainURI = '';
            const createPayload = {
                kind: 'DataClass',
                domainDesign: { name: dataType, fields: [{ name: 'Prop' }] },
                options: {
                    name: dataType,
                }
            };

            await server.post('property', 'createDomain', createPayload,
                {...topFolderOptions, ...readerUserOptions}).expect(403);
            await server.post('property', 'createDomain', createPayload,
                {...topFolderOptions, ...editorUserOptions}).expect(403);

            await server.post('property', 'createDomain', createPayload,
                {...topFolderOptions, ...designerReaderOptions}).expect((result) => {
                const domain = JSON.parse(result.text);
                domainId = domain.domainId;
                domainURI = domain.domainURI;
                return true;
            });

            const dataTypeRowId = await getDataClassRowId(dataType, topFolderOptions);

            const updatePayload = {
                domainId,
                domainDesign: { name: dataType, domainId, domainURI },
                options: {
                    rowId: dataTypeRowId,
                    name: dataType,
                    nameExpression: 'S-${genId}',
                    importAliases: {
                        'legacy': 'dataInputs/' + dataType,
                        'newAlias': {
                            inputType: 'dataInputs/' + dataType,
                            required: false,
                        }
                    }
                }
            };
            await server.post('property', 'saveDomain', updatePayload, {...topFolderOptions, ...readerUserOptions})
                .expect(403);
            await server.post('property', 'saveDomain', updatePayload, {...topFolderOptions, ...editorUserOptions})
                .expect(403);

            await server.post('property', 'saveDomain', updatePayload, {...topFolderOptions, ...designerReaderOptions}).expect(successfulResponse);

            let deleteResult = await deleteDataClass(dataTypeRowId, topFolderOptions, readerUserOptions);
            expect(deleteResult.status).toEqual(403);
            deleteResult = await deleteDataClass(dataTypeRowId, topFolderOptions, editorUserOptions);
            expect(deleteResult.status).toEqual(403);

            deleteResult = await deleteDataClass(dataTypeRowId, topFolderOptions, designerReaderOptions);
            expect(deleteResult.status).toEqual(302);

            const removeddataType = await getDataClassRowId(dataType, topFolderOptions);

            expect(removeddataType).toEqual(0);
        });

        it('Designer can update non-empty design but cannot delete non-empty design, admin can delete non-empty design', async () => {
            const dataType = "FailedDelete";
            let domainId = -1, domainURI = '';
            await server.post('property', 'createDomain', {
                kind: 'DataClass',
                domainDesign: { name: dataType, fields: [{ name: 'Prop' }] },
                options: {
                    name: dataType,
                }
            }, {...topFolderOptions, ...designerReaderOptions}).expect((result) => {
                const domain = JSON.parse(result.text);
                domainId = domain.domainId;
                domainURI = domain.domainURI;
                return true;
            });

            const dataTypeRowId = await getDataClassRowId(dataType, topFolderOptions);

            // create data in child folder
            const createdDataId = await createData(dataType, 'Data1', subfolder1Options);
            expect(createdDataId === 0).toBeFalsy();

            const updateDomainPayload = {
                domainId,
                domainDesign: {name: dataType, domainId, domainURI},
                options: {
                    rowId: dataTypeRowId,
                    name: dataType,
                    nameExpression: 'Source-${genId}',
                    importAliases: {
                        'legacy': 'dataInputs/' + dataType,
                        'newAlias': {
                            inputType: 'dataInputs/' + dataType,
                            required: false,
                        }
                    }
                }
            };
            await server.post('property', 'saveDomain', updateDomainPayload, {...topFolderOptions, ...designerReaderOptions}).expect(successfulResponse);

            // verify data exist in child prevent adding new required alias
            updateDomainPayload.options.importAliases = {
                'legacy': 'dataInputs/' + dataType,
                'newAlias': {
                    inputType: 'dataInputs/' + dataType,
                    required: true,
                }
            };
            const requiredNotAllowedResp = await server.post('property', 'saveDomain', updateDomainPayload, {...topFolderOptions, ...designerReaderOptions});
            expect(requiredNotAllowedResp['body']['success']).toBeFalsy();
            expect(requiredNotAllowedResp['body']['exception']).toBe("'FailedDelete' cannot be required as a parent type when there are existing data without a parent of this type.");

            // verify data exist in child prevent designer from delete design
            let deleteResult = await deleteDataClass(dataTypeRowId, topFolderOptions, designerReaderOptions);
            expect(deleteResult.status).toEqual(403);
            deleteResult = await deleteDataClass(dataTypeRowId, topFolderOptions, readerUserOptions);
            expect(deleteResult.status).toEqual(403);
            deleteResult = await deleteDataClass(dataTypeRowId, topFolderOptions, editorUserOptions);
            expect(deleteResult.status).toEqual(403);

            let failedRemoveddataType = await getDataClassRowId(dataType, topFolderOptions);
            expect(failedRemoveddataType).toEqual(dataTypeRowId);

            // create more data in top folder
            await createData(dataType, 'Data2', {...topFolderOptions, ...editorUserOptions});

            // verify data exist in Top prevent designer from delete design
            deleteResult = await deleteDataClass(dataTypeRowId, topFolderOptions, designerEditorOptions);
            expect(deleteResult.status).toEqual(403);
            deleteResult = await deleteDataClass(dataTypeRowId, topFolderOptions, readerUserOptions);
            expect(deleteResult.status).toEqual(403);
            deleteResult = await deleteDataClass(dataTypeRowId, topFolderOptions, editorUserOptions);
            expect(deleteResult.status).toEqual(403);

            failedRemoveddataType = await getDataClassRowId(dataType, topFolderOptions);
            expect(failedRemoveddataType).toEqual(dataTypeRowId);

            //admin can delete design with data
            deleteResult = await deleteDataClass(dataTypeRowId, topFolderOptions, adminOptions);
            expect(deleteResult.status).toEqual(302);

            const removedDataType = await getDataClassRowId(dataType, topFolderOptions);
            expect(removedDataType).toEqual(0);
        });
    });

});

describe('Import with update / merge', () => {
   it ("Issue 52922: Blank sample id in the file are getting ignored in update from file", async () => {
       const BLANK_KEY_UPDATE_ERROR_NO_EXPRESSION = 'Missing value for required property: Name';
       const BLANK_KEY_UPDATE_ERROR_WITH_EXPRESSION = 'Name value not provided on row ';
       const BOGUS_KEY_UPDATE_ERROR = 'Data not found for ';
       const CROSS_FOLDER_UPDATE_NOT_SUPPORTED_ERROR = "Data doesn't belong to folder ";

       const dataType = "NoExpressionNameRequired52922";
       const createPayload = {
           kind: 'DataClass',
           domainDesign: { name: dataType, fields: [{ name: 'Prop' }] },
           options: {
               name: dataType,
           }
       };
       await server.post('property', 'createDomain', createPayload,
           {...topFolderOptions, ...designerReaderOptions}).expect(successfulResponse);
       const dataName = "Data1";
       await ExperimentCRUDUtils.insertRows(server, [{
           name: dataName,
           description: 'created'
       }], 'exp.data', dataType, topFolderOptions, editorUserOptions);

       // Issue 52922: Blank / bogus  id in the file are getting ignored in update from file
       let blankKeyProvidedError = await ExperimentCRUDUtils.importData(server, "Name\tDescription\nData1\tNotblank\n\tisBlank", dataType, "UPDATE", topFolderOptions, editorUserOptions);
       expect(blankKeyProvidedError.text.indexOf(BLANK_KEY_UPDATE_ERROR_NO_EXPRESSION) > -1).toBeTruthy();
       blankKeyProvidedError = await ExperimentCRUDUtils.importData(server, "Name\tDescription\nData1\tNotblank\n\tisBlank", dataType, "UPDATE", subfolder1Options, editorUserOptions);
       expect(blankKeyProvidedError.text.indexOf(BLANK_KEY_UPDATE_ERROR_NO_EXPRESSION) > -1).toBeTruthy();
       blankKeyProvidedError = await ExperimentCRUDUtils.importData(server, "Name\tDescription\n\tisBlank", dataType, "UPDATE", topFolderOptions, editorUserOptions);
       expect(blankKeyProvidedError.text.indexOf(BLANK_KEY_UPDATE_ERROR_NO_EXPRESSION) > -1).toBeTruthy();
       blankKeyProvidedError = await ExperimentCRUDUtils.importData(server, "Name\tDescription\nData1\tNotblank\n\tisBlank", dataType, "MERGE", topFolderOptions, editorUserOptions);
       expect(blankKeyProvidedError.text.indexOf(BLANK_KEY_UPDATE_ERROR_NO_EXPRESSION) > -1).toBeTruthy();
       blankKeyProvidedError = await ExperimentCRUDUtils.importData(server, "Name\tDescription\nData1\tNotblank\n\tisBlank", dataType, "MERGE", subfolder1Options, editorUserOptions);
       expect(blankKeyProvidedError.text.indexOf(BLANK_KEY_UPDATE_ERROR_NO_EXPRESSION) > -1).toBeTruthy();
       blankKeyProvidedError = await ExperimentCRUDUtils.importData(server, "Name\tDescription\n\tisBlank", dataType, "MERGE", topFolderOptions, editorUserOptions);
       expect(blankKeyProvidedError.text.indexOf(BLANK_KEY_UPDATE_ERROR_NO_EXPRESSION) > -1).toBeTruthy();
       // bogus name
       let bogusKeyProvidedError = await ExperimentCRUDUtils.importData(server, "Name\tDescription\nbogus\tisBogus", dataType, "UPDATE", topFolderOptions, editorUserOptions);
       expect(bogusKeyProvidedError.text.indexOf(BOGUS_KEY_UPDATE_ERROR) > -1).toBeTruthy();
       bogusKeyProvidedError = await ExperimentCRUDUtils.importData(server, "Name\tDescription\nData1\tNotblank\nbogus\tisBogus", dataType, "UPDATE", topFolderOptions, editorUserOptions);
       expect(bogusKeyProvidedError.text.indexOf(BOGUS_KEY_UPDATE_ERROR) > -1).toBeTruthy();

       const dataTypeWithExpression = "WithExpressionNameNotRequired52922";
       let createPayloadWithExpression = {
           kind: 'DataClass',
           domainDesign: { name: dataTypeWithExpression, fields: [{ name: 'Prop' }] },
           options: {
               name: dataTypeWithExpression,
               nameExpression: 'Src-${genId}',

           }
       };
       await server.post('property', 'createDomain', createPayloadWithExpression,
           {...topFolderOptions, ...designerReaderOptions}).expect(successfulResponse);
       await ExperimentCRUDUtils.insertRows(server, [{
           name: dataName,
           description: 'created'
       }], 'exp.data', dataTypeWithExpression, topFolderOptions, editorUserOptions);

       // blank name
       blankKeyProvidedError = await ExperimentCRUDUtils.importData(server, "Name\tDescription\nData1\tNotblank\n\tisBlank", dataTypeWithExpression, "UPDATE", topFolderOptions, editorUserOptions);
       expect(blankKeyProvidedError.text.indexOf(BLANK_KEY_UPDATE_ERROR_WITH_EXPRESSION + 2) > -1).toBeTruthy();
       blankKeyProvidedError = await ExperimentCRUDUtils.importData(server, "Name\tDescription\nData1\tNotblank\n\tisBlank", dataTypeWithExpression, "UPDATE", subfolder1Options, editorUserOptions);
       expect(blankKeyProvidedError.text.indexOf(BLANK_KEY_UPDATE_ERROR_WITH_EXPRESSION + 2) > -1).toBeTruthy();
       blankKeyProvidedError = await ExperimentCRUDUtils.importData(server, "Name\tDescription\n\tisBlank", dataTypeWithExpression, "UPDATE", topFolderOptions, editorUserOptions);
       expect(blankKeyProvidedError.text.indexOf(BLANK_KEY_UPDATE_ERROR_WITH_EXPRESSION + 1) > -1).toBeTruthy();

       // merge with blank name for data type with naming expression should not fail
       let successResp = await ExperimentCRUDUtils.importData(server, "Name\tDescription\nData1\tNotblank\n\tisBlank", dataTypeWithExpression, "MERGE", topFolderOptions, editorUserOptions);
       expect(successResp.text.indexOf('"success" : true') > -1).toBeTruthy();
       successResp = await ExperimentCRUDUtils.importData(server, "Name\tDescription\n\tisBlank", dataTypeWithExpression, "MERGE", topFolderOptions, editorUserOptions);
       expect(successResp.text.indexOf('"success" : true') > -1).toBeTruthy();
       successResp = await ExperimentCRUDUtils.importData(server, "Name\tDescription\n\tisBlank", dataTypeWithExpression, "MERGE", subfolder1Options, editorUserOptions);
       expect(successResp.text.indexOf('"success" : true') > -1).toBeTruthy();

       // cross folder update not supported when folder type is "Collaboration"
       let crossFolderErrorResp = await ExperimentCRUDUtils.importData(server, "Name\tDescription\nData1\tNotblank\n\tisBlank", dataTypeWithExpression, "MERGE", subfolder1Options, editorUserOptions);
       expect(crossFolderErrorResp.text.indexOf(CROSS_FOLDER_UPDATE_NOT_SUPPORTED_ERROR) > -1).toBeTruthy();
       crossFolderErrorResp = await ExperimentCRUDUtils.importData(server, "Name\tDescription\nData1\tNotblank", dataTypeWithExpression, "UPDATE", subfolder1Options, editorUserOptions);
       expect(crossFolderErrorResp.text.indexOf(CROSS_FOLDER_UPDATE_NOT_SUPPORTED_ERROR) > -1).toBeTruthy();

       // bogus name
       bogusKeyProvidedError = await ExperimentCRUDUtils.importData(server, "Name\tDescription\nbogus\tisBogus", dataTypeWithExpression, "UPDATE", topFolderOptions, editorUserOptions);
       expect(bogusKeyProvidedError.text.indexOf(BOGUS_KEY_UPDATE_ERROR) > -1).toBeTruthy();
       bogusKeyProvidedError = await ExperimentCRUDUtils.importData(server, "Name\tDescription\nData1\tNotblank\nbogus\tisBogus", dataTypeWithExpression, "UPDATE", topFolderOptions, editorUserOptions);
       expect(bogusKeyProvidedError.text.indexOf(BOGUS_KEY_UPDATE_ERROR) > -1).toBeTruthy();

       // merge with bogus name should create a new data and not fail
       successResp = await ExperimentCRUDUtils.importData(server, "Name\tDescription\nData1\tNotblank\nbogusShouldCreate\tisBogus", dataTypeWithExpression, "MERGE", topFolderOptions, editorUserOptions);
       expect(successResp.text.indexOf('"success" : true') > -1).toBeTruthy();

   });

});

describe('Data Class - Required Lineage', () => {
    it('Test dataclass with required dataclass parents', async () => {
        await verifyRequiredLineageInsertUpdate(server, false, false, topFolderOptions, subfolder1Options, designerReaderOptions, readerUserOptions, editorUserOptions, adminOptions);
    });

});

describe('Duplicate IDs', () => {

    it("Issue 52728: don't allow updating the same data twice", async () => {
        const dataType = "TestIssue52728";
        const createPayload = {
            kind: 'DataClass',
            domainDesign: { name: dataType, fields: [{ name: 'Prop' }] },
            options: {
                name: dataType,
            }
        };

        await server.post('property', 'createDomain', createPayload,
            {...topFolderOptions, ...designerReaderOptions}).expect(successfulResponse);

        let errorResp;
        await server.post('query', 'insertRows', {
            schemaName: 'exp.data',
            queryName: dataType,
            rows: [{
                name: 'duplicateShouldFail',
            },{
                name: 'duplicateShouldFail',
            }]
        }, { ...topFolderOptions, ...editorUserOptions }).expect((result) => {
            errorResp = JSON.parse(result.text);
            expect(errorResp.exception.indexOf('already exists') > -1).toBeTruthy();
        });
        // import
        errorResp = await ExperimentCRUDUtils.importData(server, "Name\tDescription\nduplicateShouldFail\tbad\nduplicateShouldFail\tbad", dataType, "IMPORT", topFolderOptions, editorUserOptions);
        expect(errorResp.text.indexOf('already exists') > -1).toBeTruthy();

        // merge
        const duplicateKeyErrorPrefix = 'Duplicate key provided: ';
        errorResp = await ExperimentCRUDUtils.importData(server, "Name\tDescription\nduplicateShouldFail\tbad\nduplicateShouldFail\tbad", dataType, "MERGE", topFolderOptions, editorUserOptions);
        expect(errorResp.text.indexOf(duplicateKeyErrorPrefix + 'duplicateShouldFail') > -1).toBeTruthy();

        const dataName1 = "up-dataId-1";
        const dataName2 = "up-dataId-2";
        const dataRows = await ExperimentCRUDUtils.insertRows(server, [{
            name: dataName1,
            description: 'created'
        },{
            name: dataName2,
            description: 'created'
        }], 'exp.data', dataType, topFolderOptions, editorUserOptions);

        const data1RowId = caseInsensitive(dataRows[0], 'rowId');
        const data1Lsid = caseInsensitive(dataRows[0], 'lsid');
        const data2RowId = caseInsensitive(dataRows[1], 'rowId');
        const data2Lsid = caseInsensitive(dataRows[1], 'lsid');

        // update data2 twice using updateRows, using rowId
        await server.post('query', 'updateRows', {
            schemaName: 'exp.data',
            queryName: dataType,
            rows: [{
                description: 'update',
                rowId: data1RowId
            },{
                description: 'update',
                rowId: data2RowId
            },{
                description: 'update',
                rowId: data2RowId
            }]
        }, { ...topFolderOptions, ...editorUserOptions }).expect((result) => {
            errorResp = JSON.parse(result.text);
            expect(errorResp['exception']).toBe('Duplicate key provided: ' + data2RowId);
        });

        // update data2 twice using updateRows, using lsid (data iterator)
        await server.post('query', 'updateRows', {
            schemaName: 'exp.data',
            queryName: dataType,
            rows: [{
                description: 'update',
                lsid: data1Lsid
            },{
                description: 'update',
                lsid: data2Lsid
            },{
                description: 'update',
                lsid: data2Lsid
            }]
        }, { ...topFolderOptions, ...editorUserOptions }).expect((result) => {
            errorResp = JSON.parse(result.text);
            expect(errorResp['exception']).toBe('Duplicate key provided: ' + data2Lsid);
        });

        errorResp = await ExperimentCRUDUtils.importData(server, "Name\tDescription\n" + dataName1 + "\tupdate\n" + dataName2 + "\tupdate\n" + dataName2 + "\tupdate", dataType, "UPDATE", topFolderOptions, editorUserOptions);
        expect(errorResp.text.indexOf('Duplicate key provided: ' + dataName2) > -1).toBeTruthy();

        errorResp = await ExperimentCRUDUtils.importData(server, "Name\tDescription\n" + dataName1 + "\tupdate\n" + dataName2 + "\tupdate\n" + dataName2 + "\tupdate", dataType, "MERGE", topFolderOptions, editorUserOptions);
        expect(errorResp.text.indexOf('Duplicate key provided: ' + dataName2) > -1).toBeTruthy();

        // confirm rows are not updated
        let dataResults = await ExperimentCRUDUtils.getRows(server, [data1RowId, data2RowId], 'exp.data', dataType, 'rowId,description', topFolderOptions, adminOptions);
        expect(caseInsensitive(dataResults[0], 'description')).toBe('created');
        expect(caseInsensitive(dataResults[1], 'description')).toBe('created');

    });

    it("Issue 52657: We shouldn't allow creating data names that differ only in case.", async () => {
        const dataType = "Type Case Sensitive";
        const createPayload = {
            kind: 'DataClass',
            domainDesign: { name: dataType, fields: [{ name: 'Prop' }] },
            options: {
                name: dataType,
                nameExpression: 'Src-${Prop}'
            }
        };

        await server.post('property', 'createDomain', createPayload,
            {...topFolderOptions, ...designerReaderOptions}).expect(successfulResponse);

        const NAME_EXIST_MSG = "The name '%%' already exists.";
        const data1 = 'Src-case-dAta1';
        const data2 = 'Src-case-dAta2';

        let insertRows = [{
            name: data1,
        },{
            name: data2,
        }];
        const dataRows = await ExperimentCRUDUtils.insertRows(server, insertRows, 'exp.data', dataType, topFolderOptions, editorUserOptions);
        const data1RowId = caseInsensitive(dataRows[0], 'rowId');
        const data1Lsid = caseInsensitive(dataRows[0], 'lsid');
        const data2RowId = caseInsensitive(dataRows[1], 'rowId');
        const data2Lsid = caseInsensitive(dataRows[1], 'lsid');

        let expectedError = NAME_EXIST_MSG.replace('%%', 'Src-case-data1');
        // import
        let errorResp = await ExperimentCRUDUtils.importData(server, "Name\tDescription\nSrc-case-data1\tbad\nSrc-case-data2\tbad", dataType, "IMPORT", topFolderOptions, editorUserOptions);
        expect(errorResp.text).toContain(expectedError);

        // merge
        let mergeError = 'The name \'Src-case-data1\' could not be resolved. Please check the casing of the provided name.';
        errorResp = await ExperimentCRUDUtils.importData(server, "Name\tDescription\nSrc-case-data1\tbad\nSrc-case-data2\tbad", dataType, "MERGE", topFolderOptions, editorUserOptions);
        expect(errorResp.text).toContain(mergeError);

        // insert
        await server.post('query', 'insertRows', {
            schemaName: 'exp.data',
            queryName: dataType,
            rows: [{
                name: 'Src-case-data1',
            }]
        }, { ...topFolderOptions, ...editorUserOptions }).expect((result) => {
            const errorResp = JSON.parse(result.text);
            expect(errorResp['exception']).toBe(expectedError);
        });

        // insert using naming expression to create case-insensitive name
        await server.post('query', 'insertRows', {
            schemaName: 'exp.data',
            queryName: dataType,
            rows: [{
                prop: 'case-data1',
            }]
        }, { ...topFolderOptions, ...editorUserOptions }).expect((result) => {
            const errorResp = JSON.parse(result.text);
            expect(errorResp['exception']).toBe(expectedError);
        });

        // renaming data to another data's name, using rowId
        await server.post('query', 'updateRows', {
            schemaName: 'exp.data',
            queryName: dataType,
            rows: [{
                name: 'Src-case-dAta2',
                rowId: data1RowId
            }]
        }, { ...topFolderOptions, ...editorUserOptions }).expect((result) => {
            errorResp = JSON.parse(result.text);
            expect(errorResp['exception']).toBe(NAME_EXIST_MSG.replace('%%', 'Src-case-dAta2'));
        });

        // renaming data to another data's case-insensitive name, using rowId
        await server.post('query', 'updateRows', {
            schemaName: 'exp.data',
            queryName: dataType,
            rows: [{
                name: 'Src-case-data2',
                rowId: data1RowId
            }]
        }, { ...topFolderOptions, ...editorUserOptions }).expect((result) => {
            errorResp = JSON.parse(result.text);
            expect(errorResp['exception']).toBe(NAME_EXIST_MSG.replace('%%', 'Src-case-data2'));
        });

        // renaming data to another data's case-insensitive name, using lsid. Currently can only be done using api
        await server.post('query', 'updateRows', {
            schemaName: 'exp.data',
            queryName: dataType,
            rows: [{
                name: 'Src-case-data2',
                lsid: data1Lsid
            }]
        }, { ...topFolderOptions, ...editorUserOptions }).expect((result) => {
            errorResp = JSON.parse(result.text);
            expect(errorResp['exception']).toBe(NAME_EXIST_MSG.replace('%%', 'Src-case-data2'));
        });

        // swap names (fail)
        await server.post('query', 'updateRows', {
            schemaName: 'exp.data',
            queryName: dataType,
            rows: [{
                name: 'Src-case-data2',
                lsid: data1Lsid
            }, {
                name: 'Src-case-data1',
                lsid: data2Lsid
            }]
        }, { ...topFolderOptions, ...editorUserOptions }).expect((result) => {
            errorResp = JSON.parse(result.text);
            expect(errorResp['exception']).toBe(NAME_EXIST_MSG.replace('%%', 'Src-case-data2'));
        });

        await server.post('query', 'updateRows', {
            schemaName: 'exp.data',
            queryName: dataType,
            rows: [{
                name: 'Src-case-data2',
                rowId: data1RowId
            }, {
                name: 'Src-case-data1',
                rowId: data2RowId
            }]
        }, { ...topFolderOptions, ...editorUserOptions }).expect((result) => {
            errorResp = JSON.parse(result.text);
            expect(errorResp['exception']).toBe(NAME_EXIST_MSG.replace('%%', 'Src-case-data2'));
        });

        // renaming data to its case-insensitive name, using rowId
        let results = await ExperimentCRUDUtils.updateRows(server, [{name: 'SRC-CASE-data1', rowId: data1RowId}], 'exp.data', dataType, topFolderOptions, editorUserOptions);
        expect(caseInsensitive(results[0], 'Name')).toBe('SRC-CASE-data1');

        // renaming data to its case-insensitive name, using lsid
        results = await ExperimentCRUDUtils.updateRows(server, [{name: 'src-case-DATA1', lsid: data1Lsid}], 'exp.data', dataType, topFolderOptions, editorUserOptions);
        expect(caseInsensitive(results[0], 'Name')).toBe('src-case-DATA1');

    });

});