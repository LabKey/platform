import { hookServer, RequestOptions, SecurityRole, successfulResponse } from '@labkey/test';
import { caseInsensitive } from '@labkey/components';

const server = hookServer(process.env);
const PROJECT_NAME = 'ExperimentControllerTest Project';
const SAMPLE_TYPE_NAME = 'TestMoveSampleType';

let editorUserOptions: RequestOptions;
let subEditorUserOptions: RequestOptions;
let authorUserOptions: RequestOptions;
let noPermsUserOptions: RequestOptions;

let topFolderOptions: RequestOptions;
let subfolder1Options;
let subfolder2Options;

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
    const editorUser = await server.createUser('test_editor@expctrltest.com', 'pwSuperA1!');
    await server.addUserToRole(editorUser.username, SecurityRole.Editor, PROJECT_NAME);
    await server.addUserToRole(editorUser.username, SecurityRole.Editor, subfolder1.path);
    await server.addUserToRole(editorUser.username, SecurityRole.Editor, subfolder2.path);
    editorUserOptions = { requestContext: await server.createRequestContext(editorUser) };

    const subEditorUser = await server.createUser('test_subeditor@expctrltest.com', 'pwSuperA1!');
    await server.addUserToRole(subEditorUser.username, SecurityRole.Reader, PROJECT_NAME);
    await server.addUserToRole(subEditorUser.username, SecurityRole.Editor, subfolder1.path);
    await server.addUserToRole(subEditorUser.username, SecurityRole.Editor, subfolder2.path);
    subEditorUserOptions = { requestContext: await server.createRequestContext(subEditorUser) };

    const authorUser = await server.createUser('test_author@expctrltest.com', 'pwSuperA1!');
    await server.addUserToRole(authorUser.username, SecurityRole.Author, PROJECT_NAME);
    await server.addUserToRole(authorUser.username, SecurityRole.Author, subfolder1.path);
    await server.addUserToRole(authorUser.username, SecurityRole.Author, subfolder2.path);
    authorUserOptions = { requestContext: await server.createRequestContext(authorUser) };

    const noPermsUser = await server.createUser('test_no_perms@expctrltest.com', 'pwSuperA1!');
    noPermsUserOptions = { requestContext: await server.createRequestContext(noPermsUser) };

    // create a sample type at project container for use in tests
    await server.post('property', 'createDomain', {
        kind: 'SampleSet',
        domainDesign: { name: SAMPLE_TYPE_NAME, fields: [{ name: 'Name',  }] }
    }, topFolderOptions).expect(successfulResponse);
});

afterAll(async () => {
    return server.teardown();
});

async function createSample(sampleName: string, folderOptions: RequestOptions) {
    const materialResponse = await server.post('query', 'insertRows', {
        schemaName: 'samples',
        queryName: SAMPLE_TYPE_NAME,
        rows: [{ name: sampleName }]
    }, { ...folderOptions, ...editorUserOptions }).expect(successfulResponse);
    return caseInsensitive(materialResponse.body.rows[0], 'rowId');
}

async function sampleExists(sampleRowId: number, folderOptions: RequestOptions) {
    const response = await server.post('query', 'selectRows', {
        schemaName: 'samples',
        queryName: SAMPLE_TYPE_NAME,
        'query.RowId~eq': sampleRowId
    }, { ...folderOptions, ...editorUserOptions }).expect(successfulResponse);
    return response.body.rows.length === 1;
}

describe('ExperimentController', () => {
    describe('moveSamples.api', () => {
        // NOTE: the MoveSamplesAction is in the experiment module, but the sample status related test cases won't
        // work here because the sample status feature is only "enabled" when the sampleManagement module is available.
        // See sampleManagement/src/client/test/integration/MoveSamplesAction.ispec.ts for additional test cases.

        it('requires POST', () => {
            server.get('experiment', 'moveSamples.api').expect(405);
        });

        it('error, no permissions', async () => {
            // Act
            const response = await server.post('experiment', 'moveSamples.api', {}, { ...topFolderOptions, ...noPermsUserOptions }).expect(403);

            // Assert
            const { exception, success } = response.body;
            expect(success).toBe(false);
            expect(exception).toEqual('User does not have permission to perform this operation.');
        });

        it('error, requires update permissions in current', async () => {
            // Act
            const response = await server.post('experiment', 'moveSamples.api', {}, { ...topFolderOptions, ...authorUserOptions }).expect(403);

            // Assert
            const { exception, success } = response.body;
            expect(success).toBe(false);
            expect(exception).toEqual('User does not have permission to perform this operation.');
        });

        it('error, missing required targetContainer param', async () => {
            // Act
            const response = await server.post('experiment', 'moveSamples.api', {}, { ...topFolderOptions, ...editorUserOptions }).expect(400);

            // Assert
            const { exception, success } = response.body;
            expect(success).toBe(false);
            expect(exception).toEqual('A target container must be specified for the move operation.');
        });

        it('error, non-existent targetContainer param', async () => {
            // Act
            const response = await server.post('experiment', 'moveSamples.api', {
                targetContainer: 'BOGUS'
            }, { ...topFolderOptions, ...editorUserOptions }).expect(400);

            // Assert
            const { exception, success } = response.body;
            expect(success).toBe(false);
            expect(exception).toEqual('The target container was not found: BOGUS.');
        });

        it('error, targetContainer cannot equal current for parent project', async () => {
            // Act
            const response = await server.post('experiment', 'moveSamples.api', {
                targetContainer: PROJECT_NAME
            }, { ...topFolderOptions, ...editorUserOptions }).expect(400);

            // Assert
            const { exception, success } = response.body;
            expect(success).toBe(false);
            expect(exception).toEqual('Invalid target container for the move operation: ExperimentControllerTest Project.');
        });

        it('error, targetContainer cannot equal current for subfolder', async () => {
            // Act
            const response = await server.post('experiment', 'moveSamples.api', {
                targetContainer: subfolder1Options.containerPath
            }, { ...subfolder1Options, ...editorUserOptions }).expect(400);

            // Assert
            const { exception, success } = response.body;
            expect(success).toBe(false);
            expect(exception).toEqual('Invalid target container for the move operation: ' + subfolder1Options.containerPath + '.');
        });

        it('error, missing required sample IDs param', async () => {
            // Act
            const response = await server.post('experiment', 'moveSamples.api', {
                targetContainer: subfolder1Options.containerPath
            }, { ...topFolderOptions, ...editorUserOptions }).expect(400);

            // Assert
            const { exception, success } = response.body;
            expect(success).toBe(false);
            expect(exception).toEqual('Sample IDs must be specified for the move operation.');
        });

        it('error, empty sample IDs param', async () => {
            // Act
            const response = await server.post('experiment', 'moveSamples.api', {
                targetContainer: subfolder1Options.containerPath,
                rowIds: []
            }, { ...topFolderOptions, ...editorUserOptions }).expect(400);

            // Assert
            const { exception, success } = response.body;
            expect(success).toBe(false);
            expect(exception).toEqual('Sample IDs must be specified for the move operation.');
        });

        it('error, invalid sample ID', async () => {
            // Act
            const response = await server.post('experiment', 'moveSamples.api', {
                targetContainer: subfolder1Options.containerPath,
                rowIds: [1]
            }, { ...topFolderOptions, ...editorUserOptions }).expect(400);

            // Assert
            const { exception, success } = response.body;
            expect(success).toBe(false);
            expect(exception).toEqual('Unable to find all samples for the move operation.');
        });

        it('error, sample ID not in current parent project', async () => {
            // Arrange
            const sampleRowId = await createSample('sub1-notmoved-1', subfolder1Options);

            // Act
            const response = await server.post('experiment', 'moveSamples.api', {
                targetContainer: subfolder1Options.containerPath,
                rowIds: [sampleRowId]
            }, { ...topFolderOptions, ...editorUserOptions }).expect(400);

            // Assert
            const { exception, success } = response.body;
            expect(success).toBe(false);
            expect(exception).toEqual('All samples must be from the current container for the move operation.');

            const sampleExistsInTop = await sampleExists(sampleRowId, topFolderOptions);
            expect(sampleExistsInTop).toBe(false);
            const sampleExistsInSub1 = await sampleExists(sampleRowId, subfolder1Options);
            expect(sampleExistsInSub1).toBe(true);
        });

        it('error, sample ID not in current subfolder', async () => {
            // Arrange
            const sampleRowId = await createSample('top-notmoved-1', topFolderOptions);

            // Act
            const response = await server.post('experiment', 'moveSamples.api', {
                targetContainer: topFolderOptions.containerPath,
                rowIds: [sampleRowId]
            }, { ...subfolder1Options, ...editorUserOptions }).expect(400);

            // Assert
            const { exception, success } = response.body;
            expect(success).toBe(false);
            expect(exception).toEqual('All samples must be from the current container for the move operation.');

            const sampleExistsInTop = await sampleExists(sampleRowId, topFolderOptions);
            expect(sampleExistsInTop).toBe(true);
            const sampleExistsInSub1 = await sampleExists(sampleRowId, subfolder1Options);
            expect(sampleExistsInSub1).toBe(false);
        });

        it('error, requires insert perm in targetContainer', async () => {
            // Arrange
            const sampleRowId = await createSample('sub1-notmoved-2', subfolder1Options);

            // Act
            const response = await server.post('experiment', 'moveSamples.api', {
                targetContainer: topFolderOptions.containerPath,
                rowIds: [sampleRowId]
            }, { ...subfolder1Options, ...subEditorUserOptions }).expect(400);

            // Assert
            const { exception, success } = response.body;
            expect(success).toBe(false);
            expect(exception).toEqual('You do not have permission to move samples to the target container: ExperimentControllerTest Project.');

            const sampleExistsInTop = await sampleExists(sampleRowId, topFolderOptions);
            expect(sampleExistsInTop).toBe(false);
            const sampleExistsInSub1 = await sampleExists(sampleRowId, subfolder1Options);
            expect(sampleExistsInSub1).toBe(true);
        });

        it('success, move sample from parent project to subfolder', async () => {
            // Arrange
            const sampleRowId = await createSample('top-movetosub1-1', topFolderOptions);

            // Act
            const response = await server.post('experiment', 'moveSamples.api', {
                targetContainer: subfolder1Options.containerPath,
                rowIds: [sampleRowId]
            }, { ...topFolderOptions, ...editorUserOptions }).expect(200);

            // Assert
            const { samplesMoved, success } = response.body;
            expect(success).toBe(true);
            expect(samplesMoved).toEqual(1);

            const sampleExistsInTop = await sampleExists(sampleRowId, topFolderOptions);
            expect(sampleExistsInTop).toBe(false);
            const sampleExistsInSub1 = await sampleExists(sampleRowId, subfolder1Options);
            expect(sampleExistsInSub1).toBe(true);
        });

        it('success, move sample from subfolder to parent project', async () => {
            // Arrange
            const sampleRowId = await createSample('sub1-movetotop-1', subfolder1Options);

            // Act
            const response = await server.post('experiment', 'moveSamples.api', {
                targetContainer: topFolderOptions.containerPath,
                rowIds: [sampleRowId]
            }, { ...subfolder1Options, ...editorUserOptions }).expect(200);

            // Assert
            const { samplesMoved, success } = response.body;
            expect(success).toBe(true);
            expect(samplesMoved).toEqual(1);

            const sampleExistsInTop = await sampleExists(sampleRowId, topFolderOptions);
            expect(sampleExistsInTop).toBe(true);
            const sampleExistsInSub1 = await sampleExists(sampleRowId, subfolder1Options);
            expect(sampleExistsInSub1).toBe(false);
        });

        it('success, move sample from subfolder to sibling', async () => {
            // Arrange
            const sampleRowId = await createSample('sub1-movetosub2-1', subfolder1Options);

            // Act
            const response = await server.post('experiment', 'moveSamples.api', {
                targetContainer: subfolder2Options.containerPath,
                rowIds: [sampleRowId]
            }, { ...subfolder1Options, ...editorUserOptions }).expect(200);

            // Assert
            const { samplesMoved, success } = response.body;
            expect(success).toBe(true);
            expect(samplesMoved).toEqual(1);

            const sampleExistsInSub1 = await sampleExists(sampleRowId, subfolder1Options);
            expect(sampleExistsInSub1).toBe(false);
            const sampleExistsInSub2 = await sampleExists(sampleRowId, subfolder2Options);
            expect(sampleExistsInSub2).toBe(true);
        });

        it('success, move multiple sample', async () => {
            // Arrange
            const sampleRowId1 = await createSample('sub1-movetosub2-2', subfolder1Options);
            const sampleRowId2 = await createSample('sub1-movetosub2-3', subfolder1Options);
            const sampleRowId3 = await createSample('sub1-movetosub2-4', subfolder1Options);

            // Act
            const response = await server.post('experiment', 'moveSamples.api', {
                targetContainer: subfolder2Options.containerPath,
                rowIds: [sampleRowId1, sampleRowId2, sampleRowId3]
            }, { ...subfolder1Options, ...editorUserOptions }).expect(200);

            // Assert
            const { samplesMoved, success } = response.body;
            expect(success).toBe(true);
            expect(samplesMoved).toEqual(3);

            let sampleExistsInSub1 = await sampleExists(sampleRowId1, subfolder1Options);
            expect(sampleExistsInSub1).toBe(false);
            sampleExistsInSub1 = await sampleExists(sampleRowId2, subfolder1Options);
            expect(sampleExistsInSub1).toBe(false);
            sampleExistsInSub1 = await sampleExists(sampleRowId3, subfolder1Options);
            expect(sampleExistsInSub1).toBe(false);

            let sampleExistsInSub2 = await sampleExists(sampleRowId1, subfolder2Options);
            expect(sampleExistsInSub2).toBe(true);
            sampleExistsInSub2 = await sampleExists(sampleRowId2, subfolder2Options);
            expect(sampleExistsInSub2).toBe(true);
            sampleExistsInSub2 = await sampleExists(sampleRowId3, subfolder2Options);
            expect(sampleExistsInSub2).toBe(true);

            // verify that we are able to delete the original sample container after things are moved
            await server.post('core', 'deleteContainer', undefined, { ...subfolder1Options }).expect(successfulResponse);
        });
    });
});
