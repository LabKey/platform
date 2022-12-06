/*
 * Copyright (c) 2020 LabKey Corporation
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

import React, {PureComponent} from "react";
import {
    Alert,
    DatasetDesignerPanels,
    DatasetModel,
    fetchDatasetDesign,
    LoadingSpinner,
    BeforeUnload
} from "@labkey/components";
import { ActionURL, Domain, getServerContext } from "@labkey/api";

import "../DomainDesigner.scss"

interface State {
    model: DatasetModel,
    isLoadingModel: boolean,
    message?: string
}

export class App extends PureComponent<any, State> {

    private _dirty: boolean = false;

    constructor(props) {
        super(props);

        this.state = {
            model: undefined,
            isLoadingModel: true
        };
    }

    componentDidMount() {
        const { datasetId } = ActionURL.getParameters();

        fetchDatasetDesign(datasetId)
            .then((model: DatasetModel) => {
                this.setState(() => ({model, isLoadingModel: false}));
            })
            .catch((error) => {
                this.setState(() => ({message: error.exception, isLoadingModel: false}));
            });
    }

    handleWindowBeforeUnload = (event) => {
        if (this._dirty) {
            event.returnValue = 'Changes you made may not be saved.';
        }
    };

    navigate(defaultUrl: string) {
        this._dirty = false;

        const returnUrl = ActionURL.getReturnUrl();
        window.location.href = returnUrl || defaultUrl;
    }

    navigateOnComplete(model: DatasetModel) {
        // if the model comes back to here without the newly saved datasetId, query to get it
        if (model.datasetId && model.datasetId > 0) {
            this.navigate(ActionURL.buildURL('study', 'datasetDetails', getServerContext().container.path, {id: model.datasetId}));
        }
        else {
            Domain.getDomainDetails({
                containerPath: getServerContext().container.path,
                domainId: model.domain.domainId,
                success: (data) => {
                    const newModel = DatasetModel.create(undefined, data);
                    this.navigate(ActionURL.buildURL('study', 'datasetDetails', getServerContext().container.path, {id: newModel.datasetId}));
                },
                failure: (error) => {
                    // bail out and go to the study-begin page
                    this.navigate(ActionURL.buildURL('study', 'begin', getServerContext().container.path));
                }
            });
        }
    }

    onComplete = (model: DatasetModel) => {
        this.navigateOnComplete(model);
    };

    onCancel = () => {
        this.navigate(ActionURL.buildURL('study', 'begin', getServerContext().container.path));
    };

    onChange = (model: DatasetModel) => {
        this._dirty = true;
    };

    render() {
        const { isLoadingModel, message, model } = this.state;

        if (message) {
            return <Alert>{message}</Alert>
        }

        if (isLoadingModel) {
            return <LoadingSpinner/>
        }

        return (
            <BeforeUnload beforeunload={this.handleWindowBeforeUnload}>
                {model && model.isFromLinkedSource() &&
                    <p>
                        This dataset was created by linking {model.sourceType} data from <a href={model.sourceUrl}>{model.sourceName}</a>.
                    </p>
                }
                <DatasetDesignerPanels
                    initModel={model}
                    onCancel={this.onCancel}
                    useTheme={true}
                    onComplete={this.onComplete}
                    successBsStyle={'primary'}
                    onChange={this.onChange}
                />
            </BeforeUnload>
        )
    }
}