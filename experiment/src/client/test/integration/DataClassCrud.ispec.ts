import { hookServer, RequestOptions, successfulResponse } from '@labkey/test';
import mock from 'mock-fs';
import {
    checkLackDesignerOrReaderPerm,
    createSource,
    deleteSourceType,
    getDataClassRowIdByName,
    initProject,
    verifyRequiredLineageInsertUpdate,
} from './utils';
import { DATA_CLASS_DESIGNER_ROLE } from '@labkey/components';
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

describe('Data Class Designer - Permissions', () => {
    it('Lack designer or Reader permission', async () => {
        await checkLackDesignerOrReaderPerm(server, 'DataClass', topFolderOptions, readerUserOptions, editorUserOptions, designerOptions);
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

describe('Data Class - Required Lineage', () => {
    it('Test dataclass with required dataclass parents', async () => {
        await verifyRequiredLineageInsertUpdate(server, false, false, topFolderOptions, subfolder1Options, designerReaderOptions, readerUserOptions, editorUserOptions);
    });

});
