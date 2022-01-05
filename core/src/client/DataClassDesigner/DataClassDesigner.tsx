/*
 * Copyright (c) 2019 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import React from 'react'
import { ActionURL, getServerContext } from "@labkey/api";
import { Alert, BeforeUnload, DataClassDesigner, DataClassModel, fetchDataClass, LoadingSpinner } from "@labkey/components";

import "../DomainDesigner.scss"

type State = {
    model?: DataClassModel,
    isLoading: boolean,
    message?: string
}

export class App extends React.Component<any, State> {

    private _dirty: boolean = false;

    constructor(props)
    {
        super(props);

        this.state = {
            isLoading: true
        };
    }

    componentDidMount() {
        // if URL has a name or rowId, look up the data class info for the edit case
        // else we are in the create new data class case
        const { rowId, name } = ActionURL.getParameters();

        fetchDataClass(name, rowId)
            .then((model: DataClassModel) => {
                this.setState(() => ({model, isLoading: false}));
            })
            .catch((error) => {
                this.setState(() => ({message: error.exception, isLoading: false}));
            });
    }

    handleWindowBeforeUnload = (event: any) => {
        if (this._dirty) {
            event.returnValue = 'Changes you made may not be saved.';
        }
    };

    navigate(defaultUrl: string) {
        this._dirty = false;

        const returnUrl = ActionURL.getReturnUrl();
        window.location.href = returnUrl || defaultUrl;
    }

    onCancel = () => {
        this.navigate(ActionURL.buildURL('experiment', 'listDataClass', getServerContext().container.path));
    };

    onComplete = (model: DataClassModel) => {
        this.navigate(ActionURL.buildURL('experiment', 'showDataClass', getServerContext().container.path, {name: model.name}));
    };

    onChange = (model: DataClassModel) => {
        this._dirty = true;
    };

    render() {
        const { isLoading, message, model } = this.state;

        if (message) {
            return <Alert>{message}</Alert>
        }

        if (isLoading) {
            return <LoadingSpinner/>
        }

        const { rowId, name } = ActionURL.getParameters();
        const isUpdate = !!(rowId || name);

        return (
            <BeforeUnload beforeunload={this.handleWindowBeforeUnload}>
                <DataClassDesigner
                    initModel={model}
                    onCancel={this.onCancel}
                    onComplete={this.onComplete}
                    onChange={this.onChange}
                    successBsStyle={'primary'}
                    showGenIdBanner={isUpdate}
                />
            </BeforeUnload>
        )
    }
}

