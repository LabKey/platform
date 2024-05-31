import { PermissionRoles } from '@labkey/api';
import { hookServer, RequestOptions, successfulResponse } from '@labkey/test';
import { caseInsensitive, SAMPLE_TYPE_DESIGNER_ROLE } from '@labkey/components';
import mock from 'mock-fs';
const server = hookServer(process.env);
const PROJECT_NAME = 'SampleTypeCrudJestProject';

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
    mock.restore();
    const response = await server.post('query', 'selectRows', {
        schemaName: 'exp',
        queryName: 'samplesets',
        'query.name~eq': sampleType,
        'query.columns': "RowId",
    }, { ...folderOptions  }).expect(successfulResponse);
    if (response.body.rows?.length > 0)
        return caseInsensitive(response.body.rows[0], 'rowId');
    return 0;
}

async function createSample(sampleTypeName: string, sampleName: string, folderOptions: RequestOptions) {
    const materialResponse = await server.request('query', 'insertRows', (agent, url) => {
        let request = agent.post(url);

        request = request.field('json', JSON.stringify({
            schemaName: 'samples',
            queryName: sampleTypeName,
            rows: [{name: sampleName}]
        }));

        return request;

    }, { ...folderOptions, ...editorUserOptions }).expect(successfulResponse);
    return caseInsensitive(materialResponse.body.rows[0], 'rowId');
}

async function deleteSampleType(sampleTypeRowId: number, folderOptions: RequestOptions, userOptions: RequestOptions) {
    const resp = await server.request('experiment', 'deleteSampleTypes', (agent, url) => {
        return agent
            .post(url)
            .type('form')
            .send({
                singleObjectRowId: sampleTypeRowId,
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
    readerUser = await server.createUser('reader@expstcrudtest.com');
    await server.addUserToRole(readerUser.username, PermissionRoles.Reader, PROJECT_NAME);
    readerUserOptions = { requestContext: await server.createRequestContext(readerUser) };

    editorUser = await server.createUser('editor@expstcrudtest.com');
    await server.addUserToRole(editorUser.username, PermissionRoles.Editor, PROJECT_NAME);
    await server.addUserToRole(editorUser.username, PermissionRoles.Editor, subfolder1.path);
    editorUserOptions = { requestContext: await server.createRequestContext(editorUser) };

    designer = await server.createUser('designer@expstcrudtest.com');
    await server.addUserToRole(designer.username, SAMPLE_TYPE_DESIGNER_ROLE, PROJECT_NAME);
    designerOptions = { requestContext: await server.createRequestContext(designer) };

    designerReader = await server.createUser('readerdesigner@expstcrudtest.com');
    await server.addUserToRole(designerReader.username, SAMPLE_TYPE_DESIGNER_ROLE, PROJECT_NAME);
    await server.addUserToRole(designerReader.username, PermissionRoles.Reader, PROJECT_NAME);
    designerReaderOptions = { requestContext: await server.createRequestContext(designerReader) };

    designerEditor = await server.createUser('designereditor@expstcrudtest.com');
    await server.addUserToRole(designerEditor.username, PermissionRoles.Editor, PROJECT_NAME);
    await server.addUserToRole(designerEditor.username, SAMPLE_TYPE_DESIGNER_ROLE, PROJECT_NAME);
    designerEditorOptions = { requestContext: await server.createRequestContext(designerEditor) };

    admin = await server.createUser('admin@expstcrudtest.com');
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

describe('Sample Type Designer - Permissions', () => {
    describe('Lack designer or Reader permission', () => {
        it('Reader', async () => {
            await server.post('property', 'createDomain', {
                kind: 'SampleSet',
                domainDesign: { name: "Failed", fields: [{ name: 'Prop' }] },
                options: {
                    name: "Failed",
                }
            }, {...topFolderOptions, ...readerUserOptions}).expect(403);
        });

        it('Editor', async () => {
            await server.post('property', 'createDomain', {
                kind: 'SampleSet',
                domainDesign: { name: "Failed", fields: [{ name: 'Prop' }] },
                options: {
                    name: "Failed",
                }
            }, {...topFolderOptions, ...editorUserOptions}).expect(403);
        });

        it('Designer', async () => {
            await server.post('property', 'createDomain', {
                kind: 'SampleSet',
                domainDesign: { name: "Failed", fields: [{ name: 'Prop' }] },
                options: {
                    name: "Failed",
                }
            }, {...topFolderOptions, ...designerOptions}).expect(403);
        });
    });

    describe('Create/update/delete designs', () => {
        it('Designer can create, update and delete empty design', async () => {
            const sampleType = "ToDelete";
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

            await server.post('property', 'saveDomain', {
                domainId,
                domainDesign: { name: sampleType, domainId, domainURI },
                options: {
                    name: sampleType,
                    metricUnit: "mg"
                }
            }, {...topFolderOptions, ...designerReaderOptions}).expect(successfulResponse);

            const deleteResult = await deleteSampleType(sampleTypeRowId, topFolderOptions, designerReaderOptions);
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
            const createdSampleId = await createSample(sampleType, 'SampleData1', subfolder1Options);
            expect(createdSampleId === 0).toBeFalsy();

            await server.post('property', 'saveDomain', {
                domainId,
                domainDesign: { name: sampleType, domainId, domainURI },
                options: {
                    name: sampleType,
                    metricUnit: "kg"
                }
            }, {...topFolderOptions, ...designerReaderOptions}).expect(successfulResponse);

            // verify data exist in child prevent designer from delete design
            let deleteResult = await deleteSampleType(sampleTypeRowId, topFolderOptions, designerReaderOptions);
            expect(deleteResult.status).toEqual(403);

            let failedRemovedSampleType = await getSampleTypeRowId(sampleType, topFolderOptions);
            expect(failedRemovedSampleType).toEqual(sampleTypeRowId);

            // create more samples in top folder
            await createSample(sampleType, 'SampleData2', {...topFolderOptions, ...editorUserOptions});

            // verify data exist in Top prevent designer from delete design
            deleteResult = await deleteSampleType(sampleTypeRowId, topFolderOptions, designerReaderOptions);
            expect(deleteResult.status).toEqual(403);

            failedRemovedSampleType = await getSampleTypeRowId(sampleType, topFolderOptions);
            expect(failedRemovedSampleType).toEqual(sampleTypeRowId);

            //admin can delete design with data
            deleteResult = await deleteSampleType(sampleTypeRowId, topFolderOptions, adminOptions);
            expect(deleteResult.status).toEqual(302);

            const removedSampleType = await getSampleTypeRowId(sampleType, topFolderOptions);
            expect(removedSampleType).toEqual(0);
        });
    });

});


// TODO sample crud