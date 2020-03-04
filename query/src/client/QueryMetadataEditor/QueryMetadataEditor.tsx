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
import React,{PureComponent} from 'react';
import {Button} from "react-bootstrap";
import {
    LoadingSpinner,
    IBannerMessage,
    DomainDesign,
    DomainForm,
    fetchQueryMetadata,
    DomainField
} from "@labkey/components";
import {ActionURL} from "@labkey/api";
import {AliasField} from "./components/AliaseField";

import "@labkey/components/dist/components.css"

interface IAppState {
    dirty: boolean,
    domain: DomainDesign,
    messages?: List<IBannerMessage>,
    queryName: string,
    returnUrl: string,
    schemaName: string,
    showAlias: boolean
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
            showAlias : false
        };
    }

    componentDidMount() {
        const { schemaName, queryName, messages } = this.state;

        if (schemaName && queryName) {
            //TODO: remove domainId from this call
            fetchQueryMetadata(1, schemaName, queryName)
                .then(domain => {
                    console.log("success-app -", domain.toJS());
                    this.setState(() => ({domain}));
                })
                .catch(error => {
                    console.log("error");
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
        this.setState((state) => ({
            domain: newDomain,
            dirty: state.dirty || dirty // if the state is already dirty, leave it as such
        }));
    };

    onCancelBtnHandler = () => {
        if (this.state.dirty) {
            // this.setState(() => ({showConfirm: true}));
        }
        else {
            // this.navigate();
        }
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
            domain: newDomain
        }));
    };

    onSaveBtnHandler = () => {

    };

    editSourceBtnHandler = () => {

    };

    viewDataBtnHandler = () => {

    };

    onResetBtnHandler = () => {

    };

    renderButtons() {
        return (
            <div className={'domain-form-panel query-metadata-editor-buttons'}>
                <Button onClick={this.aliasFieldBtnHandler}>Alias Field</Button>
                <Button onClick={this.onSaveBtnHandler()}>Save</Button>
                <Button onClick={this.editSourceBtnHandler()}>Edit Source</Button>
                <Button onClick={this.viewDataBtnHandler()}>View Data</Button>
                <Button onClick={this.onResetBtnHandler()}>Reset To Default</Button>
                <Button onClick={this.onCancelBtnHandler}>Cancel</Button>
            </div>
        )
    }

    render() {
        const { domain, showAlias } = this.state;
        const isLoading = domain === undefined;

        if (isLoading) {
            return <LoadingSpinner/>
        }
        return (
            <>
                { domain &&
                <DomainForm
                        headerTitle={'Properties'}
                        helpTopic={'metadataSql'}
                        domain={domain}
                        onChange={this.onChangeHandler}
                        useTheme={false}
                />
                }

                { domain && this.renderButtons() }

                { showAlias &&
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

