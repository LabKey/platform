import React, {FC, memo, useCallback, useEffect, useState} from 'react'
import {AssayPicker} from '@labkey/components';
import {Button, Panel} from "react-bootstrap";

import "./AssayTypeSelect.scss"
import {ActionURL, getServerContext} from "@labkey/api";

export const App: FC<any> = memo(props => {
    const [ provider, setProvider ] = useState<string>('General')
    const [ container, setContainer ] = useState<string>()
    const [ returnUrl, setReturnUrl ] = useState<string>()

    useEffect(() => {
        setReturnUrl(ActionURL.getParameter('returnUrl'));
    }, [])

    const onCancel = useCallback(() => {
        window.location.href = returnUrl || ActionURL.buildURL('project', 'begin', getServerContext().container.path);
    }, [returnUrl])

    const onSelect = useCallback((provider) => {
        setProvider(provider);
    }, [])

    const onContainerSelect = useCallback((provider) => {
        setContainer(provider);
    }, [])

    const onSubmit = useCallback(() => {
        const cont = container ?? getServerContext().container.path
        window.location.href = ActionURL.buildURL('assay', 'designer', cont, {
            'providerName': provider,
            'returnUrl': returnUrl
        });
    }, [provider, returnUrl, container])

    const label = provider === "General" ? "Standard" : provider

    return (
        <>
            <Panel className={'assay-type-select-panel lk-border-theme-light'}>
                <div> {/* Div needed to break css child selector rule */}
                    <Panel.Heading className={'bg-primary assay-type-select-hdr'}>
                        <div>Choose Assay Type</div>
                    </Panel.Heading>
                </div>
                <Panel.Body>
                    <AssayPicker showImport={true} onProviderSelect={onSelect} onContainerSelect={onContainerSelect}/>
                </Panel.Body>
            </Panel>
            <div className={'assay-type-select-panel'}>
                <Button onClick={onCancel}>Cancel</Button>
                <Button
                    className="pull-right"
                    bsStyle={'primary'}
                    onClick={onSubmit}
                >
                    {'Choose ' + label + ' Assay'}
                </Button>
            </div>
        </>
    )

});