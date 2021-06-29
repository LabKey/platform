import React, { FC, memo } from 'react';
import { ProductNavigationMenu } from '@labkey/components';

import './productNavigation.scss';

export interface AppContext {
    show: boolean;
}

interface Props {
    context: AppContext;
}

export const ProductNavigation: FC<Props> = memo(props => {
    if (props.context.show) {
        return <ProductNavigationMenu disableLKSContainerLink />;
    } else {
        return null;
    }
});
