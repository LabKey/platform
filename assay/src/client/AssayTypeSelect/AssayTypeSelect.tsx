
import React, { FC, memo } from 'react'
import { AssayPicker } from '@labkey/components';
import { Panel } from "react-bootstrap";

import "./AssayTypeSelect.scss"

export const App: FC<any> = memo(props => {

    return (
        <Panel className={'assay-type-select-panel lk-border-theme-light'}>
            <div> {/* Div needed to break css child selector rule */}
                <Panel.Heading className={'bg-primary assay-type-select-hdr'}>
                    <div>Choose Assay Type</div>
                </Panel.Heading>
            </div>
            <Panel.Body>
                <AssayPicker showImport={true}/>
            </Panel.Body>
        </Panel>
    )

});