import React, { FC, useCallback, useState } from 'react';
import { List } from 'immutable';
import { DomainField, Modal, SelectInput } from '@labkey/components';

interface AliasFieldModalProps {
    domainFields: List<DomainField>;
    onHide: () => any;
    onAdd: (any) => any;
}

export const AliasFieldModal: FC<AliasFieldModalProps> = ({ domainFields, onAdd, onHide }) => {
    const [selectedField, setSelectedField] = useState<DomainField>(domainFields.get(0));
    const onConfirm = useCallback(() => {
        const selectedFieldName = selectedField.name;
        const domainFieldChange = selectedField.merge({
            name: 'Wrapped' + selectedFieldName,
            dataType: undefined,
            lockType: undefined,
            wrappedColumnName: selectedFieldName,
            propertyId: undefined,
            label: 'Wrapped' + selectedFieldName,
            lockExistingField: false,
            rangeURI: selectedField.rangeURI,
        });
        const newDomainField = DomainField.create(domainFieldChange.toJS());
        onAdd(newDomainField);
    }, [onAdd, selectedField]);
    const onChange = useCallback((_, __, domainField) => setSelectedField(domainField), []);
    return (
        <Modal
            className="domain-modal"
            confirmClass="btn-primary"
            confirmText="OK"
            onCancel={onHide}
            onConfirm={onConfirm}
            title="Choose a field to wrap"
        >
            <div className="row">
                <div className="col-xs-3">
                    <label>
                        Field Options
                    </label>
                </div>

                <div className="col-xs-9">
                    <SelectInput
                        id="domain-field-selector"
                        onChange={onChange}
                        value={domainFields.get(0)}
                        options={domainFields.toArray()}
                        inputClass="col-xs-12"
                        valueKey="name"
                        labelKey="label"
                        clearable={false}
                    />
                </div>
            </div>
        </Modal>
    );
}
AliasFieldModal.displayName = 'AliasFieldModal';