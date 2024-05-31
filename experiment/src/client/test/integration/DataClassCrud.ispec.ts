import { PermissionRoles } from '@labkey/api';
import { hookServer, RequestOptions, successfulResponse } from '@labkey/test';
import { caseInsensitive, DATA_CLASS_DESIGNER_ROLE } from '@labkey/components';
import mock from 'mock-fs';
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
    mock.restore();
    const response = await server.post('query', 'selectRows', {
        schemaName: 'exp',
        queryName: 'dataclasses',
        'query.name~eq': dataClassName,
        'query.columns': "RowId",
    }, { ...folderOptions  }).expect(successfulResponse);
    if (response.body.rows?.length > 0)
        return caseInsensitive(response.body.rows[0], 'rowId');
    return 0;
}

async function createData(dataClassName: string, name: string, folderOptions: RequestOptions) {
    const response = await server.request('query', 'insertRows', (agent, url) => {
        let request = agent.post(url);

        request = request.field('json', JSON.stringify({
            schemaName: 'exp.data',
            queryName: dataClassName,
            rows: [{name}]
        }));

        return request;

    }, { ...folderOptions, ...editorUserOptions }).expect(successfulResponse);
    return caseInsensitive(response.body.rows[0], 'rowId');
}

async function deleteDataClass(dataTypeRowId: number, folderOptions: RequestOptions, userOptions: RequestOptions) {
    const resp = await server.request('experiment', 'deleteDataClass', (agent, url) => {
        return agent
            .post(url)
            .type('form')
            .send({
                singleObjectRowId: dataTypeRowId,
                forceDelete: true
            });
    }, { ...folderOptions, ...userOptions });
    return resp;
}


beforeAll(async () => {
    await server.init(PROJECT_NAME, {
        ensureModules: ['experiment'],
    });
    topFolderOptions = { containerPath: PROJECT_NAME };

    // create subfolders to use in tests
    const subfolder1 = await server.createTestContainer();
    subfolder1Options = { containerPath: subfolder1.path };
    const subfolder2 = await server.createTestContainer();
    subfolder2Options = { containerPath: subfolder2.path };

    // create users with different permissions
    readerUser = await server.createUser('reader@expdscrudtest.com');
    await server.addUserToRole(readerUser.username, PermissionRoles.Reader, PROJECT_NAME);
    readerUserOptions = { requestContext: await server.createRequestContext(readerUser) };

    editorUser = await server.createUser('editor@expdscrudtest.com');
    await server.addUserToRole(editorUser.username, PermissionRoles.Editor, PROJECT_NAME);
    await server.addUserToRole(editorUser.username, PermissionRoles.Editor, subfolder1.path);
    editorUserOptions = { requestContext: await server.createRequestContext(editorUser) };

    designer = await server.createUser('designer@expdscrudtest.com');
    await server.addUserToRole(designer.username, DATA_CLASS_DESIGNER_ROLE, PROJECT_NAME);
    designerOptions = { requestContext: await server.createRequestContext(designer) };

    designerReader = await server.createUser('readerdesigner@expdscrudtest.com');
    await server.addUserToRole(designerReader.username, DATA_CLASS_DESIGNER_ROLE, PROJECT_NAME);
    await server.addUserToRole(designerReader.username, PermissionRoles.Reader, PROJECT_NAME);
    designerReaderOptions = { requestContext: await server.createRequestContext(designerReader) };

    designerEditor = await server.createUser('designereditor@expdscrudtest.com');
    await server.addUserToRole(designerEditor.username, PermissionRoles.Editor, PROJECT_NAME);
    await server.addUserToRole(designerEditor.username, DATA_CLASS_DESIGNER_ROLE, PROJECT_NAME);
    designerEditorOptions = { requestContext: await server.createRequestContext(designerEditor) };

    admin = await server.createUser('admin@expdscrudtest.com');
    await server.addUserToRole(admin.username, PermissionRoles.ProjectAdmin, PROJECT_NAME);
    await server.addUserToRole(admin.username, PermissionRoles.FolderAdmin, subfolder1.path);
    await server.addUserToRole(admin.username, PermissionRoles.FolderAdmin, subfolder2.path);
    adminOptions = { requestContext: await server.createRequestContext(admin) };

});

afterAll(async () => {
    return server.teardown();
});

afterEach(() => {
    mock.restore();
});

describe('Data Class Designer - Permissions', () => {
    describe('Lack designer or Reader permission', () => {
        it('Reader', async () => {
            await server.post('property', 'createDomain', {
                kind: 'DataClass',
                domainDesign: { name: "Failed", fields: [{ name: 'Prop' }] },
                options: {
                    name: "Failed",
                }
            }, {...topFolderOptions, ...readerUserOptions}).expect(403);
        });

        it('Editor', async () => {
            await server.post('property', 'createDomain', {
                kind: 'DataClass',
                domainDesign: { name: "Failed", fields: [{ name: 'Prop' }] },
                options: {
                    name: "Failed",
                }
            }, {...topFolderOptions, ...editorUserOptions}).expect(403);
        });

        it('Designer', async () => {
            await server.post('property', 'createDomain', {
                kind: 'DataClass',
                domainDesign: { name: "Failed", fields: [{ name: 'Prop' }] },
                options: {
                    name: "Failed",
                }
            }, {...topFolderOptions, ...designerOptions}).expect(403);
        });
    });

    describe('Create/update/delete designs', () => {
        it('Designer can create, update and delete empty design', async () => {
            const dataType = "ToDelete";
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

            await server.post('property', 'saveDomain', {
                domainId,
                domainDesign: { name: dataType, domainId, domainURI },
                options: {
                    name: dataType,
                    nameExpression: 'Source-${genId}'
                }
            }, {...topFolderOptions, ...designerReaderOptions}).expect(successfulResponse);

            const deleteResult = await deleteDataClass(dataTypeRowId, topFolderOptions, designerReaderOptions);
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

            await server.post('property', 'saveDomain', {
                domainId,
                domainDesign: { name: dataType, domainId, domainURI },
                options: {
                    name: dataType,
                    nameExpression: 'Source-${genId}'
                }
            }, {...topFolderOptions, ...designerReaderOptions}).expect(successfulResponse);

            // verify data exist in child prevent designer from delete design
            let deleteResult = await deleteDataClass(dataTypeRowId, topFolderOptions, designerReaderOptions);
            expect(deleteResult.status).toEqual(403);

            let failedRemoveddataType = await getDataClassRowId(dataType, topFolderOptions);
            expect(failedRemoveddataType).toEqual(dataTypeRowId);

            // create more data in top folder
            await createData(dataType, 'Data2', {...topFolderOptions, ...editorUserOptions});

            // verify data exist in Top prevent designer from delete design
            deleteResult = await deleteDataClass(dataTypeRowId, topFolderOptions, designerReaderOptions);
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


// TODO data crud