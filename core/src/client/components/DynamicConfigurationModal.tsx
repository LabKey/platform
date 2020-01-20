import React, { PureComponent } from 'react';
import { Button, FormControl, Modal } from 'react-bootstrap';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faTimes } from '@fortawesome/free-solid-svg-icons';

import FACheckBox from './FACheckBox';

import ReactBootstrapToggle from 'react-bootstrap-toggle';

import { LabelHelpTip, FileAttachmentForm } from '@labkey/components';
import '@labkey/components/dist/components.css';
import { ActionURL, Ajax } from '@labkey/api';

import SSOFields from './SSOFields';

interface Props {
    modalType?: AuthConfigProvider;
    stateSection?: string;
    description?: string;
    enabled?: boolean;
    canEdit?: boolean;
    title?: string;
    provider?: string;
    updateAuthRowsAfterSave?: Function;
    closeModal?: Function;
    configuration?: number;
    headerLogoUrl?: string;
    loginLogoUrl?: string;
    type?: AuthConfigProvider;
}

interface State {
    enabled?: boolean;
    description?: string;
    errorMessage?: string;
    auth_header_logo?: string;
    auth_login_page_logo?: string;
    deletedLogos?: string[];
    emptyRequiredFields?: null | string[];
    servers?: string;
    principalTemplate?: string;
    SASL?: string;
    search?: boolean;
}

export default class DynamicConfigurationModal extends PureComponent<Props, State> {
    constructor(props) {
        super(props);
        this.state = {
            enabled: this.props.enabled,
            description: this.props.description,
            errorMessage: '',
            auth_header_logo: '',
            auth_login_page_logo: '',
            deletedLogos: [],
            emptyRequiredFields: null,
        };
    }

    componentDidMount = () => {
        const fieldValues = {};

        this.props.modalType.settingsFields.forEach(field => {
            fieldValues[field.name] = field.name in this.props ? this.props[field.name] : field.defaultValue;
        });

        this.setState(() => ({
            ...fieldValues,
        }));
    };

    saveEditedModal = (): void => {
        const baseUrl = ActionURL.getBaseURL(true);
        const saveUrl = baseUrl + this.props.modalType.saveLink;
        const form = new FormData();

        if (this.areRequiredFieldsEmpty()) {
            return;
        }

        if (this.props.configuration) {
            form.append('configuration', (this.props.configuration).toString());
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
                this.props.updateAuthRowsAfterSave(result.response, this.props.stateSection);
                this.props.closeModal();
            },
        });
    };

    areRequiredFieldsEmpty = () => {
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
        this.setState({ enabled: !this.state.enabled });
    };

    handleChange = (event): void => {
        const { name, value } = event.target;
        this.setState(() => ({
            [name]: value,
        }));
    };

    handleDeleteLogo = (value: string) => {
        const arr = this.state.deletedLogos;
        arr.push(value);

        this.setState(() => ({ deletedLogos: arr }));
    };

    checkCheckBox = (name: string) => {
        const oldState = this.state[name];
        this.setState(() => ({
            [name]: !oldState,
        }));
    };

    onFileChange = (attachment, logoType: string) => {
        this.setState(() => ({ [logoType]: attachment.first() }));
    };

    dynamicallyCreateFields = (fields: AuthConfigField[], expandableOpen) => {
        let stopPoint = fields.length;
        for (let i = 0; i < fields.length; i++) {
            if ('dictateFieldVisibility' in fields[i]) {
                stopPoint = i + 1;
                break;
            }
        }

        const fieldsToCreate = expandableOpen ? fields : fields.slice(0, stopPoint);

        return fieldsToCreate.map((field, index) => {
            switch (field.type) {
                case 'input':
                    return (
                        <TextInput
                            key={index}
                            handleChange={this.handleChange}
                            value={this.state[field.name]}
                            type="text"
                            canEdit={this.props.canEdit}
                            emptyRequiredFields={this.state.emptyRequiredFields}
                            {...field}
                        />
                    );
                case 'checkbox':
                    return (
                        <CheckBoxInput
                            key={index}
                            checkCheckBox={this.checkCheckBox}
                            value={this.state[field.name]}
                            canEdit={this.props.canEdit}
                            {...field}
                        />
                    );
                case 'password':
                    if (!this.props.canEdit) {
                        return;
                    }
                    return (
                        <TextInput
                            key={index}
                            handleChange={this.handleChange}
                            value={this.state[field.name]}
                            type="password"
                            canEdit={this.props.canEdit}
                            {...field}
                        />
                    );

                case 'textarea':
                    return (
                        <SmallFileUpload
                            key={index}
                            index={index}
                            canEdit={this.props.canEdit}
                            onFileChange={this.onFileChange}
                            {...field}
                        />
                    );

                case 'options':
                    return (
                        <Option
                            key={index}
                            handleChange={this.handleChange}
                            value={this.state[field.name]}
                            options={field.options}
                            canEdit={this.props.canEdit}
                            {...field}
                        />
                    );

                case 'fixedHtml':
                    return <FixedHtml key={index} {...field} />;
                default:
                    return <div> Error: Invalid field type received. </div>;
            }
        });
    };

    render() {
        const { modalType, closeModal, canEdit } = this.props;
        const queryString = {
            server: this.state.servers,
            principal: this.state.principalTemplate,
            sasl: this.state.SASL,
        };
        const isAddNewConfig = this.props.title;
        const modalTitle = isAddNewConfig ? this.props.title : this.props.description;
        const finalizeButtonText = isAddNewConfig ? 'Finish' : 'Apply';

        return (
            <Modal show={true} onHide={() => {}}>
                <Modal.Header>
                    <Modal.Title>
                        {'Configure ' + modalTitle}
                        <FontAwesomeIcon size="sm" icon={faTimes} className="modal__close-icon" onClick={() => closeModal()} />
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
                            active={this.state.enabled}
                            className="modal__enable-toggle"
                            disabled={!canEdit}
                        />
                    </div>

                    <div className="bold-text modal__settings-text"> Settings </div>

                    <TextInput
                        handleChange={this.handleChange}
                        value={this.state.description}
                        type="text"
                        canEdit={this.props.canEdit}
                        emptyRequiredFields={this.state.emptyRequiredFields}
                        required={true}
                        name={"description"}
                        caption={"Description"}
                    />

                    {modalType && this.dynamicallyCreateFields(modalType.settingsFields, this.state.search)}

                    {modalType && modalType.sso && (
                        <SSOFields
                            headerLogoUrl={this.props.headerLogoUrl}
                            loginLogoUrl={this.props.loginLogoUrl}
                            onFileChange={this.onFileChange}
                            handleDeleteLogo={this.handleDeleteLogo}
                            canEdit={canEdit}
                        />
                    )}

                    <div className="modal__test-button">
                        {modalType.testLink && (
                            <Button
                                className="labkey-button"
                                onClick={() =>
                                    window.open(
                                        ActionURL.getBaseURL(true) +
                                            modalType.testLink +
                                            ActionURL.queryString(queryString)
                                    )
                                }>
                                Test
                            </Button>
                        )}
                    </div>

                    <div className="modal__error-message"> {this.state.errorMessage} </div>

                    <div className="modal__bottom">
                        <div className="modal__bottom-buttons">
                            <a target="_blank" href={modalType.helpLink} className="modal__help-link">
                                {'More about ' + this.props.provider + ' authentication'}
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

// To Reviewer: Using this gives me the error 'TS2339: Property 'includes' does not exist on type 'string[]'.'
// I tried adding 'es7' and then 'es2017' to my 'lib' in tsconfig.json, as stackoverflow suggested, to no avail
interface TextInputProps extends InputFieldProps {
    emptyRequiredFields?: string[] | null;
}

class TextInput extends PureComponent<any> {
    render() {
        const fieldIsRequiredAndEmpty =
            this.props.emptyRequiredFields && this.props.emptyRequiredFields.includes(this.props.name);

        return (
            <div className="modal__text-input">
                <span className="modal__field-label">
                    {this.props.caption} {this.props.required ? '*' : null}
                </span>

                {this.props.description && (
                    <LabelHelpTip
                        title="Tip"
                        body={() => {
                            return <div> {this.props.description} </div>;
                        }}
                    />
                )}

                {fieldIsRequiredAndEmpty && <div className="modal__tiny-error"> This field is required </div>}

                {this.props.canEdit ? (
                    <FormControl
                        name={this.props.name}
                        type={this.props.type}
                        value={this.props.value}
                        onChange={e => this.props.handleChange(e)}
                        className={"modal__text-input-field" + (fieldIsRequiredAndEmpty ? " modal__text-input-field--error" : "")}
                    />
                ) : (
                    <span className="modal__text-input-field"> {this.props.value} </span>
                )}
            </div>
        );
    }
}

interface CheckBoxInputProps extends InputFieldProps {
    checked?: boolean;
    checkCheckBox?: Function;
}

class CheckBoxInput extends PureComponent<CheckBoxInputProps> {
    render() {
        return (
            <div className="modal__field">
                <span className="modal__field-label">
                    {this.props.caption} {this.props.required ? '*' : null}
                </span>

                {this.props.description && (
                    <LabelHelpTip
                        title="Tip"
                        body={() => {
                            return <div> {this.props.description} </div>;
                        }}
                    />
                )}

                <span className="modal__input">
                    {this.props.canEdit ? (
                        <FACheckBox
                            name={this.props.name}
                            checked={this.props.value == 'true'}
                            canEdit={true}
                            onClick={() => {
                                this.props.checkCheckBox(this.props.name);
                            }}
                        />
                    ) : (
                        <FACheckBox name={this.props.name} checked={this.props.value == 'true'} canEdit={false} />
                    )}
                </span>
            </div>
        );
    }
}

interface OptionInputProps extends InputFieldProps {
    options: Record<string, string>;
}

class Option extends PureComponent<OptionInputProps> {
    render() {
        const { options } = this.props;
        return (
            <div className="modal__option-field">
                <span className="modal__field-label">
                    {this.props.caption} {this.props.required ? '*' : null}
                </span>

                {this.props.description && (
                    <LabelHelpTip
                        title="Tip"
                        body={() => {
                            return <div> {this.props.description} </div>;
                        }}
                    />
                )}

                {this.props.canEdit ? (
                    <div className="modal__option-input">
                        <FormControl
                            componentClass="select"
                            name={this.props.name}
                            onChange={this.props.handleChange}
                            value={this.props.value}>
                            {options &&
                                Object.keys(options).map(item => (
                                    <option value={item} key={item}>
                                        {' '}
                                        {options[item]}{' '}
                                    </option>
                                ))}
                        </FormControl>
                    </div>
                ) : (
                    <span className="modal__fixed-html-text"> {this.props.value} </span>
                )}
            </div>
        );
    }
}

interface FixedHtmlProps {
    caption: string;
    html?: string;
    key: number;
}

class FixedHtml extends PureComponent<FixedHtmlProps> {
    render() {
        return (
            <div className="modal__fixed-html-field">
                <span className="modal__field-label">{this.props.caption}</span>

                <div className="modal__fixed-html-text">
                    <div dangerouslySetInnerHTML={{ __html: this.props.html }} />
                </div>
            </div>
        );
    }
}

interface SmallFileInputProps extends InputFieldProps {
    text?: string;
    index: number;
    onFileChange: Function;
}

class SmallFileUpload extends PureComponent<SmallFileInputProps, any> {
    render() {
        return (
            <div className="modal__compact-file-upload-field">
                <span className="modal__field-label">
                    {this.props.caption} {this.props.required ? '*' : null}
                </span>

                {this.props.description && (
                    <LabelHelpTip
                        title="Tip"
                        body={() => {
                            return <div> {this.props.description} </div>;
                        }}
                    />
                )}

                <div className="modal__compact-file-upload-input">
                    <FileAttachmentForm
                        key={this.props.text}
                        index={this.props.index}
                        showLabel={false}
                        allowMultiple={false}
                        allowDirectories={false}
                        acceptedFormats=".txt,.doc,.docx"
                        showAcceptedFormats={false}
                        onFileChange={attachment => {
                            this.props.onFileChange(attachment, this.props.name);
                        }}
                        compact={true}
                    />
                </div>
            </div>
        );
    }
}
