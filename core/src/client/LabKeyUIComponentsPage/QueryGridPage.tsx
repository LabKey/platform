/*
 * Copyright (c) 2019 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
import React, {PureComponent, ReactNode} from 'react'
import {
    QueryGridModel,
    ManageDropdownButton,
    SelectionMenuItem,
    QueryGridPanel
} from "@labkey/components";

import {SchemaQueryInputContext, SchemaQueryInputProvider} from "./SchemaQueryInputProvider";

class QueryGridPageImpl extends PureComponent<SchemaQueryInputContext> {

    renderButtons = (updatedModel: QueryGridModel): ReactNode => {
        if (updatedModel) {
            return (
                <ManageDropdownButton id={'componentmanage'}>
                    <SelectionMenuItem
                        id={'componentselectionmenu'}
                        model={updatedModel}
                        text={'Selection Based Menu Item'}
                        onClick={() => console.log('SelectionMenuItem click: ' + updatedModel.selectedQuantity + ' selected.')}
                    />
                </ManageDropdownButton>
            )
        }
    };

    render(): ReactNode {
        const { model } = this.props;

        return (
            <>
                {model &&
                    <QueryGridPanel
                        header={'QueryGridPanel'}
                        model={model}
                        buttons={this.renderButtons}
                    />
                }
            </>
        )
    }
}

export const QueryGridPage = SchemaQueryInputProvider(QueryGridPageImpl);

