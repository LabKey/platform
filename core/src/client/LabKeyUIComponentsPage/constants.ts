import {List, fromJS} from 'immutable'
import {GridColumn} from "@labkey/components";

export const GRID_DATA = fromJS([{
    name: 'premium',
    label: 'Premium',
    link: 'https://github.com/LabKey/premium'
}, {
    name: 'sampleManagement',
    label: 'Sample Management',
    link: 'https://github.com/LabKey/sampleManagement'
}, {
    name: 'labkey-ui-components',
    label: 'LabKey UI Components',
    link: 'https://github.com/LabKey/labkey-ui-components'
}]);

export const GRID_COLUMNS = List([
    {
        index: 'name',
        caption: 'Module Name'
    },
    {
        index: 'label',
        caption: 'Module Label'
    },
    new GridColumn({
        index: 'link',
        title: 'GitHub Link'
    })
]);

export const SEARCH_RESULT_HITS = {
    hits: [{
        "summary" : "briakinumab\nPS-10, IgG1, PS-9, im:1302, MS-1",
        "data" : {
            "createdBy" : "cnathe@labkey.com",
            "created" : "2019-02-27 18:44:13.550",
            "dataClass" : {
                "createdBy" : "cnathe@labkey.com",
                "created" : "2019-02-27 12:43:37.537",
                "name" : "Molecule",
                "modified" : "2019-02-27 12:43:37.537",
                "modifiedBy" : "cnathe@labkey.com",
                "id" : 73
            },
            "name" : "M-1",
            "modified" : "2019-02-27 18:44:13.550",
            "dataFileURL" : null,
            "modifiedBy" : "cnathe@labkey.com",
            "id" : 41234
        },
        "title" : "M-1"
    },{
        "summary" : "briakinumab_LC immunoglobulin G1-lambda, anti-[Homo sapiens interleukin 12 beta (IL12B, IL-12B, IL12 p40, NKSF2, CMLF p40)], Homo sapiens monoclonal antibody;\nM-1, NS-9, Lambda Light Chain, ips:1247, PS-9, human",
        "data" : {
            "createdBy" : "cnathe@labkey.com",
            "created" : "2019-02-27 18:44:00.450",
            "dataClass" : {
                "createdBy" : "cnathe@labkey.com",
                "created" : "2019-02-27 12:43:37.080",
                "name" : "ProtSequence",
                "modified" : "2019-02-27 12:43:37.080",
                "modifiedBy" : "cnathe@labkey.com",
                "id" : 71
            },
            "name" : "PS-9",
            "modified" : "2019-02-27 18:44:00.450",
            "dataFileURL" : null,
            "modifiedBy" : "cnathe@labkey.com",
            "id" : 41173
        },
        "title" : "PS-9"
    },{
        "summary" : "Project Apollo - M-1 Target\nM-1, HEK293, C-1",
        "data" : {
            "createdBy" : "cnathe@labkey.com",
            "created" : "2019-02-27 18:44:24.793",
            "dataClass" : {
                "createdBy" : "cnathe@labkey.com",
                "created" : "2019-02-27 12:43:39.270",
                "name" : "ExpressionSystem",
                "modified" : "2019-02-27 12:43:39.270",
                "modifiedBy" : "cnathe@labkey.com",
                "id" : 78
            },
            "name" : "ES-2",
            "modified" : "2019-02-27 18:44:24.793",
            "dataFileURL" : null,
            "modifiedBy" : "cnathe@labkey.com",
            "id" : 41302
        },
        "title" : "ES-2",
    }]
};