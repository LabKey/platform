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
import {Button, Panel} from "react-bootstrap";
import {LoadingSpinner, IBannerMessage, DomainDesign, DomainForm, fetchQueryMetadata} from "@labkey/components";
import {ActionURL} from "@labkey/api";

import "@labkey/components/dist/components.css"

interface IAppState {
    dirty: boolean,
    domain: DomainDesign
    messages?: List<IBannerMessage>,
    queryName: string
    returnUrl: string
    schemaName: string
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
            messages
        };
    }

    componentDidMount() {
        const { schemaName, queryName, messages } = this.state;
        console.log("component did mount");

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

    renderButtons() {
        // const { submitting } = this.state;
        console.log("in render buttons");

        return (
            <div className={'domain-form-panel domain-designer-buttons'}>
                <Button onClick={this.onCancelBtnHandler}>Cancel</Button>
                {/*<Button className='pull-right' bsStyle='success' disabled={submitting} onClick={this.submitAndNavigate}>Save</Button>*/}
            </div>
        )
    }

    render() {
        const { domain } = this.state;
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
            </>
        )
    }
}