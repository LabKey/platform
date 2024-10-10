import React, { ChangeEvent, FC, memo, PureComponent, useCallback } from 'react';
import { FormControl } from 'react-bootstrap';
import { FileAttachmentForm, LabelHelpTip } from '@labkey/components';
import { Utils } from '@labkey/api';

import { AuthConfig, AuthConfigField, AuthConfigProvider, InputFieldProps } from './models';

interface TextInputProps extends InputFieldProps {
    requiredFieldEmpty?: boolean;
}

export const TextInput: FC<TextInputProps> = memo(props => {
    const { description, caption, required, canEdit, requiredFieldEmpty, name, onChange, type, value } = props;
    const onChange_ = useCallback(
        (event: ChangeEvent<HTMLInputElement>) => onChange(event.target.name, event.target.value),
        [onChange]
    );

    return (
        <div className="auth-config-input-row">
            <span className="auth-config-input-row__caption">
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
                    onChange={onChange_}
                    className={
                        'auth-config-input-row__input' +
                        (requiredFieldEmpty ? ' auth-config-input-row__input--error' : '')
                    }
                />
            ) : (
                <span className="auth-config-input-row__input"> {value} </span>
            )}
        </div>
    );
});

interface CheckBoxInputProps extends InputFieldProps {
    canEdit: boolean;
    value: boolean;
}

export const CheckBoxInput: FC<CheckBoxInputProps> = memo(props => {
    const { canEdit, caption, description, name, onChange, value, required } = props;
    const onChange_ = useCallback(
        (event: ChangeEvent<HTMLInputElement>) => {
            onChange(name, event.target.checked);
        },
        [name, onChange]
    );

    return (
        <div className="auth-config-input-row">
            <span className="auth-config-input-row__caption">
                {caption}
                {description && (
                    <LabelHelpTip title="Tip">
                        <div> {description} </div>
                    </LabelHelpTip>
                )}
                {required ? ' *' : null}
            </span>

            <span className="auth-config-input-row__input">
                <input checked={value} disabled={!canEdit} id={name} onChange={onChange_} type="checkbox" />
            </span>
        </div>
    );
});

interface SelectProps extends InputFieldProps {
    options: { [key: string]: string };
}

// TODO: This should use the SelectInput component from UI components
export const Select: FC<SelectProps> = props => {
    const { options, caption, required, description, canEdit, name, value, onChange } = props;
    const onChange_ = useCallback(
        (event: ChangeEvent<HTMLSelectElement>) => {
            onChange(name, event.target.value);
        },
        [name, onChange]
    );
    return (
        <div className="auth-config-input-row">
            <span className="auth-config-input-row__caption">
                {caption}
                {description && (
                    <LabelHelpTip title="Tip">
                        <div> {description} </div>
                    </LabelHelpTip>
                )}
                {required ? ' *' : null}
            </span>

            {canEdit && (
                <div className="auth-config-input-row__input">
                    <FormControl componentClass="select" name={name} onChange={onChange_} value={value}>
                        {Object.keys(options).map(item => (
                            <option value={item} key={item}>
                                {options[item]}
                            </option>
                        ))}
                    </FormControl>
                </div>
            )}

            {!canEdit && <span className="modal__fixed-html-text"> {value} </span>}
        </div>
    );
};

interface FixedHtmlProps {
    authConfig: AuthConfig;
    caption: string;
    description?: string;
    html?: string;
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
            <div className="auth-config-input-row">
                <span className="auth-config-input-row__caption">
                    {caption}
                    {description && (
                        <LabelHelpTip title="Tip">
                            <div> {description} </div>
                        </LabelHelpTip>
                    )}
                </span>

                {/* HTML set is text-only information that lives on the server */}
                <div className="auth-config-input-row__input">
                    <div dangerouslySetInnerHTML={{ __html: stringTemplatedHtml }} />
                </div>
            </div>
        );
    }
}

interface SectionProps {
    caption: string;
}

export class Section extends PureComponent<SectionProps> {
    render() {
        const { caption } = this.props;

        return (
            <div className="auth-config-section">
                {caption}
            </div>
        );
    }
}

interface SmallFileInputProps extends InputFieldProps {
    index: number;
    onFileChange: (attachment: File, name: string) => void;
    onFileRemoval: (name: string) => void;
    requiredFieldEmpty?: boolean;
}

export const SmallFileUpload: FC<SmallFileInputProps> = props => {
    const { canEdit, caption, description, index, name, required, requiredFieldEmpty, value } = props;
    const onFileChange = useCallback(attachment => props.onFileChange(attachment, name), [name, props.onFileChange]);
    const onFileRemoval = useCallback(() => props.onFileRemoval(name), [name, props.onFileRemoval]);

    return (
        <div className="auth-config-input-row file-upload-field">
            <span className="auth-config-input-row__caption">
                {caption}
                {description && (
                    <LabelHelpTip title="Tip">
                        <div> {description} </div>
                    </LabelHelpTip>
                )}
                {required ? ' *' : null}
            </span>

            {requiredFieldEmpty && <div className="modal__tiny-error--small-file-input"> This file is required </div>}

            {canEdit ? (
                <div className="auth-config-input-row__input">
                    <FileAttachmentForm
                        index={index}
                        showLabel={false}
                        allowMultiple={false}
                        allowDirectories={false}
                        showAcceptedFormats={false}
                        onFileChange={onFileChange}
                        onFileRemoval={onFileRemoval}
                        compact={true}
                        initialFileNames={value ? [''] : undefined}
                    />
                </div>
            ) : (
                value && (
                    <div className="modal__pem-input">
                        <span className="fa fa-file-alt attached-file--icon" />
                    </div>
                )
            )}
        </div>
    );
};

interface DynamicFieldsProps {
    authConfig: AuthConfig;
    canEdit: boolean;
    emptyRequiredFields: string[];
    fieldValues: any;
    fields: AuthConfigField[];
    modalType: AuthConfigProvider;
    onChange: (name: string, value: string | boolean) => void;
    onFileChange: (attachment: File, logoType: string) => void;
    onFileRemoval: (name: string) => void;
}

export const DynamicFields: FC<DynamicFieldsProps> = memo(props => {
    const { fields, emptyRequiredFields, canEdit, onChange, onFileChange, onFileRemoval, fieldValues, authConfig } =
        props;

    // If dictateFieldVisibility is set on a checkbox field, its value determines the visibility of all subsequent
    // fields until the next checkbox with a dictateFieldVisibility value, or a section field or the end of the form
    let on = true;
    const fieldsToCreate = fields.filter(field => {
        const returnVal = on;

        if ('dictateFieldVisibility' in field) {
            on = fieldValues[field['name']];
            return true;
        }

        if (field.type === 'section') {
            on = true;
            return true;
        }

        return returnVal;
    });

    const allFields = fieldsToCreate.map((field, index) => {
        const requiredFieldEmpty = emptyRequiredFields.indexOf(field.name) !== -1;
        const name = fieldValues[field.name];

        switch (field.type) {
            case 'input':
                return (
                    <TextInput
                        key={field.name}
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
                        key={field.name}
                        onChange={onChange}
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
                    return <></>;
                }

                return (
                    <TextInput
                        key={field.name}
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
                        key={field.name}
                        onFileChange={onFileChange}
                        onFileRemoval={onFileRemoval}
                        value={name}
                        index={index + 3} // There are two other FileAttachmentForms (from SSOFields) on modal
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

            case 'options':
                return (
                    <Select
                        key={field.name}
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
                        key={field.name}
                        caption={field.caption}
                        html={field.html}
                        description={field.description}
                        authConfig={authConfig}
                    />
                );

            case 'section':
                return (
                    <Section
                        key={field.caption}
                        caption={field.caption}
                    />
                );

            default:
                return <div> Error: Invalid field type received. </div>;
        }
    });

    return <>{allFields}</>;
});
