import { hookServer, RequestOptions, successfulResponse } from '@labkey/test';
import mock from 'mock-fs';
import {
    checkLackDesignerOrReaderPerm,
    createSample,
    deleteSampleType,
    getSampleTypeRowIdByName,
    initProject
} from './utils';
import { SAMPLE_TYPE_DESIGNER_ROLE } from '@labkey/components';
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
    return getSampleTypeRowIdByName(server, sampleType, folderOptions);
}

async function createASample(sampleTypeName: string, sampleName: string, folderOptions: RequestOptions) {
    return createSample(server, sampleName, folderOptions, editorUserOptions, undefined,  sampleTypeName);
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
});

afterAll(async () => {
    return server.teardown();
});

afterEach(() => {
    mock.restore();
});

describe('Sample Type Designer - Permissions', () => {
    it('Lack designer or Reader permission', async () => {
        await checkLackDesignerOrReaderPerm(server, 'SampleSet', topFolderOptions, readerUserOptions, editorUserOptions, designerOptions);
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

            const deleteResult = await deleteSampleTypeByRowId(sampleTypeRowId, topFolderOptions, designerReaderOptions);
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

            await server.post('property', 'saveDomain', {
                domainId,
                domainDesign: { name: sampleType, domainId, domainURI },
                options: {
                    name: sampleType,
                    metricUnit: "kg"
                }
            }, {...topFolderOptions, ...designerReaderOptions}).expect(successfulResponse);

            // verify data exist in child prevent designer from delete design
            let deleteResult = await deleteSampleTypeByRowId(sampleTypeRowId, topFolderOptions, designerReaderOptions);
            expect(deleteResult.status).toEqual(403);

            let failedRemovedSampleType = await getSampleTypeRowId(sampleType, topFolderOptions);
            expect(failedRemovedSampleType).toEqual(sampleTypeRowId);

            // create more samples in top folder
            await createASample(sampleType, 'SampleData2', {...topFolderOptions, ...editorUserOptions});

            // verify data exist in Top prevent designer from delete design
            deleteResult = await deleteSampleTypeByRowId(sampleTypeRowId, topFolderOptions, designerEditorOptions);
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


// TODO sample crud