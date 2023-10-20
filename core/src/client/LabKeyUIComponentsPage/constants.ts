import { List, fromJS } from 'immutable';
import { GridColumn } from '@labkey/components';

export const GRID_DATA = fromJS([
    {
        name: 'biologics',
        label: 'Biologics',
        link: 'https://github.com/LabKey/biologics',
    },
    {
        name: 'premium',
        label: 'Premium',
        link: 'https://github.com/LabKey/premium',
    },
    {
        name: 'sampleManagement',
        label: 'Sample Management',
        link: 'https://github.com/LabKey/sampleManagement',
    },
    {
        name: 'labkey-ui-components',
        label: 'LabKey UI Components',
        link: 'https://github.com/LabKey/labkey-ui-components',
    },
]);

export const GRID_COLUMNS = List([
    {
        index: 'name',
        caption: 'Module Name',
    },
    {
        index: 'label',
        caption: 'Module Label',
    },
    new GridColumn({
        index: 'link',
        title: 'GitHub Link',
    }),
]);
