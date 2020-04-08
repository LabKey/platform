import React, {PureComponent} from "react";
import {Button, Modal, Row, Col} from "react-bootstrap";
import {List} from "immutable";
import {DomainField, SelectInput} from "@labkey/components";

interface AliasFieldModalProps
{
    domainFields: List<DomainField>
    showAlias?: boolean
    onHide: () => any
    onAdd: (any) => any
}

interface AliasFieldModalState
{
    show?: boolean
    selectedField?: DomainField
}

export class AliasFieldModal extends PureComponent<AliasFieldModalProps, AliasFieldModalState> {

    // pass fields as props
    constructor(props) {
        super(props);

        this.state = {
            show: this.props.showAlias,
            selectedField: this.props.domainFields.get(0)
        }
    }

    handleOK = () => {
        const { onAdd } = this.props;
        const { selectedField } = this.state;

        const selectedFieldName = selectedField.name;

        if (onAdd) {
            const domainFieldChange = selectedField
                .merge({
                    name: 'Wrapped' + selectedFieldName,
                    dataType: undefined,
                    lockType: undefined,
                    wrappedColumnName: selectedFieldName,
                    propertyId: undefined,
                    label: 'Wrapped' + selectedFieldName
                });
            const newDomainField =  DomainField.create(domainFieldChange.toJS());
            onAdd(newDomainField);
        }

    };

    handleClose = () => {
        const { onHide } = this.props;

        onHide();
    };

    handleChange = (name, formValue, selected) => {
        this.setState( () =>({
            selectedField: selected
        }));
    };

    renderDisplayFieldOptions = () => {
        const { domainFields } = this.props;

        return (
            <>
                <Row>
                    <Col xs={3}>
                        Field Options
                    </Col>
                    <Col xs={9}>
                        <SelectInput
                            onChange={this.handleChange}
                            value={domainFields.get(0)}
                            options={domainFields.toArray()}
                            inputClass={'col-xs-12'}
                            valueKey="name"
                            labelKey="label"
                            formsy={false}
                            multiple={false}
                            required={false}
                            clearable={false}
                        />
                    </Col>
                </Row>
            </>
        )

    };

    render() {
        const {show} = this.state;

        return (
            <Modal
                show={show}
                onHide={this.handleClose}>
                <Modal.Header closeButton>
                    <Modal.Title>{'Choose a field to wrap'}</Modal.Title>
                </Modal.Header>
                <Modal.Body>
                    <div className='domain-modal'>
                        {this.renderDisplayFieldOptions()}
                    </div>
                </Modal.Body>
                <Modal.Footer>
                    <div className={'domain-designer-buttons'}>
                        <Button onClick={this.handleClose} className='domain-adv-footer domain-adv-cancel-btn'>
                            Cancel
                        </Button>

                        <Button onClick={this.handleOK} className='domain-adv-footer domain-adv-apply-btn' bsStyle={'primary'}>
                            OK
                        </Button>
                    </div>
                </Modal.Footer>
            </Modal>
        );
    }

}