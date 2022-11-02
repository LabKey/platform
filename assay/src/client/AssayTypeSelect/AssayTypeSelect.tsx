import React, { FC, memo, useCallback, useEffect, useMemo, useState } from 'react';
import { ActionURL, Ajax, getServerContext, Utils } from '@labkey/api';
import { AssayPicker, AssayPickerSelectionModel, AssayPickerTabs, GENERAL_ASSAY_PROVIDER_NAME, App as LabKeyApp } from '@labkey/components';
import { Button, Panel } from 'react-bootstrap';

import './AssayTypeSelect.scss';

function uploadXarFile(
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
    const [ returnUrl, setReturnUrl ] = useState<string>();
    const [ assayPickerSelection, setAssayPickerSelection ] = useState<AssayPickerSelectionModel>({
        provider: undefined,
        container: "",
        file: undefined,
        tab: undefined
    })

    useEffect(() => {
        setReturnUrl(ActionURL.getParameter('returnUrl'));
    }, [])

    const tab = useMemo(() => ActionURL.getParameter('tab'), [])

    const onCancel = useCallback(() => {
        window.location.href = returnUrl || ActionURL.buildURL('project', 'begin', getServerContext().container.path);
    }, [returnUrl])

    const onChange = useCallback((model: AssayPickerSelectionModel) => {
        setAssayPickerSelection(model);
    }, [])

    const onSubmit = useCallback(() => {
        const container = assayPickerSelection.container ?? getServerContext().container.path;
        if (assayPickerSelection.tab === AssayPickerTabs.XAR_IMPORT_TAB
            && assayPickerSelection.file) {
            uploadXarFile(assayPickerSelection.file, assayPickerSelection.container).then(() => {
                window.location.href = ActionURL.buildURL('pipeline', 'status-showList', container);
            })
        }
        else {
            window.location.href = ActionURL.buildURL('assay', 'designer', container, {
                'providerName': assayPickerSelection.provider.name,
                'returnUrl': returnUrl
            });
        }
    }, [assayPickerSelection, returnUrl])

    const label = (!assayPickerSelection.provider || assayPickerSelection.provider.name === GENERAL_ASSAY_PROVIDER_NAME) ? "Standard" : assayPickerSelection.provider.name

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
                        defaultTab={tab}
                        hasPremium={LabKeyApp.hasPremiumModule()}
                        onChange={onChange}
                        showContainerSelect
                        showImport
                    />
                </Panel.Body>
            </Panel>
            <div className={'assay-type-select-panel assay-type-select-btns'}>
                <Button onClick={onCancel}>Cancel</Button>
                <Button
                    className="pull-right"
                    bsStyle={'primary'}
                    onClick={onSubmit}
                    disabled={assayPickerSelection.tab === AssayPickerTabs.XAR_IMPORT_TAB
                        && !assayPickerSelection.file}
                >
                    {assayPickerSelection.tab === AssayPickerTabs.XAR_IMPORT_TAB ? 'Import' : 'Choose ' + label + ' Assay'}
                </Button>
            </div>
        </>
    )

});