/*
 * Copyright (c) 2019-2022 LabKey Corporation
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

import React from 'react';
import { ActionURL, getServerContext } from '@labkey/api';
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
} from '@labkey/components';

import '../DomainDesigner.scss';

interface State {
    sampleType?: DomainDetails
    isLoading: boolean,
    message?: string
}

const UPDATE_SAMPLE_TYPE_ACTION = 'updateMaterialSource';

export class App extends React.PureComponent<any, State> {

    private _dirty: boolean = false;

    constructor(props) {
        super(props);

        const action = ActionURL.getAction();
        let message;
        if (action === UPDATE_SAMPLE_TYPE_ACTION && !this.getRowIdParam()) {
            message = 'RowId parameter not supplied. Unable to determine which Sample Type to edit.';
        }

        this.state = {
            isLoading: true,
            message,
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
                    getSampleTypeDetails(undefined, domainId)
                        .then((sampleType: DomainDetails) => {
                            this.setState(()=> ({sampleType, isLoading: false}));
                        }).catch(error => {
                            this.setState(() => ({message: 'Sample type does not exist in this container for domainId ' + domainId + '.', isLoading: false}));
                        }
                    );
                })
                .catch(error => {
                    this.setState(() => ({message: 'Sample type does not exist in this container for rowId ' + rowId + '.', isLoading: false}));
                });
        }
        else {
            const { name, nameReadOnly } = ActionURL.getParameters();

            //Creating a new Sample Type
            getSampleTypeDetails()
                .then((sampleType: DomainDetails) => {
                    // allow for support of URL params for a name value that is readOnly
                    const updatedSampleType = sampleType.merge({
                        nameReadOnly,
                        domainDesign: sampleType.domainDesign.merge({ name })
                    }) as DomainDetails;

                    this.setState(()=> ({sampleType: updatedSampleType, isLoading: false}));
                }).catch(error => {
                    this.setState(() => ({message: error.exception, isLoading: false}));
                }
            );
        }
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
            ? ActionURL.buildURL('experiment', 'showSampleType', getServerContext().container.path, {rowId: rowId})
            : ActionURL.buildURL('experiment', 'listSampleTypes', getServerContext().container.path);

        this.navigate(url);
    };

    onCancel = () => {
        this.navigate(ActionURL.buildURL('experiment', 'listSampleTypes', getServerContext().container.path));
    };

    navigate(defaultUrl: string) {
        this._dirty = false;

        const returnUrl = ActionURL.getReturnUrl();
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

        const isUpdate = !!this.getRowIdParam();

        return (
            <BeforeUnload beforeunload={this.handleWindowBeforeUnload}>
                <SampleTypeDesigner
                    initModel={sampleType}
                    onComplete={this.onComplete}
                    onCancel={this.onCancel}
                    onChange={this.onChange}
                    includeDataClasses={true}
                    useTheme={true}
                    showLinkToStudy={true}
                    successBsStyle={'primary'}
                    showGenIdBanner={isUpdate}
                />
            </BeforeUnload>
        )
    }
}

