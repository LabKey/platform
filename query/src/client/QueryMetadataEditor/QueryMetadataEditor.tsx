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
    ConfirmModal,
    DomainDesign,
    DomainField,
    DomainForm,
    IBannerMessage,
    LoadingSpinner
} from "@labkey/components";
import {ActionURL, getServerContext} from "@labkey/api";
import {AliasFieldModal} from "./components/AliaseFieldModal";
import {fetchQueryMetadata, resetQueryMetadata, saveQueryMetadata} from "./actions";

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
    showEditSourceConfirmationModal: boolean,
    showResetConfirmationModal: boolean,
    showViewDataConfirmationModal: boolean,
    navigateAfterSave: boolean,
    userDefinedQuery: boolean
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
            showSave: true,
            dirty: false,
            showEditSourceConfirmationModal: false,
            showResetConfirmationModal: false,
            showViewDataConfirmationModal: false,
            navigateAfterSave: false,
            userDefinedQuery: false
        };
    }

    componentDidMount() {
        const { schemaName, queryName, messages } = this.state;

        if (schemaName && queryName) {
            fetchQueryMetadata(schemaName, queryName)
                .then((data) => {
                    const domain = DomainDesign.create(data.domainDesign ? data.domainDesign : data, undefined);
                    this.setState(() => ({
                        domain: domain,
                        userDefinedQuery: data.userDefinedQuery
                    }))
                })
                .catch((error) => {
                    this.setState(() => ({
                        messages: messages.set(0, {message: error.exception, messageType: 'danger'})
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

    dismissChangeConfirmation = () => {
        const { showViewDataConfirmationModal, showEditSourceConfirmationModal } = this.state;

        this.setState(() => ({
            showResetConfirmationModal: false,
            showViewDataConfirmationModal: false,
            showEditSourceConfirmationModal: false,
            dirty: false
        }), () => {
            if (showViewDataConfirmationModal) {
                this.navigateToViewData();
            }
            if (showEditSourceConfirmationModal) {
                this.navigateToEditSource();
            }
        });
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
            showSave: true,
            dirty: true
        }));
    };

    onConfirmEditSource = () => {
        const { dirty } = this.state;

        if (dirty) {
            this.onSaveBtnHandler(this.navigateToEditSource);
        }
        else {
            this.navigateToEditSource();
        }
    };

    navigateToViewData = () => {
        const { schemaName, queryName } = this.state;

        this.setState(() => ({dirty: false}), () => {
            window.location.href =  ActionURL.buildURL('query', 'executeQuery', getServerContext().container.path, {schemaName: schemaName, ['query.queryName']: queryName});
        });
    };

    onConfirmViewData = () => {
        const { dirty } = this.state;

        if (dirty) {
            this.onSaveBtnHandler(this.navigateToViewData);
        }
        else {
            this.navigateToViewData();
        }
    };

    onSaveBtnHandler = (onSaveNavigation) => {
        const { domain, schemaName, messages, navigateAfterSave, userDefinedQuery } = this.state;
        saveQueryMetadata(domain, schemaName, userDefinedQuery)
            .then(() => {
                this.showMessage("Save Successful", 'success', 0);

                if (navigateAfterSave) {
                    this.setState(() => ({
                        showSave: false,
                        dirty: false
                    }), () => {
                        onSaveNavigation();
                    });
                }
                else {
                    this.setState(() => ({
                        showSave: false,
                        dirty: false
                    }));
                }
            })
            .catch((error) => {
                this.setState(() => ({
                    messages: messages.set(0, {message: error.exception, messageType: 'danger'})
                }));
            });
    };

    navigateToEditSource = () =>  {
        const { schemaName, queryName } = this.state;

        this.setState(() => ({dirty: false}), () => {
            window.location.href =  ActionURL.buildURL('query', 'sourceQuery', getServerContext().container.path, {schemaName: schemaName, ['query.queryName']: queryName}) + '#metadata';
        });
    };

    editSourceBtnHandler = () => {
        const { dirty } = this.state;

        this.setState(() => ({
            navigateAfterSave: true
        }));

        if (dirty) {
            this.setState(() => ({
                showEditSourceConfirmationModal: true
            }));
        }
        else {
            this.onConfirmEditSource();
        }
    };

    viewDataBtnHandler = () => {
        const { dirty } = this.state;

        this.setState(() => ({
            navigateAfterSave: true
        }));

        if (dirty) {
            this.setState(() => ({
                showViewDataConfirmationModal: true
            }));
        }
        else {
            this.onConfirmViewData();
        }
    };

    onConfirmReset = () => {
        const { schemaName, queryName, messages } = this.state;

        resetQueryMetadata(schemaName, queryName)
            .then((domain) => {
                this.setState(() => ({
                    domain: domain,
                    showResetConfirmationModal: false
                }))
            })
            .catch((error) => {
                this.setState(() => ({
                    messages: messages.set(0, {message: error.exception, messageType: 'danger'})
                }));
            });
    };

    onResetBtnHandler = () => {
        this.setState(() => ({
            showResetConfirmationModal: true
        }));
    };

    renderButtons() {
        const { showSave, userDefinedQuery } = this.state;

        return (
            <div className={'domain-form-panel query-metadata-editor-buttons'}>
                { !userDefinedQuery && <Button onClick={this.aliasFieldBtnHandler}>Alias Field</Button> }
                <Button bsStyle='primary' className='pull-right' disabled={!showSave} onClick={this.onSaveBtnHandler}>Save</Button>
                <Button onClick={this.editSourceBtnHandler}>Edit Source</Button>
                <Button onClick={this.viewDataBtnHandler}>View Data</Button>
                <Button onClick={this.onResetBtnHandler}>Reset To Default</Button>
            </div>
        )
    }

    renderConfirmationModal(title: string, msg: string, onConfirm: any, onCancel: any, confirmButtonText: string, cancelButtonText: string) {
        return (
            <ConfirmModal
                title={title}
                msg={msg}
                onConfirm={onConfirm}
                onCancel={onCancel}
                confirmVariant='danger'
                confirmButtonText={confirmButtonText}
                cancelButtonText={cancelButtonText}
            />
        )
    }

    render() {
        const { domain, showAlias, messages, showEditSourceConfirmationModal, showResetConfirmationModal, showViewDataConfirmationModal } = this.state;
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
                        domainFormDisplayOptions= {{
                            showRequired: false,
                            isDragDisabled: true,
                            showValidators: false,
                            phiLevelDisabled: true,
                            showAddFieldsButton: false
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
                    <AliasFieldModal
                        domainFields={domain.fields}
                        showAlias={true}
                        onHide={this.onHideAliasField}
                        onAdd={this.onAddAliasField}
                    />
                }

                {
                    showResetConfirmationModal &&
                    this.renderConfirmationModal("Confirm Reset", "Are you sure you want to reset? You will lose any edits you made.",
                        this.onConfirmReset, this.dismissChangeConfirmation, "Reset", "Cancel")
                }

                {
                    showEditSourceConfirmationModal &&
                    this.renderConfirmationModal("Save Changes?", "Do you want to save your changes?",
                        this.onConfirmEditSource, this.dismissChangeConfirmation, "Yes, Save", "No, Edit Source")
                }

                {
                    showViewDataConfirmationModal &&
                    this.renderConfirmationModal("Save Changes?", "Do you want to save your changes?",
                        this.onConfirmViewData, this.dismissChangeConfirmation, "Yes, Save", "No, View Data")
                }
            </>
        )
    }
}

