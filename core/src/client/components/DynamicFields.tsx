import React, {PureComponent} from "react";
import { FormControl } from 'react-bootstrap';
import {FileAttachmentForm, LabelHelpTip} from "@labkey/components";
import FACheckBox from "./FACheckBox";
import { AuthConfigField, AuthConfigProvider, InputFieldProps } from "./models";
import {FontAwesomeIcon} from "@fortawesome/react-fontawesome";
import {faFileAlt} from "@fortawesome/free-solid-svg-icons";

interface TextInputProps extends InputFieldProps {
    requiredFieldEmpty?: boolean;
}

export class TextInput extends PureComponent<TextInputProps> {
    render() {
        const { description, caption, required, canEdit, requiredFieldEmpty, onChange, name, type, value } = this.props;

        return (
            <div className="modal__text-input">
                <span className="modal__field-label">
                    {caption} {required ? '*' : null}
                </span>

                {description && (
                    <LabelHelpTip
                        title="Tip"
                        body={() => {
                            return <div> {description} </div>;
                        }}
                    />
                )}

                {requiredFieldEmpty && <div className="modal__tiny-error"> This field is required </div>}

                {canEdit ? (
                    <FormControl
                        name={name}
                        type={type}
                        value={value}
                        onChange={onChange}
                        className={
                            'modal__text-input-field' +
                            (requiredFieldEmpty ? ' modal__text-input-field--error' : '')
                        }
                    />
                ) : (
                    <span className="modal__text-input-field"> {value} </span>
                )}
            </div>
        );
    }
}

interface CheckBoxInputProps extends InputFieldProps {
    checked?: boolean | string;
    checkCheckBox?: Function;
}

export class CheckBoxInput extends PureComponent<any> {
    render() {
        const { caption, description, name, value, required } = this.props;
        return (
            <div className="modal__field">
                <span className="modal__field-label">
                    {caption} {required ? '*' : null}
                </span>

                {description && (
                    <LabelHelpTip
                        title="Tip"
                        body={() => {
                            return <div> {description} </div>;
                        }}
                    />
                )}

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
                        <FACheckBox name={name} checked={value == 'true'} canEdit={false} onClick={null} />
                    )}
                </span>
            </div>
        );
    }
}

interface OptionInputProps extends InputFieldProps {
    options: {[key: string]: string; };
}

export class Option extends PureComponent<OptionInputProps> {
    render() {
        const { options, caption, required, description, canEdit, name, value, onChange } = this.props;
        return (
            <div className="modal__option-field">
                <span className="modal__field-label">
                    {caption} {required ? '*' : null}
                </span>

                {description && (
                    <LabelHelpTip
                        title="Tip"
                        body={() => {
                            return <div> {description} </div>;
                        }}
                    />
                )}

                {canEdit ? (
                    <div className="modal__option-input">
                        <FormControl
                            componentClass="select"
                            name={name}
                            onChange={onChange}
                            value={value}>
                            {options &&
                            Object.keys(options).map(item => (
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
    key: number;
}

export class FixedHtml extends PureComponent<FixedHtmlProps> {
    render() {
        return (
            <div className="modal__fixed-html-field">
                <span className="modal__field-label">{this.props.caption}</span>

                {/* HTML set is text-only information that lives on the server */}
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
    onFileRemoval: Function;
    requiredFieldEmpty?: boolean;
}

export class SmallFileUpload extends PureComponent<SmallFileInputProps> {
    render() {
        const { requiredFieldEmpty, description, canEdit, name, caption, required, value } = this.props;

        return (
            <div className="modal__compact-file-upload-field">
                <span className="modal__field-label">
                    {caption} {required ? '*' : null}
                </span>

                {description && (
                    <LabelHelpTip
                        title="Tip"
                        body={() => {
                            return <div> {description} </div>;
                        }}
                    />
                )}

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
    search: boolean;
    canEdit: boolean,
    modalType: AuthConfigProvider;
    emptyRequiredFields: String[];
    onChange: (event) => void;
    checkCheckBox: (string) => void;
    onFileChange: (attachment, logoType: string) => void;
    onFileRemoval: (name: string) => void;
}

export class DynamicFields extends PureComponent<DynamicFieldsProps> {
    render() {
        const { fields, search, emptyRequiredFields, canEdit, onChange, checkCheckBox, onFileChange, onFileRemoval } = this.props;
        let stopPoint = fields.length;
        for (let i = 0; i < fields.length; i++) {
            if ('dictateFieldVisibility' in fields[i]) {
                stopPoint = i + 1;
                break;
            }
        }
        const fieldsToCreate = search ? fields : fields.slice(0, stopPoint);

        const allFields = fieldsToCreate.map((field, index) => {
            const requiredFieldEmpty = (emptyRequiredFields.indexOf(field.name) !== -1);

            switch (field.type) {
                case 'input':
                    return (
                        <TextInput
                            key={index}
                            onChange={onChange}
                            value={this.props[field.name]}
                            type="text"
                            canEdit={canEdit}
                            requiredFieldEmpty={requiredFieldEmpty}
                            {...field}
                        />
                    );
                case 'checkbox':
                    return (
                        <CheckBoxInput
                            key={index}
                            checkCheckBox={checkCheckBox}
                            value={this.props[field.name]}
                            canEdit={canEdit}
                            {...field}
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
                            value={this.props[field.name]}
                            type="password"
                            canEdit={canEdit}
                            {...field}
                        />
                    );

                case 'pem':
                    return (
                        <SmallFileUpload
                            key={index}
                            onFileChange={onFileChange}
                            onFileRemoval={onFileRemoval}
                            value={this.props[field.name]}
                            index={index + 2} // There are two other FileAttachmentForms (from SSOFields) on modal
                            canEdit={canEdit}
                            requiredFieldEmpty={requiredFieldEmpty}
                            {...field}
                        />
                    );

                case 'options':
                    return (
                        <Option
                            key={index}
                            onChange={onChange}
                            value={this.props[field.name]}
                            options={field.options}
                            canEdit={canEdit}
                            {...field}
                        />
                    );

                case 'fixedHtml':
                    return <FixedHtml key={index} {...field} />;
                default:
                    return <div> Error: Invalid field type received. </div>;
            }
        });
        return (
            <>
                { fields && allFields }
            </>
        );
    };
}