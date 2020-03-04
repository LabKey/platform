import React, {PureComponent} from "react";
import {Button, Modal, FormControl} from "react-bootstrap";
import {List} from "immutable";
import {DomainField} from "@labkey/components";

interface AliasFieldProps
{
    domainFields: List<DomainField>
    showAlias?: boolean
    onHide: () => any
    onAdd: (any) => any
}

interface AliasFieldState
{
    show?: boolean
    selectedFieldName?: string
    selectedDomainFieldIndex?: number
    selectedDomainField?: DomainField
}

export class AliasField extends PureComponent<AliasFieldProps, AliasFieldState> {

    // pass fields as props
    constructor(props) {
        super(props);

        const { domainFields } = this.props;

        this.state = {
            show: this.props.showAlias,
            selectedDomainField: domainFields.get(0),
            selectedFieldName: domainFields.get(0).name
        }
    }

    handleOK = () => {
        const { domainFields, onAdd } = this.props;
        const { selectedDomainFieldIndex, selectedFieldName } = this.state;

        if (onAdd && selectedDomainFieldIndex) {
            const domainFieldChange = domainFields.get(selectedDomainFieldIndex)
                .merge({
                    name: 'Wrapped' + selectedFieldName,
                    dataType: undefined,
                    lockType: undefined
                });
            const newDomainField =  DomainField.create(domainFieldChange.toJS());
            onAdd(newDomainField);
        }

    };

    handleClose = () => {
        const { onHide } = this.props;

        onHide();
    };

    handleChange = (evt) => {

        this.setState( {
            selectedFieldName: evt.target.value,
            selectedDomainFieldIndex: evt.target.selectedIndex
        });
    };

    renderDisplayFieldOptions = () => {
        const { domainFields } = this.props;
        const { selectedFieldName } = this.state;
        return (
            <>
                <div>Field Options</div>
                <FormControl
                    componentClass="select"
                    onChange={this.handleChange}
                    value={selectedFieldName}
                >
                    {
                        domainFields.map((level, i) => (
                            <option key={i} value={level.name}>{level.label}</option>
                        ))
                    }
                </FormControl>
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
                    <Button onClick={this.handleClose} className='domain-adv-footer domain-adv-cancel-btn'>
                        Cancel
                    </Button>

                    <Button onClick={this.handleOK} className='domain-adv-footer domain-adv-apply-btn'>
                        OK
                    </Button>
                </Modal.Footer>
            </Modal>
        );
    }

}