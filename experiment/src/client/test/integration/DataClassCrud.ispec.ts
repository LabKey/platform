import {
    ExperimentCRUDUtils,
    generateDomainName,
    getEscapedNameExpression,
    hookServer,
    RequestOptions,
    successfulResponse
} from '@labkey/test';
import mock from 'mock-fs';
import {
    checkDomainName,
    checkLackDesignerOrReaderPerm,
    createSource,
    deleteSourceType,
    generateFieldNameForImport,
    getDataClassRowIdByName,
    initProject,
    MVTC_FIELD_PROP,
    TC_FIELD_PROP,
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


// TODO: move utils to ExperimentCRUDUtils
export async function insertDataClassData(
    rows: Record<string, any>[],
    dataType: string,
    folderOptions: RequestOptions = topFolderOptions,
): Promise<{ lsid: string; name: string; rowId: number }[]> {
    const resultRows = await ExperimentCRUDUtils.insertRows(
        server,
        rows,
        'exp.data',
        dataType,
        folderOptions,
        editorUserOptions,
    );

    return resultRows.map(row => ({
        lsid: caseInsensitive(row, 'lsid'),
        name: caseInsensitive(row, 'name'),
        rowId: caseInsensitive(row, 'rowId'),
    }));
}
export async function getDataClassDataByName(dataName: string, queryName: string, columns: string = 'Name, RowId', folderOptions: RequestOptions , userOptions: RequestOptions, debug?: boolean) : Promise<any> {
    const response = await server.post('query', 'selectRows', {
        schemaName: 'exp.data',
        queryName,
        'query.Name~eq': dataName,
        'query.columns': columns,
    }, { ...folderOptions, ...userOptions }).expect(successfulResponse);
    if (debug)
        console.log(response);
    return response.body.rows[0];
}


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
       const DUPLICATE_KEY_ERROR = 'duplicate key value';

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
       expect(blankKeyProvidedError.text.indexOf(BLANK_KEY_UPDATE_ERROR_WITH_EXPRESSION) > -1).toBeTruthy();
       blankKeyProvidedError = await ExperimentCRUDUtils.importData(server, "Name\tDescription\nData1\tNotblank\n\tisBlank", dataType, "UPDATE", subfolder1Options, editorUserOptions);
       expect(blankKeyProvidedError.text.indexOf(BLANK_KEY_UPDATE_ERROR_WITH_EXPRESSION) > -1).toBeTruthy();
       blankKeyProvidedError = await ExperimentCRUDUtils.importData(server, "Name\tDescription\n\tisBlank", dataType, "UPDATE", topFolderOptions, editorUserOptions);
       expect(blankKeyProvidedError.text.indexOf(BLANK_KEY_UPDATE_ERROR_WITH_EXPRESSION) > -1).toBeTruthy();
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
       expect(crossFolderErrorResp.text.indexOf(DUPLICATE_KEY_ERROR) > -1).toBeTruthy();
       crossFolderErrorResp = await ExperimentCRUDUtils.importData(server, "Name\tDescription\nData1\tNotblank", dataTypeWithExpression, "UPDATE", subfolder1Options, editorUserOptions);
       expect(crossFolderErrorResp.text.indexOf(BOGUS_KEY_UPDATE_ERROR) > -1).toBeTruthy();

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
            expect(errorResp.exception.indexOf('duplicate key') > -1).toBeTruthy();
        });
        // import
        errorResp = await ExperimentCRUDUtils.importData(server, "Name\tDescription\nduplicateShouldFail\tbad\nduplicateShouldFail\tbad", dataType, "IMPORT", topFolderOptions, editorUserOptions);
        expect(errorResp.text.indexOf('duplicate key') > -1).toBeTruthy();

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
        const data2RowId = caseInsensitive(dataRows[1], 'rowId');

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

        // update data twice specifying the name across multiple partitions
        await server.post('query', 'updateRows', {
            schemaName: 'exp.data',
            queryName: dataType,
            rows: [{
                description: 'update',
                name: dataName1
            },{
                description: 'update',
                rowId: data2RowId
            },{
                description: 'update',
                name: dataName1
            }]
        }, { ...topFolderOptions, ...editorUserOptions }).expect((result) => {
            errorResp = JSON.parse(result.text);
            expect(errorResp['exception']).toBe('Duplicate key provided: ' + dataName1);
        });

        // update data twice specifying the rowId across multiple partitions
        await server.post('query', 'updateRows', {
            schemaName: 'exp.data',
            queryName: dataType,
            rows: [{
                description: 'update',
                rowId: data1RowId
            },{
                description: 'update',
                name: dataName2
            },{
                description: 'update',
                rowId: data1RowId
            }]
        }, { ...topFolderOptions, ...editorUserOptions }).expect((result) => {
            errorResp = JSON.parse(result.text);
            expect(errorResp['exception']).toBe('Duplicate key provided: ' + data1RowId);
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
});


describe('Multi Value Text Choice', () => {



    it("MVTC CRUD", async () => {
        let supportMultiChoice = false;
        const createTestPayload = {
            kind: 'DataClass',
            domainDesign: { name: 'Test_mvtc_support_check', fields: [{ name: 'Prop' }] },
            options: {
                name: "Test_mvtc_support_check",
            }
        };

        await server.post('property', 'createDomain', createTestPayload,
            {...topFolderOptions, ...designerReaderOptions}).expect((result) => {
            const domain = JSON.parse(result.text);
            supportMultiChoice = domain.allowMultiChoiceProperties;
            return true;
        });

        const dataType = 'MVTCReq Source Type';
        const fieldName = generateFieldNameForImport();
        const fieldNameInExpression = getEscapedNameExpression((fieldName));
        console.log("Selected Required MVTC dataclass name: " + dataType + ", field name: " + fieldName);

        const fields = [
            {
                ...MVTC_FIELD_PROP,
                name: fieldName
            }
        ];

        let domainId = -1, domainURI = '', propertyId, propertyURI;
        const createPayload = {
            kind: 'DataClass',
            domainDesign: { name: dataType, fields },
            options: {
                name: dataType,
                nameExpression: 'Src-${' + fieldNameInExpression + '}'
            }
        };

        if (!supportMultiChoice) {
            const failedCreateDomain = await server.post('property', 'createDomain', createPayload,
                {...topFolderOptions, ...adminOptions});

            expect(failedCreateDomain?.['body']?.['exception']).toContain('does not support multiple values.');

            console.warn("Multi Value Text Choice not supported, skipping test");
            return;
        }

        await server.post('property', 'createDomain', createPayload, {...topFolderOptions, ...designerReaderOptions}).expect((result) => {
            const domain = JSON.parse(result.text);
            domainId = domain.domainId;
            domainURI = domain.domainURI;
            const field = domain.fields[0];
            propertyId = field.propertyId;
            propertyURI = field.propertyURI;
            return true;
        });

        let dataCount = 0;

        // import invalid mvtc values, verify import should fail
        let errorResp = await ExperimentCRUDUtils.importData(server, "Name\t" + fieldName + "\tDescription\nS-" + dataCount++ + "\ta,x\timport invalid mvtc", dataType, "IMPORT", topFolderOptions, editorUserOptions);
        expect(errorResp.text.indexOf("Value 'a, x' for field") > -1).toBeTruthy();
        errorResp = await ExperimentCRUDUtils.importData(server, "Name\t" + fieldName + "\tDescription\nS-" + dataCount++ + "\tabc\timport invalid mvtc", dataType, "IMPORT", topFolderOptions, editorUserOptions);
        expect(errorResp.text.indexOf("Value 'abc' for field") > -1).toBeTruthy();

        // insert data using insertRows api, with invalid mvtc value
        const invalidValues = ['x', 'a, x', 'x, y', ['x'], ['agent', 'x'], ['x', 'y']];
        invalidValues.forEach( async (val) => {
            await server.post('query', 'insertRows', {
                schemaName: 'exp.data',
                queryName: dataType,
                rows: [{
                    name: 'invalid',
                    [fieldName]: val
                }]
            }, { ...topFolderOptions, ...editorUserOptions }).expect((result) => {
                const errorResp = JSON.parse(result.text);
                expect(errorResp['exception']).toContain('is invalid.');
            });
        });

        // insert data using insertRows api, with no mvtc value
        let inserted = await insertDataClassData(
            [
                { 'name': 'S-' + dataCount++, 'description': 'no column' },
                { 'name': 'S-' + dataCount++, 'description': 'null column', [fieldName]: null },
                { 'name': 'S-' + dataCount++, 'description': 'blank column', [fieldName]: '' },
                { 'name': 'S-' + dataCount++, 'description': 'empty array', [fieldName]: [] },
            ], dataType, topFolderOptions
        );
        inserted.forEach(async (row) => {
            let res = await ExperimentCRUDUtils.getRows(server, [caseInsensitive(row, 'RowId')], 'exp.data', dataType, '*', topFolderOptions, adminOptions);
            expect(caseInsensitive(res[0], fieldName)).toEqual([]);
        });

        // import data with no mvtc column
        let importText = "Name\tDescription\n";
        let dataNameImported = ["S-" + dataCount++];
        importText += dataNameImported + "\timport no column\n";
        await ExperimentCRUDUtils.importData(server, importText, dataType, "IMPORT", topFolderOptions, editorUserOptions);
        let result = await getDataClassDataByName(dataNameImported[0], dataType, '*', topFolderOptions, editorUserOptions);
        expect(caseInsensitive(result, fieldName)).toEqual([]);

        // import data with mvtc fieldname column header, with blank, single, multi values
        dataNameImported = ["S-" + dataCount++, "S-" + dataCount++, "S-" + dataCount++];
        importText = "Name\t" + fieldName + "\tDescription\n";
        importText += dataNameImported[0] + "\t\timport blank column\n";
        importText += dataNameImported[1] + "\tagent\timport single value\n";
        importText += dataNameImported[2] + "\tPlasma, agent, cDNA, Abnormal\timport multi values\n";
        await ExperimentCRUDUtils.importData(server, importText, dataType, "IMPORT", topFolderOptions, editorUserOptions);
        const dataImportedRowIds = [];
        // verify imported data mvtc
        result = await getDataClassDataByName(dataNameImported[0], dataType, '*', topFolderOptions, editorUserOptions);
        expect(caseInsensitive(result, fieldName)).toEqual([]);
        dataImportedRowIds.push(caseInsensitive(result, 'rowId'));
        result = await getDataClassDataByName(dataNameImported[1], dataType, '*', topFolderOptions, editorUserOptions);
        expect(caseInsensitive(result, fieldName)).toEqual(['agent']);
        dataImportedRowIds.push(caseInsensitive(result, 'rowId'));
        result = await getDataClassDataByName(dataNameImported[2], dataType, '*', topFolderOptions, editorUserOptions);
        expect(caseInsensitive(result, fieldName)).toEqual(['Abnormal', 'agent', 'cDNA', 'Plasma']);
        dataImportedRowIds.push(caseInsensitive(result, 'rowId'));

        // update data using updateRows api, mvtc column absent from update
        await ExperimentCRUDUtils.updateRows(server, [
            { 'rowId': dataImportedRowIds[0], 'description': 'no update' },
            { 'rowId': dataImportedRowIds[1], 'description': 'null column' },
            { 'rowId': dataImportedRowIds[2], 'description': 'blank column' },
        ], 'exp.data', dataType, topFolderOptions, editorUserOptions);

        result = await getDataClassDataByName(dataNameImported[0], dataType, '*', topFolderOptions, editorUserOptions);
        expect(caseInsensitive(result, fieldName)).toEqual([]);
        result = await getDataClassDataByName(dataNameImported[1], dataType, '*', topFolderOptions, editorUserOptions);
        expect(caseInsensitive(result, fieldName)).toEqual(['agent']);
        result = await getDataClassDataByName(dataNameImported[2], dataType, '*', topFolderOptions, editorUserOptions);
        expect(caseInsensitive(result, fieldName)).toEqual(['Abnormal', 'agent', 'cDNA', 'Plasma']);

        // update data using updateRows api, mvtc column blank or empty or empty array
        await ExperimentCRUDUtils.updateRows(server, [
            { 'rowId': dataImportedRowIds[0], [fieldName]: '' },
            { 'rowId': dataImportedRowIds[1], [fieldName]: null },
            { 'rowId': dataImportedRowIds[2], [fieldName]: [] },
        ], 'exp.data', dataType, topFolderOptions, editorUserOptions);

        result = await getDataClassDataByName(dataNameImported[0], dataType, '*', topFolderOptions, editorUserOptions);
        expect(caseInsensitive(result, fieldName)).toEqual([]);
        result = await getDataClassDataByName(dataNameImported[1], dataType, '*', topFolderOptions, editorUserOptions);
        expect(caseInsensitive(result, fieldName)).toEqual([]);
        result = await getDataClassDataByName(dataNameImported[2], dataType, '*', topFolderOptions, editorUserOptions);
        expect(caseInsensitive(result, fieldName)).toEqual([]);

        // values are Abnormal|agent|cDNA|Plasma
        const singleValues = ['Abnormal', 'cDNA', ['Abnormal'], ['cDNA']];
        const expectedSingleResults = [['Abnormal'], ['cDNA'], ['Abnormal'], ['cDNA']];
        for (let i = 0; i < singleValues.length; i++) {
            const val = singleValues[i];
            const expected = expectedSingleResults[i];
            await ExperimentCRUDUtils.updateRows(server, [
                    { 'rowId': dataImportedRowIds[0], [fieldName]: val },
                ], 'exp.data', dataType, topFolderOptions, editorUserOptions
            );
            result = await getDataClassDataByName(dataNameImported[0], dataType, '*', topFolderOptions, editorUserOptions);
            expect(caseInsensitive(result, fieldName)).toEqual(expected);
        }

        const multiValues = ['agent, cDNA', 'cDNA, Plasma, Abnormal', ['agent', 'cDNA'], ['agent', 'Abnormal', 'cDNA']];
        const expectedMultiResults = [['agent', 'cDNA'], ['Abnormal', 'cDNA', 'Plasma'], ['agent', 'cDNA'], ['Abnormal', 'agent', 'cDNA']];
        for (let i = 0; i < multiValues.length; i++) {
            const val = multiValues[i];
            const expected = expectedMultiResults[i];
            await ExperimentCRUDUtils.updateRows(server, [
                    { 'rowId': dataImportedRowIds[1], [fieldName]: val },
                ], 'exp.data', dataType, topFolderOptions, editorUserOptions
            );
            result = await getDataClassDataByName(dataNameImported[1], dataType, '*', topFolderOptions, editorUserOptions);
            expect(caseInsensitive(result, fieldName)).toEqual(expected);
        }

        // update with import to set mvtc from non blank to blank
        importText = "Name\t" + fieldName + "\tDescription\n";
        importText += dataNameImported[0] + "\t\timport blank column\n";
        importText += dataNameImported[1] + "\t\timport single value\n";
        await ExperimentCRUDUtils.importData(server, importText, dataType, "UPDATE", topFolderOptions, editorUserOptions);
        result = await getDataClassDataByName(dataNameImported[0], dataType, '*', topFolderOptions, editorUserOptions);
        expect(caseInsensitive(result, fieldName)).toEqual([]);
        result = await getDataClassDataByName(dataNameImported[1], dataType, '*', topFolderOptions, editorUserOptions);
        expect(caseInsensitive(result, fieldName)).toEqual([]);

        // update with import to set mvtc from blank to multi values
        importText = "Name\t" + fieldName + "\tDescription\n";
        importText += dataNameImported[0] + "\tAbnormal, agent\timport multi value\n";
        importText += dataNameImported[1] + "\tcDNA, Plasma, Abnormal\timport multi values\n";
        await ExperimentCRUDUtils.importData(server, importText, dataType, "UPDATE", topFolderOptions, editorUserOptions);
        result = await getDataClassDataByName(dataNameImported[0], dataType, '*', topFolderOptions, editorUserOptions);
        expect(caseInsensitive(result, fieldName)).toEqual(['Abnormal', 'agent']);
        result = await getDataClassDataByName(dataNameImported[1], dataType, '*', topFolderOptions, editorUserOptions);
        expect(caseInsensitive(result, fieldName)).toEqual(['Abnormal', 'cDNA', 'Plasma']);

        // merge with import to change mvtc and create new data
        const newMerged = 'S-' + dataCount++;
        importText = "Name\t" + fieldName + "\tDescription\n";
        importText += dataNameImported[0] + "\tPlasma, Abnormal\timport multi value\n";
        importText += newMerged + "\tagent, cDNA\timport multi values\n";
        await ExperimentCRUDUtils.importData(server, importText, dataType, "MERGE", topFolderOptions, editorUserOptions);
        result = await getDataClassDataByName(dataNameImported[0], dataType, '*', topFolderOptions, editorUserOptions);
        expect(caseInsensitive(result, fieldName)).toEqual(['Abnormal', 'Plasma']);
        result = await getDataClassDataByName(newMerged, dataType, '*', topFolderOptions, editorUserOptions);
        expect(caseInsensitive(result, fieldName)).toEqual(['agent', 'cDNA']);

        // insert using insertRows api, with single mvtc value
        let toInsert = [];
        for (let i = 0; i < singleValues.length; i++) {
            const val = singleValues[i];
            toInsert.push({ 'name': 'S-' + dataCount++, [fieldName]: val , 'description': 'insert single value' });
        }

        inserted = await ExperimentCRUDUtils.insertRows(server, toInsert, 'exp.data', dataType, topFolderOptions, editorUserOptions);

        for (let i = 0; i < singleValues.length; i++) {
            const expected = expectedSingleResults[i];
            let res = await ExperimentCRUDUtils.getSourcesData(server, [caseInsensitive(inserted[i], 'RowId')], dataType, '*', topFolderOptions, adminOptions);
            expect(caseInsensitive(res[0], fieldName)).toEqual(expected);
        }

        // insert using insertRows api, with multi mvtc values
        toInsert = [];
        for (let i = 0; i < multiValues.length; i++) {
            const val = multiValues[i];
            toInsert.push({ 'name': 'S-' + dataCount++, [fieldName]: val , 'description': 'insert multi values' });
        }

        inserted = await ExperimentCRUDUtils.insertRows(server, toInsert, 'exp.data', dataType, topFolderOptions, editorUserOptions);

        for (let i = 0; i < multiValues.length; i++) {
            const expected = expectedMultiResults[i];
            let res = await ExperimentCRUDUtils.getSourcesData(server, [caseInsensitive(inserted[i], 'RowId')], dataType, '*', topFolderOptions, adminOptions);
            expect(caseInsensitive(res[0], fieldName)).toEqual(expected);
        }

        // verify convert to required column fails with existing blank values
        let dataClassRowId = await getDataClassRowIdByName(server, dataType, topFolderOptions)

        let updatePayload: any = {
            domainId,
            domainDesign: {
                name: dataType,
                fields: [
                    {
                        ...MVTC_FIELD_PROP,
                        name: fieldName,
                        required: true
                    }
                ],
                domainId,
                domainURI
            },
            options: {
                rowId: dataClassRowId,
                name: dataType,
                nameExpression: 'Src-${' + fieldNameInExpression + '}'
            }
        };
        let failedUpdate = await server.post('property', 'saveDomain', updatePayload, {...topFolderOptions, ...adminOptions});
        expect(failedUpdate?.['body']?.['exception']).toContain('cannot be required when it contains rows with blank values.');

        // verify convert to single value text choice fails with existing multiple values
        updatePayload = {
            domainId,
            domainDesign: {
                name: dataType,
                fields: [
                    {
                        ...TC_FIELD_PROP,
                        name: fieldName,
                        propertyId,
                        propertyURI
                    }
                ],
                domainId,
                domainURI
            },
            options: {
                rowId: dataClassRowId,
                name: dataType,
                nameExpression: 'S-${' + fieldNameInExpression + '}'
            }
        };
        failedUpdate = await server.post('property', 'saveDomain', updatePayload, {...topFolderOptions, ...adminOptions});
        expect(failedUpdate?.['body']?.['exception']).toContain('Unable to change property type. There are rows with multiple values stored for');

        // verify can convert to Text field type
        updatePayload = {
            domainId,
            domainDesign: {
                name: dataType,
                fields: [
                    {
                        name: fieldName,
                        propertyId,
                        propertyURI
                    }
                ],
                domainId,
                domainURI
            },
            options: {
                rowId: dataClassRowId,
                name: dataType,
                nameExpression: 'S-${' + fieldNameInExpression + '}'
            }
        };
        await server.post('property', 'saveDomain', updatePayload, {...topFolderOptions, ...adminOptions}).expect(successfulResponse);
        result = await getDataClassDataByName(dataNameImported[0], dataType, '*', topFolderOptions, editorUserOptions);
        expect(caseInsensitive(result, fieldName)).toEqual('Abnormal, Plasma'); // convert from ['Abnormal', 'Plasma'] to 'Abnormal, Plasma'


        const textChoiceMultiLineOption = {
            propertyValidators: [
                {
                    "type": "TextChoice",
                    "name": "Text Choice Validator",
                    "new": true,
                    "expression": "Abnormal|multi\nline|cDNA|Plasma"
                }
            ],
            rangeURI: 'http://www.w3.org/2001/XMLSchema#string',
            conceptURI: 'http://www.labkey.org/types#textChoice',
        }

        const textMultiChoiceMultiLineOption = {
            propertyValidators: [
                {
                    "type": "TextChoice",
                    "name": "Text Choice Validator",
                    "new": true,
                    "expression": "Abnormal|multi\nline|cDNA|Plasma"
                }
            ],
            rangeURI: "http://cpas.fhcrc.org/exp/xml#multiChoice",
        }

        // GitHub Issue 951: Multi-line values converted to text choices lose multi-line editability
        // verify cannot convert MultiLine field to MultiValue Text Choice
        updatePayload = {
            domainId,
            domainDesign: {
                name: dataType,
                fields: [
                    {
                        ...textChoiceMultiLineOption,
                        name: fieldName,
                        propertyId,
                        propertyURI,
                    }
                ],
                domainId,
                domainURI
            },
            options: {
                rowId: dataClassRowId,
                name: dataType,
                nameExpression: 'S-${' + fieldNameInExpression + '}'
            }
        };
        failedUpdate = await server.post('property', 'saveDomain', updatePayload, {...topFolderOptions, ...adminOptions});
        expect(failedUpdate?.['body']?.['exception']).toContain('must not be multi-line:');

        // verify cannot convert MultiLine field to Text Choice field
        updatePayload = {
            domainId,
            domainDesign: {
                name: dataType,
                fields: [
                    {
                        ...textMultiChoiceMultiLineOption,
                        name: fieldName,
                        propertyId,
                        propertyURI,
                    }
                ],
                domainId,
                domainURI
            },
            options: {
                rowId: dataClassRowId,
                name: dataType,
                nameExpression: 'S-${genId}'
            }
        };
        failedUpdate = await server.post('property', 'saveDomain', updatePayload, {...topFolderOptions, ...adminOptions});
        expect(failedUpdate?.['body']?.['exception']).toContain('must not be multi-line:');

    });

});

const LSID_UPDATE_ERROR = 'LSID is no longer accepted as a key for data update. Specify a RowId or Name instead.';
const LSID_MERGE_ERROR = 'LSID is no longer accepted as a key for data merge. Specify a RowId or Name instead.';
const ROWID_MERGE_ERROR = 'RowId is not accepted when merging data. Specify only the data name instead.';

describe('Data CRUD', () => {

    it ("Update using different key fields", async () => {
        const dataType = generateDomainName(3) + "UpdateKeyFields";
        const fieldName = generateFieldNameForImport();
        console.log("Selected dataclass name: " + dataType + ", field name: " + fieldName);

        // create data class with one field
        await server.post('property', 'createDomain', {
            kind: 'DataClass',
            domainDesign: { name: dataType, fields: [{ name: fieldName }] },
            options: { name: dataType }
        }, { ...topFolderOptions, ...designerReaderOptions }).expect(successfulResponse);

        // insert 2 rows data, provide explicit names and a rowId = -1
        const dataName1 = 'KeyData1';
        const dataName2 = 'KeyData2';
        const dataName3 = 'KeyData3';
        const inserted = await insertDataClassData([
            { name: dataName1, description: 'original1', [fieldName]: 'val1', rowId: -1 },
            { name: dataName2, description: 'original2', [fieldName]: 'val2', rowId: -1 },
            { name: dataName3, description: 'original3', [fieldName]: 'val3', rowId: -1 },
        ], dataType, topFolderOptions);

        // verify both rows are inserted with correct name and rowId is not -1 for both rows, record the rowId and lsid for both rows
        expect(inserted[0].name).toBe(dataName1);
        expect(inserted[1].name).toBe(dataName2);
        expect(inserted[2].name).toBe(dataName3);
        expect(inserted[0].rowId).not.toBe(-1);
        expect(inserted[1].rowId).not.toBe(-1);
        expect(inserted[2].rowId).not.toBe(-1);
        const row1RowId = inserted[0].rowId;
        const row1Lsid = inserted[0].lsid;
        const row2RowId = inserted[1].rowId;
        const row2Lsid = inserted[1].lsid;
        const row3RowId = inserted[2].rowId;

        const findRow = (rows: any[], rowId: number) => rows.find(r => caseInsensitive(r, 'RowId') === rowId);

        // update description and fieldName value for both rows using rowId as key, verify update is successful and data are updated correctly
        await ExperimentCRUDUtils.updateRows(server, [
            { rowId: row1RowId, description: 'updByRowId1', [fieldName]: 'rowIdVal1' },
            { rowId: row2RowId, description: 'updByRowId2', [fieldName]: 'rowIdVal2' },
        ], 'exp.data', dataType, topFolderOptions, editorUserOptions);

        let rows = await ExperimentCRUDUtils.getRows(server, [row1RowId, row2RowId], 'exp.data', dataType, '*', topFolderOptions, adminOptions);
        let row1 = findRow(rows, row1RowId);
        let row2 = findRow(rows, row2RowId);
        expect(caseInsensitive(row1, 'description')).toBe('updByRowId1');
        expect(caseInsensitive(row1, fieldName)).toBe('rowIdVal1');
        expect(caseInsensitive(row2, 'description')).toBe('updByRowId2');
        expect(caseInsensitive(row2, fieldName)).toBe('rowIdVal2');

        // Error when supplying LSID without RowId or Name
        // query api
        await server.post('query', 'updateRows', {
            schemaName: 'exp.data',
            queryName: dataType,
            rows: [
                { lsid: row1Lsid, description: 'updByLsid1', [fieldName]: 'lsidVal1' },
                { lsid: row2Lsid, description: 'updByLsid2', [fieldName]: 'lsidVal2' },
            ]
        }, { ...topFolderOptions, ...editorUserOptions }).expect((result) => {
            const errorResp = JSON.parse(result.text);
            expect(errorResp['exception']).toContain(LSID_UPDATE_ERROR);
        });
        // update from import
        let importUpdateText = 'LSID\tDescription\t' + fieldName + '\n' + row1Lsid + '\timportUpd1\timportLsidVal1\n' + row2Lsid + '\timportUpd2\timportLsidVal2';
        let errorResp = await ExperimentCRUDUtils.importData(server, importUpdateText, dataType, "UPDATE", topFolderOptions, editorUserOptions);
        expect(errorResp.text.indexOf(LSID_UPDATE_ERROR) > -1).toBeTruthy();

        // merge from import
        errorResp = await ExperimentCRUDUtils.importData(server, importUpdateText, dataType, "MERGE", topFolderOptions, editorUserOptions);
        expect(errorResp.text.indexOf(LSID_MERGE_ERROR) > -1).toBeTruthy();

        // update using lsid (correct and incorrect, should both be ignored), as well as rowId, as key, should succeed, verify update is successful and data are updated correctly
        await ExperimentCRUDUtils.updateRows(server, [
            { lsid: row1Lsid, rowId: row1RowId, description: 'updByLsid1', [fieldName]: 'lsidVal1' },
            { lsid: row1Lsid /*wrong lsid, should be ignored anyways*/, rowId: row2RowId, description: 'updByLsid2', [fieldName]: 'lsidVal2' },
        ], 'exp.data', dataType, topFolderOptions, editorUserOptions);

        rows = await ExperimentCRUDUtils.getRows(server, [row1RowId, row2RowId], 'exp.data', dataType, '*', topFolderOptions, adminOptions);
        row1 = findRow(rows, row1RowId);
        row2 = findRow(rows, row2RowId);
        expect(caseInsensitive(row1, 'description')).toBe('updByLsid1');
        expect(caseInsensitive(row1, fieldName)).toBe('lsidVal1');
        expect(caseInsensitive(row2, 'description')).toBe('updByLsid2');
        expect(caseInsensitive(row2, fieldName)).toBe('lsidVal2');
        expect(caseInsensitive(row2, 'lsid')).toBe(row2Lsid); // lsid should not be updated

        // update with different set of columns
        // should use partitioned data iterator
        await ExperimentCRUDUtils.updateRows(server, [
            { rowId: row1RowId, description: 'updMixed1', [fieldName]: 'mixedVal1' },
            { rowId: row2RowId, name: 'mixed_rename2', [fieldName]: 'mixedVal2' },
            { rowId: row3RowId, description: 'mixedVal3 desc' },
        ], 'exp.data', dataType, topFolderOptions, editorUserOptions);

        rows = await ExperimentCRUDUtils.getRows(server, [row1RowId, row2RowId, row3RowId], 'exp.data', dataType, '*', topFolderOptions, adminOptions);
        row1 = findRow(rows, row1RowId);
        row2 = findRow(rows, row2RowId);
        var row3 = findRow(rows, row3RowId);
        expect(caseInsensitive(row1, 'description')).toBe('updMixed1');
        expect(caseInsensitive(row1, fieldName)).toBe('mixedVal1');
        expect(caseInsensitive(row2, 'description')).toBe('updByLsid2');
        expect(caseInsensitive(row2, fieldName)).toBe('mixedVal2');
        expect(caseInsensitive(row2, 'name')).toBe('mixed_rename2');
        expect(caseInsensitive(row3, 'description')).toBe('mixedVal3 desc');
        expect(caseInsensitive(row3, fieldName)).toBe('val3'); // fieldName value should not be updated for row3

        // update using name as key, should succeed, verify update is successful and data are updated correctly
        await ExperimentCRUDUtils.updateRows(server, [
            { name: dataName1, description: 'updByName1', [fieldName]: 'nameVal1' },
            { name: 'mixed_rename2', description: 'updByName2', [fieldName]: 'nameVal2' },
        ], 'exp.data', dataType, topFolderOptions, editorUserOptions);

        rows = await ExperimentCRUDUtils.getRows(server, [row1RowId, row2RowId], 'exp.data', dataType, '*', topFolderOptions, adminOptions);
        row1 = findRow(rows, row1RowId);
        row2 = findRow(rows, row2RowId);
        expect(caseInsensitive(row1, 'description')).toBe('updByName1');
        expect(caseInsensitive(row1, fieldName)).toBe('nameVal1');
        expect(caseInsensitive(row2, 'description')).toBe('updByName2');
        expect(caseInsensitive(row2, fieldName)).toBe('nameVal2');

        // update names of both rows using lsid (ignored) an rowId as key, verify update is successful and names are updated correctly
        const newName1 = 'RenamedByLsid1';
        const newName2 = 'RenamedByLsid2';
        await ExperimentCRUDUtils.updateRows(server, [
            { lsid: "BAD", rowId: row1RowId, name: newName1 },
            { lsid: row1Lsid /*wrong*/, rowId: row2RowId, name: newName2 },
        ], 'exp.data', dataType, topFolderOptions, editorUserOptions);

        rows = await ExperimentCRUDUtils.getRows(server, [row1RowId, row2RowId], 'exp.data', dataType, 'RowId,Name', topFolderOptions, adminOptions);
        row1 = findRow(rows, row1RowId);
        row2 = findRow(rows, row2RowId);
        expect(caseInsensitive(row1, 'Name')).toBe(newName1);
        expect(caseInsensitive(row2, 'Name')).toBe(newName2);

        // update names of both rows using just rowId as key, verify update is successful and names are updated correctly
        const newName3 = 'RenamedByRowId1';
        const newName4 = 'RenamedByRowId2';
        await ExperimentCRUDUtils.updateRows(server, [
            { rowId: row1RowId, name: newName3 },
            { rowId: row2RowId, name: newName4 },
        ], 'exp.data', dataType, topFolderOptions, editorUserOptions);

        rows = await ExperimentCRUDUtils.getRows(server, [row1RowId, row2RowId], 'exp.data', dataType, 'RowId,Name', topFolderOptions, adminOptions);
        row1 = findRow(rows, row1RowId);
        row2 = findRow(rows, row2RowId);
        expect(caseInsensitive(row1, 'Name')).toBe(newName3);
        expect(caseInsensitive(row2, 'Name')).toBe(newName4);

        // update description and fieldName value from Import with update, the import columns contains name field, verify update is successful and data are updated correctly
        importUpdateText = 'Name\tDescription\t' + fieldName + '\n' + newName3 + '\timportUpd1\timportVal1\n' + newName4 + '\timportUpd2\timportVal2';
        const updateResp = await ExperimentCRUDUtils.importData(server, importUpdateText, dataType, 'UPDATE', topFolderOptions, editorUserOptions);
        expect(updateResp.body.success).toBe(true);

        rows = await ExperimentCRUDUtils.getRows(server, [row1RowId, row2RowId], 'exp.data', dataType, '*', topFolderOptions, adminOptions);
        row1 = findRow(rows, row1RowId);
        row2 = findRow(rows, row2RowId);
        expect(caseInsensitive(row1, 'description')).toBe('importUpd1');
        expect(caseInsensitive(row1, fieldName)).toBe('importVal1');
        expect(caseInsensitive(row2, 'description')).toBe('importUpd2');
        expect(caseInsensitive(row2, fieldName)).toBe('importVal2');

        // Error when supplying RowId during MERGE, verify import fails
        errorResp = await ExperimentCRUDUtils.importData(server, "RowId\tDescription\n" + row3RowId + "\tupdate\n", dataType, "MERGE", topFolderOptions, editorUserOptions);
        expect(errorResp.text).toContain(ROWID_MERGE_ERROR);
        errorResp = await ExperimentCRUDUtils.importData(server, "RowId\tName\tDescription\n" + row3RowId + "\t" + dataName3 + "\tupdate\n", dataType, "MERGE", topFolderOptions, editorUserOptions);
        expect(errorResp.text).toContain(ROWID_MERGE_ERROR);

        // update description and fieldName value from Import with merge. at the same time create a new data. the import columns contain name field, verify update and insert is successful
        const newDataName = 'MergedNewData';
        const importMergeText = 'Name\tDescription\t' + fieldName + '\n' + newName3 + '\tmergeUpd1\tmergeVal1\n' + newName4 + '\tmergeUpd2\tmergeVal2\n' + newDataName + '\tmergeNew\tmergeNewVal';
        const mergeResp = await ExperimentCRUDUtils.importData(server, importMergeText, dataType, 'MERGE', topFolderOptions, editorUserOptions);
        expect(mergeResp.body.success).toBe(true);

        rows = await ExperimentCRUDUtils.getRows(server, [row1RowId, row2RowId], 'exp.data', dataType, '*', topFolderOptions, adminOptions);
        row1 = findRow(rows, row1RowId);
        row2 = findRow(rows, row2RowId);
        expect(caseInsensitive(row1, 'description')).toBe('mergeUpd1');
        expect(caseInsensitive(row1, fieldName)).toBe('mergeVal1');
        expect(caseInsensitive(row2, 'description')).toBe('mergeUpd2');
        expect(caseInsensitive(row2, fieldName)).toBe('mergeVal2');

        // verify new data was created by merge
        const newDataRow = await getDataClassDataByName(newDataName, dataType, '*', topFolderOptions, adminOptions);
        expect(caseInsensitive(newDataRow, 'Name')).toBe(newDataName);
        expect(caseInsensitive(newDataRow, 'description')).toBe('mergeNew');
        expect(caseInsensitive(newDataRow, fieldName)).toBe('mergeNewVal');

        // Update from file, using rowId as key, verify update should be successful and data are updated correctly
        const importUpdateRowIdText = 'RowId\tDescription\t' + fieldName + '\n' + row1RowId + '\timportUpdByRowId1\timportValByRowId1\n' + row2RowId + '\timportUpdByRowId2\timportValByRowId2';
        const updateByRowIdResp = await ExperimentCRUDUtils.importData(server, importUpdateRowIdText, dataType, 'UPDATE', topFolderOptions, editorUserOptions);
        expect(updateByRowIdResp.body.success).toBe(true);

        rows = await ExperimentCRUDUtils.getRows(server, [row1RowId, row2RowId], 'exp.data', dataType, '*', topFolderOptions, adminOptions);
        row1 = findRow(rows, row1RowId);
        row2 = findRow(rows, row2RowId);
        expect(caseInsensitive(row1, 'description')).toBe('importUpdByRowId1');
        expect(caseInsensitive(row1, fieldName)).toBe('importValByRowId1');
        expect(caseInsensitive(row2, 'description')).toBe('importUpdByRowId2');
        expect(caseInsensitive(row2, fieldName)).toBe('importValByRowId2');

        // update from file, provide rowId and an updated name, verify name is successfully updated
        const newNameByRowId1 = 'RenamedByRowId1Import';
        const newNameByRowId2 = 'RenamedByRowId2Import';
        const importUpdateRowIdNameText = 'RowId\tName\tDescription\n' + row1RowId + '\t' + newNameByRowId1 + '\timportUpdByRowId1-2\n' + row2RowId + '\t' + newNameByRowId2 + '\timportUpdByRowId2-2\n';
        const updateByRowIdNameResp = await ExperimentCRUDUtils.importData(server, importUpdateRowIdNameText, dataType, 'UPDATE', topFolderOptions, editorUserOptions);
        expect(updateByRowIdNameResp.body.success).toBe(true);

        rows = await ExperimentCRUDUtils.getRows(server, [row1RowId, row2RowId], 'exp.data', dataType, '*', topFolderOptions, adminOptions);
        row1 = findRow(rows, row1RowId);
        row2 = findRow(rows, row2RowId);
        expect(caseInsensitive(row1, 'Name')).toBe(newNameByRowId1);
        expect(caseInsensitive(row1, 'description')).toBe('importUpdByRowId1-2');
        expect(caseInsensitive(row2, 'Name')).toBe(newNameByRowId2);
        expect(caseInsensitive(row2, 'description')).toBe('importUpdByRowId2-2');

        // verify data rowId needs to match provided dataclass type
        const emptyDataClass = dataType + "Empty";
        await server.post('property', 'createDomain', {
            kind: 'DataClass',
            domainDesign: { name: emptyDataClass, fields: [{ name: fieldName }] },
            options: { name: dataType }
        }, { ...topFolderOptions, ...designerReaderOptions }).expect(successfulResponse);

        // using query api, update using rowId for data that doesn't exist on the new dataclass should fail.
        await server.post('query', 'updateRows', {
            schemaName: 'exp.data',
            queryName: emptyDataClass,
            rows: [{
                description: 'update',
                rowId: row3RowId
            }]
        }, { ...topFolderOptions, ...editorUserOptions }).expect((result) => {
            const errorResp = JSON.parse(result.text);
            expect(errorResp['exception']).toContain('Data not found for [' + row3RowId + ']');
        });

        // using update from file, verify update using rowId for data that doesn't exist on this dataclass should fail.
        errorResp = await ExperimentCRUDUtils.importData(server, "RowId\tDescription\n" + row3RowId + "\tupdate\n", emptyDataClass, "UPDATE", topFolderOptions, editorUserOptions);
        expect(errorResp.text).toContain('Data not found for [' + row3RowId + ']');

    });

});
