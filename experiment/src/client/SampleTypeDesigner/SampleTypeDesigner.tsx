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

import { List, Map } from "immutable";
import * as React from 'react'
import { ActionURL } from "@labkey/api";
import {
    Alert,
    DomainDetails,
    getSampleSet,
    getSampleTypeDetails,
    IBannerMessage,
    SampleTypeDesigner,
    SchemaQuery
} from "@labkey/components"

import "@labkey/components/dist/components.css"
import "./sampleTypeDesigner.scss";

interface IAppState {
    dirty: boolean
    sampleType: DomainDetails
    rowId: number
    domainId?: number
    messages?: List<IBannerMessage>,
    queryName: string
    returnUrl: string
    schemaName: string
    showConfirm: boolean
    submitting: boolean
    includeWarnings: boolean
    showWarnings: boolean
    badDomain: DomainDetails
    name?: string
    nameReadOnly?: boolean
}

const CREATE_SAMPLE_SET_ACTION = 'createSampleSet';

export class App extends React.PureComponent<any, Partial<IAppState>> {

    constructor(props) {
        super(props);

        const { RowId, schemaName, queryName, domainId, returnUrl, name, nameReadOnly } = ActionURL.getParameters();
        const action = ActionURL.getAction();

        let messages = (action !== CREATE_SAMPLE_SET_ACTION) ?
            this.checkUpdateActionParameters( RowId,) :
            List<IBannerMessage>();

        this.state = {
            schemaName,
            queryName,
            domainId,
            rowId: RowId,
            returnUrl,
            submitting: false,
            messages,
            showConfirm: false,
            dirty: false,
            includeWarnings: true,
            name,
            nameReadOnly,
        };
    }

    componentDidMount() {
        const { rowId, messages } = this.state;

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
                    this.setState(() => ({
                        messages: messages.set(0, {message: error.exception, messageType: 'danger'})
                    }));
                });
        }
        else {
            //Creating a new Sample Type
            this.setState(()=>({
                sampleType: DomainDetails.create(Map<string, any> ({
                    domainDesign: {name: this.state.name},
                    nameReadOnly: this.state.nameReadOnly
                }))
            }));
        }

        window.addEventListener("beforeunload", this.handleWindowBeforeUnload);
    }

    /**
     * Verify that the needed parameters are supplied either
     * @param rowId of sample type
     * @returns List of error messages
     */
    private checkUpdateActionParameters = ( rowId: number): List<IBannerMessage> => {
        let messages = List<IBannerMessage>();

        if ( !rowId) {
            let msg =  'RowId parameter not supplied, unable to determine which Sample Type to edit.';
            let msgType = 'danger';
            let bannerMsg ={message: msg, messageType: msgType};
            messages = messages.push(bannerMsg);
        }

        return messages;
    };

    /**
     * Look up full Sample Type domain, including fields
     **/
    private fetchSampleTypeDomain = (domainId, schemaName?, queryName?): void => {
        getSampleTypeDetails( SchemaQuery.create(schemaName, queryName), domainId)
            .then( sampleType => {
                this.setState(()=> ({sampleType}));
            }).catch(error => {
                const {messages} = this.state;

                this.setState(() => ({
                    messages: messages.set(0, {message: error.exception, messageType: 'danger'})
                }));
            }
        );
    };

    handleWindowBeforeUnload = (event) => {
        if (this.state.dirty) {
            event.returnValue = 'Changes you made may not be saved.';
        }
    };

    componentWillUnmount() {
        window.removeEventListener("beforeunload", this.handleWindowBeforeUnload);
    }

    submitAndNavigate = (response: any) => {
        this.setState(() => ({
            submitting: false,
            dirty: false
        }));

        this.showMessage("Save Successful", 'success', 0);

        this.navigate();
    };

    dismissAlert = (index: any) => {
        this.setState(() => ({
            messages: this.state.messages.setIn([index], [{message: undefined, messageType: undefined}])
        }));
    };

    showMessage = (message: string, messageType: string, index: number, additionalState?: Partial<IAppState>) => {
        const { messages } = this.state;

        this.setState(Object.assign({}, additionalState, {
            messages : messages.set(index, {message: message, messageType: messageType})
        }));
    };

    onCancelBtnHandler = () => {
        this.navigate();
    };

    navigate = () => {
        const { returnUrl } = this.state;
        this.setState(() => ({dirty: false}), () => {
            window.location.href = returnUrl || ActionURL.buildURL('project', 'begin', LABKEY.container.path);
        });
    };

    beforeFinish = ():void => {};

    render() {
        const { sampleType, messages } = this.state;

        return (
            <>
                { messages && messages.size > 0 && messages.map((bannerMessage, idx) => {
                    return (<Alert key={idx} bsStyle={bannerMessage.messageType} onDismiss={() => this.dismissAlert(idx)}>{bannerMessage.message}</Alert>) })
                }

                {sampleType &&
                <SampleTypeDesigner
                    initModel={sampleType}
                    beforeFinish={this.beforeFinish}
                    onComplete={this.submitAndNavigate}
                    onCancel={this.onCancelBtnHandler}
                    includeDataClasses={true}
                    useTheme={true}
                    appPropertiesOnly={false}
                    successBsStyle={'primary'}
                />
                }
            </>
        )
    }
}

