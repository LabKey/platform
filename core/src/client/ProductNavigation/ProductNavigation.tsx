import React, { FC, memo } from 'react';
import { ProductNavigationMenu } from '@labkey/components';

import './productNavigation.scss';

export interface AppContext {}

interface Props {
    context: AppContext;
}

export const ProductNavigation: FC<Props> = memo(props => {
    return (
        <ProductNavigationMenu/>
    );
});
