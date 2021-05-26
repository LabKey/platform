import React, { PureComponent } from 'react';
import { FormControl } from 'react-bootstrap';
import { FileAttachmentForm, LabelHelpTip } from '@labkey/components';
import { Utils } from '@labkey/api';

import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { faFileAlt } from '@fortawesome/free-solid-svg-icons';

import FACheckBox from './FACheckBox';
import { AuthConfig, AuthConfigField, AuthConfigProvider, InputFieldProps } from './models';

interface TextInputProps extends InputFieldProps {
    requiredFieldEmpty?: boolean;
}

export class TextInput extends PureComponent<TextInputProps> {
    render() {
        const { description, caption, required, canEdit, requiredFieldEmpty, onChange, name, type, value } = this.props;

        return (
            <div className="modal__text-input">
                <span className="modal__field-label">
                    {caption}
                    {description && (
                        <LabelHelpTip title="Tip">
                            <div> {description} </div>
                        </LabelHelpTip>
                    )}
                    {required ? ' *' : null}
                </span>

                {requiredFieldEmpty && <div className="modal__tiny-error"> This field is required </div>}

                {canEdit ? (
                    <FormControl
                        name={name}
                        type={type}
                        value={value}
                        onChange={onChange}
                        className={
                            'modal__text-input-field' + (requiredFieldEmpty ? ' modal__text-input-field--error' : '')
                        }
                    />
                ) : (
                    <span className="modal__text-input-field"> {value} </span>
                )}
            </div>
        );
    }
}

interface CheckBoxInputProps extends AuthConfigField {
    checkCheckBox?: Function;
    value: boolean;
    canEdit: boolean;
}

export class CheckBoxInput extends PureComponent<CheckBoxInputProps> {
    render() {
        const { caption, description, name, value, required } = this.props;

        return (
            <div className="modal__field">
                <span className="modal__field-label">
                    {caption}
                    {description && (
                        <LabelHelpTip title="Tip">
                            <div> {description} </div>
                        </LabelHelpTip>
                    )}
                    {required ? ' *' : null}
                </span>

                <span className="modal__input">
                    {this.props.canEdit ? (
                        <FACheckBox
                            name={name}
                            checked={value}
                            canEdit={true}
                            onClick={() => {
                                this.props.checkCheckBox(name);
                            }}
                        />
                    ) : (
                        <FACheckBox name={name} checked={value} canEdit={false} onClick={null} />
                    )}
                </span>
            </div>
        );
    }
}

interface OptionInputProps extends InputFieldProps {
    options: { [key: string]: string };
}

export class Option extends PureComponent<OptionInputProps> {
    render() {
        const { options, caption, required, description, canEdit, name, value, onChange } = this.props;
        return (
            <div className="modal__option-field">
                <span className="modal__field-label">
                    {caption}
                    {description && (
                        <LabelHelpTip title="Tip">
                            <div> {description} </div>
                        </LabelHelpTip>
                    )}
                    {required ? ' *' : null}
                </span>

                {canEdit ? (
                    <div className="modal__option-input">
                        <FormControl componentClass="select" name={name} onChange={onChange} value={value}>
                            {Object.keys(options).map(item => (
                                <option value={item} key={item}>
                                    {options[item]}
                                </option>
                            ))}
                        </FormControl>
                    </div>
                ) : (
                    <span className="modal__fixed-html-text"> {value} </span>
                )}
            </div>
        );
    }
}

interface FixedHtmlProps {
    caption: string;
    html?: string;
    description?: string;
    authConfig: AuthConfig;
}

export class FixedHtml extends PureComponent<FixedHtmlProps> {
    render() {
        const { description, caption, html, authConfig } = this.props;

        // Issue42885: Upon detecting the parameter substitution pattern ('${someVariable}') we attempt to replace it
        // with the passed dynamic variable value existing on 'authConfig'. If we cannot, we replace the pattern with
        // the text 'someVariable' itself.
        const stringTemplatedHtml = html.replace(/\${(.*?)}/g, (match, value) => {
            return Utils.encodeHtml(
                authConfig[value] !== undefined && authConfig[value] !== null ? authConfig[value].toString() : value
            );
        });

        return (
            <div className="modal__fixed-html-field">
                <span className="modal__field-label">{caption}</span>
                {description && (
                    <LabelHelpTip title="Tip">
                        <div> {description} </div>
                    </LabelHelpTip>
                )}

                {/* HTML set is text-only information that lives on the server */}
                <div className="modal__fixed-html-text">
                    <div dangerouslySetInnerHTML={{ __html: stringTemplatedHtml }} />
                </div>
            </div>
        );
    }
}

interface SmallFileInputProps extends InputFieldProps {
    text?: string;
    index: number;
    onFileChange: Function;
    onFileRemoval: Function;
    requiredFieldEmpty?: boolean;
}

export class SmallFileUpload extends PureComponent<SmallFileInputProps> {
    render() {
        const { requiredFieldEmpty, description, canEdit, name, caption, required, value } = this.props;

        return (
            <div className="modal__compact-file-upload-field">
                <span className="modal__field-label">
                    {caption}
                    {description && (
                        <LabelHelpTip title="Tip">
                            <div> {description} </div>
                        </LabelHelpTip>
                    )}
                    {required ? ' *' : null}
                </span>

                {requiredFieldEmpty && (
                    <div className="modal__tiny-error--small-file-input"> This file is required </div>
                )}

                {canEdit ? (
                    <div className="modal__compact-file-upload-input">
                        <FileAttachmentForm
                            index={this.props.index}
                            showLabel={false}
                            allowMultiple={false}
                            allowDirectories={false}
                            acceptedFormats=".txt,.pem,.crt"
                            showAcceptedFormats={false}
                            onFileChange={attachment => {
                                this.props.onFileChange(attachment, name);
                            }}
                            onFileRemoval={() => {
                                this.props.onFileRemoval(name);
                            }}
                            compact={true}
                            initialFileNames={value ? [''] : undefined}
                        />
                    </div>
                ) : (
                    value && (
                        <div className="modal__pem-input">
                            <FontAwesomeIcon icon={faFileAlt} className="attached-file--icon" />
                        </div>
                    )
                )}
            </div>
        );
    }
}

interface DynamicFieldsProps {
    fields: AuthConfigField[];
    fieldValues: any;
    canEdit: boolean;
    modalType: AuthConfigProvider;
    emptyRequiredFields: string[];
    authConfig: AuthConfig;
    onChange: (event) => void;
    checkCheckBox: (string) => void;
    onFileChange: (attachment, logoType: string) => void;
    onFileRemoval: (name: string) => void;
}

export class DynamicFields extends PureComponent<DynamicFieldsProps> {
    render() {
        const {
            fields,
            emptyRequiredFields,
            canEdit,
            onChange,
            checkCheckBox,
            onFileChange,
            onFileRemoval,
            fieldValues,
            authConfig,
        } = this.props;
        let stopPoint = fields.findIndex(field => 'dictateFieldVisibility' in field) + 1;
        if (stopPoint === 0) {
            stopPoint = fields.length;
        }
        const fieldsToCreate = fieldValues.search ? fields : fields.slice(0, stopPoint);

        const allFields = fieldsToCreate.map((field, index) => {
            const requiredFieldEmpty = emptyRequiredFields.indexOf(field.name) !== -1;
            const name = fieldValues[field.name];

            switch (field.type) {
                case 'input':
                    return (
                        <TextInput
                            key={index}
                            onChange={onChange}
                            value={name}
                            canEdit={canEdit}
                            requiredFieldEmpty={requiredFieldEmpty}
                            defaultValue={field.defaultValue}
                            name={field.name}
                            caption={field.caption}
                            description={field.description}
                            required={field.required}
                            type={field.type}
                        />
                    );
                case 'checkbox':
                    return (
                        <CheckBoxInput
                            key={index}
                            checkCheckBox={checkCheckBox}
                            value={name}
                            canEdit={canEdit}
                            defaultValue={field.defaultValue}
                            name={field.name}
                            caption={field.caption}
                            description={field.description}
                            required={field.required}
                            type={field.type}
                        />
                    );
                case 'password':
                    if (!canEdit) {
                        return;
                    }
                    return (
                        <TextInput
                            key={index}
                            onChange={onChange}
                            value={name}
                            canEdit={canEdit}
                            defaultValue={field.defaultValue}
                            name={field.name}
                            caption={field.caption}
                            description={field.description}
                            required={field.required}
                            type="password"
                        />
                    );

                case 'pem':
                    return (
                        <SmallFileUpload
                            key={index}
                            onFileChange={onFileChange}
                            onFileRemoval={onFileRemoval}
                            value={name}
                            index={index + 3} // There are two other FileAttachmentForms (from SSOFields) on modal
                            canEdit={canEdit}
                            requiredFieldEmpty={requiredFieldEmpty}
                            defaultValue={field.defaultValue}
                            name={field.name}
                            caption={field.caption}
                            required={field.required}
                            type={field.type}
                        />
                    );

                case 'options':
                    return (
                        <Option
                            key={index}
                            onChange={onChange}
                            value={name}
                            canEdit={canEdit}
                            options={field.options}
                            defaultValue={field.defaultValue}
                            name={field.name}
                            caption={field.caption}
                            description={field.description}
                            required={field.required}
                            type={field.type}
                        />
                    );

                case 'fixedHtml':
                    return (
                        <FixedHtml
                            key={index}
                            caption={field.caption}
                            html={field.html}
                            description={field.description}
                            authConfig={authConfig}
                        />
                    );

                default:
                    return <div> Error: Invalid field type received. </div>;
            }
        });
        return <>{allFields}</>;
    }
}
