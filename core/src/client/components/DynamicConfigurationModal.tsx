import React, { PureComponent } from 'react';
import { Button, Modal } from 'react-bootstrap';
import ReactBootstrapToggle from 'react-bootstrap-toggle';

import { ActionURL, Ajax } from '@labkey/api';

import { SSOFields } from './SSOFields';
import { DynamicFields, TextInput} from './DynamicFields';
import { AuthConfigProvider } from "./models";

interface Props {
    modalType?: AuthConfigProvider;
    configType?: string;
    description?: string;
    enabled?: boolean;
    canEdit: boolean;
    title?: string;
    provider?: string;
    updateAuthRowsAfterSave: Function;
    closeModal: Function;
    configuration?: number;
    headerLogoUrl?: string;
    loginLogoUrl?: string;
}

interface State {
    enabled?: boolean;
    description?: string;
    errorMessage?: string;
    auth_header_logo?: string;
    auth_login_page_logo?: string;
    deletedLogos?: string[];
    changedFiles?: string[];
    emptyRequiredFields?: string[];
    servers?: string;
    principalTemplate?: string;
    SASL?: string;
    search?: boolean;
}

export default class DynamicConfigurationModal extends PureComponent<Props, State> {
    constructor(props) {
        super(props);

        const fieldValues = {};
        this.props.modalType.settingsFields.forEach(field => {
            fieldValues[field.name] = field.name in this.props ? this.props[field.name] : field.defaultValue;
        });

        this.state = {
            enabled: this.props.enabled,
            description: this.props.description,
            errorMessage: '',
            auth_header_logo: '',
            auth_login_page_logo: '',
            deletedLogos: [],
            changedFiles: [],
            emptyRequiredFields: [],
            servers: '',
            principalTemplate: '',
            SASL: '',
            search: false,
            ...fieldValues,
        };
    }

    saveEditedModal = (): void => {
        const baseUrl = ActionURL.getBaseURL(true);
        const saveUrl = baseUrl + this.props.modalType.saveLink;
        let form = new FormData();

        if (this.areRequiredFieldsEmpty()) {
            return;
        }

        if (this.props.configuration) {
            form.append('configuration', this.props.configuration.toString());
        }

        Object.keys(this.state).map(item => {
            form.append(item, this.state[item]);
        });

        Ajax.request({
            url: saveUrl,
            method: 'POST',
            form,
            scope: this,
            failure: function(error) {
                const errorObj = JSON.parse(error.response);
                const errorMessage = errorObj.exception;
                this.setState(() => ({ errorMessage }));
            },
            success: function(result) {
                this.props.updateAuthRowsAfterSave(result.response, this.props.configType);
                this.props.closeModal();
            },
        });
    };

    areRequiredFieldsEmpty = () => {
        // Array of all required fields
        const requiredFields = this.props.modalType.settingsFields.reduce(
            (accum, current) => {
                if (current.required) {
                    accum.push(current.name);
                }
                return accum;
            },
            ['description']
        );

        const emptyRequiredFields = requiredFields.filter(name => this.state[name] == '');
        if (emptyRequiredFields.length > 0) {
            this.setState({ emptyRequiredFields });
            return true;
        } else {
            return false;
        }
    };

    onToggle = () => {
        this.setState(state => ({ enabled: !state.enabled }));
    };

    onChange = (event)  => {
        const { name, value } = event.target;
        this.setState(() => ({
            [name]: value,
        }));
    };

    handleDeleteLogo = (value: string) => {
        this.setState(state => ({ deletedLogos: [ ...state.deletedLogos, value] }));
    };

    checkCheckBox = (name: string) => {
        this.setState((state) => ({
            [name]: !state[name],
        }));
    };

    onFileChange = (attachment, logoType: string) => {
        this.setState(() => ({ [logoType]: attachment.first() }));
    };

    onFileRemoval = (name: string) => {
        const changedFiles = this.state.changedFiles;
        if (changedFiles.indexOf(name) === -1) {
            this.setState((state) => ({changedFiles: [...state.changedFiles, name]}));
        }
        this.setState({ [name]: '' });
    };

    render() {
        const { modalType, closeModal, canEdit, title } = this.props;
        const { emptyRequiredFields, enabled, description, search, ...restState } = this.state;
        const queryString = {
            server: this.state.servers,
            principal: this.state.principalTemplate,
            sasl: this.state.SASL,
        };
        const isAddNewConfig = title;
        const modalTitle = isAddNewConfig ? 'Add ' + title : 'Configure ' + this.props.description;
        const finalizeButtonText = isAddNewConfig ? 'Finish' : 'Apply';
        const requiredFieldEmpty = emptyRequiredFields.indexOf("description") !== -1;

        return (
            <Modal show={true} onHide={closeModal}>
                <Modal.Header closeButton>
                    <Modal.Title>
                        {modalTitle}
                    </Modal.Title>
                </Modal.Header>
                <Modal.Body>
                    <div className="modal__top">
                        <span className="bold-text"> Configuration Status </span>
                        <ReactBootstrapToggle
                            onClick={this.onToggle}
                            on="Enabled"
                            off="Disabled"
                            onstyle="primary"
                            active={enabled}
                            className="modal__enable-toggle"
                            disabled={!canEdit}
                        />
                    </div>

                    <div className="bold-text modal__settings-text"> Settings </div>

                    <TextInput
                        onChange={this.onChange}
                        value={description}
                        type="text"
                        canEdit={canEdit}
                        requiredFieldEmpty={requiredFieldEmpty}
                        required={true}
                        name="description"
                        caption="Description"
                    />

                    {modalType &&
                        <DynamicFields
                                fields={modalType.settingsFields}
                                search={search}
                                canEdit={canEdit}
                                emptyRequiredFields={emptyRequiredFields}
                                modalType={modalType}
                                onChange={this.onChange}
                                checkCheckBox={this.checkCheckBox}
                                onFileChange={this.onFileChange}
                                onFileRemoval={this.onFileRemoval}
                                {...restState}
                        />
                    }

                    {modalType && modalType.sso && (
                        <SSOFields
                            headerLogoUrl={this.props.headerLogoUrl}
                            loginLogoUrl={this.props.loginLogoUrl}
                            onFileChange={this.onFileChange}
                            handleDeleteLogo={this.handleDeleteLogo}
                            canEdit={canEdit}
                        />
                    )}

                    {modalType.testLink && (
                        <div className="modal__test-button">
                            <Button
                            className="labkey-button"
                            onClick={() =>
                                window.open(
                                    ActionURL.getBaseURL(true) +
                                        modalType.testLink +
                                            ActionURL.queryString(queryString)
                                    )
                                }
                            >
                                Test
                            </Button>
                        </div>
                    )}

                    <div className="modal__error-message"> {this.state.errorMessage} </div>

                    <div className="modal__bottom">
                        <div className="modal__bottom-buttons">
                            <a target="_blank" href={modalType.helpLink} className="modal__help-link">
                                {`More about ${this.props.provider} authentication`}
                            </a>

                            {canEdit ? (
                                <Button className="labkey-button primary" onClick={() => this.saveEditedModal()}>
                                    {finalizeButtonText}
                                </Button>
                            ) : null}
                        </div>

                        <Button className="labkey-button modal__save-button" onClick={closeModal}>
                            {canEdit ? 'Cancel' : 'Close'}
                        </Button>
                    </div>
                </Modal.Body>
            </Modal>
        );
    }
}
