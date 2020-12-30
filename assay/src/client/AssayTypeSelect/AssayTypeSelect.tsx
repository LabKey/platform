import React, { FC, memo, useCallback, useEffect, useState } from 'react'
import { AssayPicker } from '@labkey/components';
import { Button, Panel } from "react-bootstrap";

import "./AssayTypeSelect.scss"
import { ActionURL, Ajax, Utils, getServerContext } from "@labkey/api";

export function uploadXarFile(
    file: File,
    container: string
): Promise<string> {
    return new Promise((resolve, reject) => {
        const form = new FormData();
        form.append('file', file);

        Ajax.request({
            url: ActionURL.buildURL('experiment', 'assayXarFile', container),
            method: 'POST',
            form,
            success: Utils.getCallbackWrapper(() => {
                resolve(file.name);
            }),
            failure: Utils.getCallbackWrapper(
                () => {
                    console.error('failure uploading file ' + file.name);
                    reject(file.name);
                },
                null,
                false
            ),
        });
    });
}

export const App: FC<any> = memo(props => {
    const [ provider, setProvider ] = useState<string>('General');
    const [ container, setContainer ] = useState<string>();
    const [ returnUrl, setReturnUrl ] = useState<string>();
    const [ isFileUpload, setIsFileUpload ] = useState(false);
    const [ xar, setXar ] = useState<File>()

    useEffect(() => {
        setReturnUrl(ActionURL.getParameter('returnUrl'));
    }, [])

    const onCancel = useCallback(() => {
        window.location.href = returnUrl || ActionURL.buildURL('project', 'begin', getServerContext().container.path);
    }, [returnUrl])

    const onSelect = useCallback((provider) => {
        setProvider(provider);
    }, [])

    const onContainerSelect = useCallback((cont) => {
        setContainer(cont);
    }, [])

    const onSubmit = useCallback(() => {
        const cont = container ?? getServerContext().container.path;
        if (isFileUpload && xar) {
            uploadXarFile(xar, container).then(() => {
                window.location.href = ActionURL.buildURL('pipeline', 'status-showList', container);
            })
        }
        else {
            window.location.href = ActionURL.buildURL('assay', 'designer', cont, {
                'providerName': provider,
                'returnUrl': returnUrl
            });
        }
    }, [provider, returnUrl, container, isFileUpload, xar])

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
                    <AssayPicker
                        showImport={true}
                        onProviderSelect={onSelect}
                        onContainerSelect={onContainerSelect}
                        onFileChange={setXar}
                        setIsFileUpload={setIsFileUpload}
                    />
                </Panel.Body>
            </Panel>
            <div className={'assay-type-select-panel assay-type-select-btns'}>
                <Button onClick={onCancel}>Cancel</Button>
                <Button
                    className="pull-right"
                    bsStyle={'primary'}
                    onClick={onSubmit}
                    disabled={isFileUpload && !xar}
                >
                    {isFileUpload ? 'Import' : 'Choose ' + label + ' Assay'}
                </Button>
            </div>
        </>
    )

});