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

import {List} from "immutable";
import React, {PureComponent} from 'react';
import {Button} from "react-bootstrap";
import {
    Alert,
    buildURL,
    DomainDesign,
    DomainField,
    DomainForm,
    fetchQueryMetadata,
    IBannerMessage,
    LoadingSpinner
} from "@labkey/components";
import {ActionURL, Ajax, Utils} from "@labkey/api";
import {AliasField} from "./components/AliaseField";

import "@labkey/components/dist/components.css"
import "./queryMetadataEditor.scss";

interface IAppState {
    dirty: boolean,
    domain: DomainDesign,
    messages?: List<IBannerMessage>,
    queryName: string,
    returnUrl: string,
    schemaName: string,
    showAlias: boolean,
    showSave: boolean,
}

export class App extends PureComponent<any, Partial<IAppState>> {

    constructor(props) {
        super(props);

        const { schemaName, queryName, returnUrl } = ActionURL.getParameters();

        let messages = List<IBannerMessage>();
        if ((!schemaName || !queryName)) {
            let msg =  'Missing required parameter: schemaName and queryName.';
            let msgType = 'danger';
            let bannerMsg ={message : msg, messageType : msgType};
            messages = messages.push(bannerMsg);
        }

        this.state = {
            schemaName,
            queryName,
            returnUrl,
            messages,
            showAlias : false,
            showSave: false
        };
    }

    componentDidMount() {
        const { schemaName, queryName, messages } = this.state;

        if (schemaName && queryName) {
            //TODO: remove domainId from this call
            fetchQueryMetadata(1, schemaName, queryName)
                .then(domain => {
                    this.setState(() => ({domain}));
                })
                .catch(error => {
                    this.setState(() => ({
                        messages : messages.set(0, {message: error.exception, messageType: 'danger'})
                    }));
                });
        }
        window.addEventListener("beforeunload", this.handleWindowBeforeUnload);
    }

    handleWindowBeforeUnload = (event) => {
        if (this.state.dirty) {
            event.returnValue = 'Changes you made may not be saved.';
        }
    };

    onChangeHandler = (newDomain, dirty) => {
        console.log("called");
        this.setState((state) => ({
            domain: newDomain,
            dirty: state.dirty || dirty, // if the state is already dirty, leave it as such
            showSave: true
        }));
    };

    showMessage = (message: string, messageType: string, index: number, additionalState?: Partial<IAppState>) => {
        const { messages } = this.state;

        this.setState(Object.assign({}, additionalState, {
            messages : messages.set(index, {message: message, messageType: messageType})
        }));
    };

    dismissAlert = (index: any) => {
        this.setState(() => ({
            messages: this.state.messages.setIn([index], [{message: undefined, messageType: undefined}])
        }));
    };

    aliasFieldBtnHandler = () => {
        this.setState(() => ({showAlias: true}));
    };

    onHideAliasField = (): any => {
        this.setState(() => ({showAlias: false}));
    };

    onAddAliasField = (domainField: DomainField): any => {
        const { domain } = this.state;
        const newFields = domain.fields.push(domainField);
        const newDomain = domain.set('fields', newFields) as DomainDesign;

        this.setState(() => ({
            showAlias: false,
            domain: newDomain,
            showSave: true
        }));
    };

    onSaveBtnHandler = () => {
        const { domain, schemaName, messages } = this.state;

        Ajax.request({
            url: buildURL('query', 'saveQueryMetadata.api'),
            method: 'POST',
            success: Utils.getCallbackWrapper(() => {
                this.showMessage("Save Successful", 'success', 0);
                this.setState(() => ({
                    showSave: false
                }));
            }),
            failure: Utils.getCallbackWrapper((error) => {
                this.setState(() => ({
                    messages: messages.set(0, {message: error.exception, messageType: 'danger'})
                }))
            }),
            jsonData: {
                domain: DomainDesign.serialize(domain),
                schemaName: schemaName
            }
        });
    };

    editSourceBtnHandler = () => {
        const { schemaName, queryName } = this.state;
        this.setState(() => ({dirty: false}), () => {
            window.location.href =  ActionURL.buildURL('query', 'sourceQuery', LABKEY.container.path, {schemaName: schemaName, ['query.queryName']: queryName}) + '#metadata';
        });
    };

    viewDataBtnHandler = () => {
        const { schemaName, queryName } = this.state;
        this.setState(() => ({dirty: false}), () => {
            window.location.href =  ActionURL.buildURL('query', 'executeQuery', LABKEY.container.path, {schemaName: schemaName, ['query.queryName']: queryName});
        });
    };

    onResetBtnHandler = () => {
        const { schemaName, queryName, messages } = this.state;

        Ajax.request({
            url: buildURL('query', 'resetQueryMetadata.api'),
            method: 'POST',
            success: Utils.getCallbackWrapper((data) => {
                this.setState(() => ({
                    domain: DomainDesign.create(data, undefined)
                }))
            }),
            failure: Utils.getCallbackWrapper((error) => {
                this.setState(() => ({
                    messages: messages.set(0, {message: error.exception, messageType: 'danger'})
                }))
            }),
            params : {
                schemaName : schemaName,
                queryName : queryName
            }
        });
    };

    renderButtons() {
        const { showSave } = this.state;

        return (
            <div className={'domain-form-panel query-metadata-editor-buttons'}>
                <Button onClick={this.aliasFieldBtnHandler}>Alias Field</Button>
                <Button bsStyle='primary' className='pull-right' disabled={!showSave} onClick={this.onSaveBtnHandler}>Save</Button>
                <Button onClick={this.editSourceBtnHandler}>Edit Source</Button>
                <Button onClick={this.viewDataBtnHandler}>View Data</Button>
                <Button onClick={this.onResetBtnHandler}>Reset To Default</Button>
            </div>
        )
    }

    render() {
        const { domain, showAlias, messages } = this.state;
        const isLoading = domain === undefined;

        if (isLoading) {
            return <LoadingSpinner/>
        }
        return (
            <>
                {
                    domain &&
                    <DomainForm
                        headerTitle={'Metadata Properties'}
                        helpTopic={'metadataSql'}
                        domain={domain}
                        onChange={this.onChangeHandler}
                        useTheme={false}
                        hideAddFieldsButton={true}
                        domainFormDisplayOptions= {{
                            showRequired: false,
                            isDragDisabled: true,
                            showValidators: false,
                            phiLevelDisabled: true
                        }}
                    />
                }

                {
                    messages &&
                    messages.size > 0 &&
                    messages.map((bannerMessage, idx) => {
                        return (
                            <Alert
                                key={idx}
                                bsStyle={bannerMessage.messageType}
                                onDismiss={() => this.dismissAlert(idx)}>
                                {bannerMessage.message}
                            </Alert>
                        )
                    })
                }

                { domain && this.renderButtons() }

                {
                    showAlias &&
                    <AliasField
                        domainFields={domain.fields}
                        showAlias={true}
                        onHide={this.onHideAliasField}
                        onAdd={this.onAddAliasField}
                    />
                }
            </>
        )
    }
}

