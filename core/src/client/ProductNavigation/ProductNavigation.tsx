/*
 * Copyright (c) 2021-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
import React, { FC, memo, useRef } from 'react';
import { ProductNavigationMenu } from '@labkey/components';

import './productNavigation.scss';

export interface AppContext {
    show: boolean;
}

interface Props {
    context: AppContext;
}

export const ProductNavigation: FC<Props> = memo(props => {
    const menuRef = useRef();
    if (props.context.show) {
        return <ProductNavigationMenu menuRef={menuRef} disableLKSContainerLink />;
    } else {
        return null;
    }
});
