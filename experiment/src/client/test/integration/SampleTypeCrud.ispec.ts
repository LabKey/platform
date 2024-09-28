import { ExperimentCRUDUtils, hookServer, RequestOptions, successfulResponse } from '@labkey/test';
import mock from 'mock-fs';
import {
    checkLackDesignerOrReaderPerm,
    createSample,
    deleteSampleType,
    getSampleTypeRowIdByName,
    initProject
} from './utils';
import { caseInsensitive, SAMPLE_TYPE_DESIGNER_ROLE } from '@labkey/components';
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
            metricUnit: 'mL'
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
            name: SAMPLE_ALIQUOT_REQ_IMPORT_TYPE_NAME,
            aliquotNameExpression: "${${AliquotedFrom}-:withCounter}",
            nameExpression: "S-Req-${genId}",
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

describe('Sample Type Designer - Permissions', () => {
    it('Lack designer or Reader permission', async () => {
        await checkLackDesignerOrReaderPerm(server, 'SampleSet', topFolderOptions, readerUserOptions, editorUserOptions, designerOptions);
    });

    describe('Create/update/delete designs', () => {
        it('Designer can create, update and delete empty design, reader and editors cannot create/update/delete design', async () => {
            const sampleType = "ToDelete";
            let domainId = -1, domainURI = '';
            const createPayload = {
                kind: 'SampleSet',
                domainDesign: { name: sampleType, fields: [{ name: 'Name' }] },
                options: {
                    name: sampleType,
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
                    name: sampleType,
                    metricUnit: "mg"
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
         *     </ul>
         *     Because importing is batched the imported aliquot without an explicit name set it should have the next
         *     index (default behavior).
         * </p>
         */
        it('testImportingWithNameValue - with naming patten', async () => {
            // also include scenarios from testImportWithUpdate
            await verifyImportingWithNameValue('withNameValueParent', SAMPLE_ALIQUOT_IMPORT_TYPE_NAME);
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
        it('testImportingWithNameValue - without naming patten', async () => {
            await verifyImportingWithNameValue('withNameValueParentNoPattern', SAMPLE_ALIQUOT_IMPORT_NO_NAME_PATTERN_NAME);
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
            importText += "\t" + description + "\t\t\n";
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
});

describe('Required lineage', () => {

})