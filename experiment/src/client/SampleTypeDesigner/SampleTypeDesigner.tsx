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
import { Map } from "immutable";
import { ActionURL, getServerContext } from "@labkey/api";
import {
    Alert,
    BeforeUnload,
    DomainDesign,
    DomainDetails,
    getSampleSet,
    getSampleTypeDetails,
    LoadingSpinner,
    SampleTypeDesigner,
    SampleTypeModel
} from "@labkey/components"

import "@labkey/components/dist/components.css"

interface State {
    sampleType?: DomainDetails
    isLoading: boolean,
    message?: string
    name?: string
    nameReadOnly?: boolean
}

const UPDATE_SAMPLE_SET_ACTION = 'updateMaterialSource';

export class App extends React.PureComponent<any, State> {

    private _dirty = false;

    constructor(props) {
        super(props);

        const { name, nameReadOnly } = ActionURL.getParameters();
        const action = ActionURL.getAction();

        let message;
        if (action === UPDATE_SAMPLE_SET_ACTION && !this.getRowIdParam()) {
            message = 'RowId parameter not supplied. Unable to determine which Sample Set to edit.';
        }

        this.state = {
            isLoading: true,
            message,
            name,
            nameReadOnly,
        };
    }

    getRowIdParam(): number {
        const { RowId, rowId } = ActionURL.getParameters();
        return RowId || rowId;
    }

    componentDidMount() {
        // if the URL has a RowId param, look up the sample type info for the edit case
        // else we are in the create new sample type case
        const rowId = this.getRowIdParam();
        if (rowId) {
            //Get SampleType from experiment service
            getSampleSet({rowId})
                .then(results => {
                    const sampleSet = results.get('sampleSet');
                    const {domainId} = sampleSet;

                    // Then query for actual domain design
                    this.fetchSampleTypeDomain(domainId);
                })
                .catch(error => {
                    console.error(error);
                    this.setState(() => ({message: 'Sample set does not exist in this container for rowId ' + rowId + '.', isLoading: false}));
                });
        }
        else {
            //Creating a new Sample Type
            this.setState(()=>({
                isLoading: false,
                sampleType: DomainDetails.create(Map<string, any> ({
                    domainDesign: {name: this.state.name},
                    nameReadOnly: this.state.nameReadOnly
                }))
            }));
        }
    }

    /**
     * Look up full Sample Type domain, including fields
     **/
    fetchSampleTypeDomain(domainId) {
        getSampleTypeDetails(undefined, domainId)
            .then( sampleType => {
                this.setState(()=> ({sampleType, isLoading: false}));
            }).catch(error => {
                console.error(error);
                this.setState(() => ({message: 'Sample set does not exist in this container for domainId ' + domainId + '.', isLoading: false}));
            }
        );
    }

    handleWindowBeforeUnload = (event: any) => {
        if (this._dirty) {
            event.returnValue = 'Changes you made may not be saved.';
        }
    };

    onChange = (model: SampleTypeModel) => {
        this._dirty = true;
    };

    onComplete = (response: DomainDesign) => {
        const rowId = this.getRowIdParam();
        const url = rowId
            ? ActionURL.buildURL('experiment', 'showMaterialSource', getServerContext().container.path, {rowId: rowId})
            : ActionURL.buildURL('experiment', 'listMaterialSources', getServerContext().container.path);

        this.navigate(url);
    };

    onCancel = () => {
        this.navigate(ActionURL.buildURL('experiment', 'listMaterialSources', getServerContext().container.path));
    };

    navigate(defaultUrl: string) {
        this._dirty = false;

        const returnUrl = ActionURL.getParameter('returnUrl');
        window.location.href = returnUrl || defaultUrl;
    }

    render() {
        const { isLoading, message, sampleType } = this.state;

        if (message) {
            return <Alert>{message}</Alert>
        }

        if (isLoading) {
            return <LoadingSpinner/>
        }

        return (
            <BeforeUnload beforeunload={this.handleWindowBeforeUnload}>
                <SampleTypeDesigner
                    initModel={sampleType}
                    onComplete={this.onComplete}
                    onCancel={this.onCancel}
                    onChange={this.onChange}
                    includeDataClasses={true}
                    useTheme={true}
                    appPropertiesOnly={false}
                    successBsStyle={'primary'}
                />
            </BeforeUnload>
        )
    }
}

