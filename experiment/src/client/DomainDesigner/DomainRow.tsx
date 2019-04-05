import * as React from "react";
import {
    Row,
    Col,
    FormGroup,
    FormControl,
    Checkbox, OverlayTrigger, TooltipProps, Tooltip
} from "react-bootstrap";
import {
    DOMAIN_FIELD_ADV,
    DOMAIN_FIELD_DETAILS,
    DOMAIN_FIELD_NAME,
    DOMAIN_FIELD_PREFIX,
    DOMAIN_FIELD_REQ,
    DOMAIN_FIELD_TYPE, DomainDesign,
    DomainField,
    PropDescTypes
} from "./models";
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { library } from '@fortawesome/fontawesome-svg-core'
import { faPencilAlt } from '@fortawesome/free-solid-svg-icons';

library.add(faPencilAlt);

const rowStyle = {
    border: '1px solid lightgray',
    margin: '0 0 20px 0px',
    padding: '13px 10px 5px 0px',
    boxShadow: 'inset 0 1px 3px 2px #F3F3F4'
};

const checkboxStyle = {
    // paddingLeft: '26%',
    paddingLeft: '25%',
    width: '12px',
    marginTop: 8,
    marginBottom: 0
};

const detailsStyle = {
    color: 'darkgray',
    marginTop: 5,
    marginLeft: 10
};

const pencilStyle = {
    marginLeft: '100%'
};

interface IDomainRowDisplay {
    field: DomainField,
    onChange: (any) => any
}

interface IDomainRow {
    name: string
}

export class DomainRow extends React.Component<IDomainRowDisplay, IDomainRow> {

    // const { field, onChange } = props;

    constructor(props: IDomainRowDisplay, state: IDomainRow) {
        super(props, state);

        this.state = {
            name: props.field.name,
        }

        this.generateToolTip = this.generateToolTip.bind(this);
        this.getDetails = this.getDetails.bind(this);
        this.getDataType = this.getDataType.bind(this);
        this.onFieldChange = this.onFieldChange.bind(this);
    }

    onFieldChange(e) {
        this.setState({ name: e.target.value })
    }

    generateToolTip(tooltip: string, id: number): React.ReactElement<TooltipProps> {
        return <Tooltip id={id.toString()}>{tooltip}</Tooltip>;
    }

    getDetails() {
        let details = '';

        // Hack for now just for display
        if (this.props.field.name === 'Key') {
            details += 'Primary Key, Locked';
        }

        if (this.props.field.newField) {
            if (details.length > 0)
                details += ', ';

            details += 'New Field';
        }

        if (this.props.field.updatedField && !this.props.field.newField) {
            if (details.length > 0)
                details += ', ';

            details += 'Updated';
        }

        return details;
    }

    getDataType() {
        const types = PropDescTypes.filter((value) => {

            // handle matching rangeURI and conceptURI
            if (value.rangeURI === this.props.field.rangeURI) {
                if (!this.props.field.lookupQuery &&
                    ((!value.conceptURI && !this.props.field.conceptURI) || (value.conceptURI === this.props.field.conceptURI))) {
                    return true;
                }
            }
            // handle selected lookup option
            else if (value.name === 'lookup' && this.props.field.lookupQuery && this.props.field.lookupQuery !== 'users') {
                return true;
            }
            // handle selected users option
            else if (value.name === 'users' && this.props.field.lookupQuery && this.props.field.lookupQuery === 'users') {
                return true;
            }

            return false;
        });

        // If found return name
        if (types.size > 0) {
            return types.get(0).name;
        }

        return null;
    }

    render() {
        const { field, onChange } = this.props;

        return (

            <Row style={rowStyle} key={DOMAIN_FIELD_PREFIX + "-" + field.propertyId}>
                <Col xs={3}>
                    <OverlayTrigger overlay={this.generateToolTip('Name', field.propertyId)} placement="top">
                        <FormControl key={DOMAIN_FIELD_PREFIX + DOMAIN_FIELD_NAME + "-" + field.propertyId}
                                     id={DOMAIN_FIELD_PREFIX + DOMAIN_FIELD_NAME + "-" + field.propertyId} type="text"
                                     value={this.state.name} onChange={this.onFieldChange} onBlur={onChange}/>
                    </OverlayTrigger>
                </Col>
                <Col xs={2}>
                    <OverlayTrigger overlay={this.generateToolTip('Data Type', field.propertyId)} placement="top">
                        <select key={DOMAIN_FIELD_PREFIX + DOMAIN_FIELD_TYPE + "-" + field.propertyId}
                                id={DOMAIN_FIELD_PREFIX + DOMAIN_FIELD_TYPE + "-" + field.propertyId}
                                className={'form-control'} onChange={onChange} value={this.getDataType()}>
                            {
                                PropDescTypes.map(function (type) {
                                    if (type.display)
                                    {
                                        return <option
                                            key={DOMAIN_FIELD_PREFIX + DOMAIN_FIELD_TYPE + 'option-' + type.name + '-' + field.propertyId}
                                            value={type.name}>{type.display}</option>
                                    }
                                    return ''
                                })
                            }
                        </select>
                    </OverlayTrigger>
                </Col>
                <Col xs={1}>

                    <div style={checkboxStyle}>
                        <OverlayTrigger overlay={this.generateToolTip('Required?', field.propertyId)} placement="top">
                            <Checkbox style={checkboxStyle}
                                      key={DOMAIN_FIELD_PREFIX + DOMAIN_FIELD_REQ + "-" + field.propertyId}
                                      id={DOMAIN_FIELD_PREFIX + DOMAIN_FIELD_REQ + "-" + field.propertyId}
                                      checked={field.required} onChange={onChange}/>
                        </OverlayTrigger>
                    </div>
                </Col>
                <Col xs={5}>
                <span id={DOMAIN_FIELD_PREFIX + DOMAIN_FIELD_DETAILS + "-" + field.propertyId}
                      key={DOMAIN_FIELD_PREFIX + DOMAIN_FIELD_DETAILS + "-" + field.propertyId} style={detailsStyle}>
                    {this.getDetails()}
                </span>
                </Col>
                <Col xs={1}>
                    <OverlayTrigger overlay={this.generateToolTip('Advanced Settings', field.propertyId)} placement="top">
                        <div style={pencilStyle} id={DOMAIN_FIELD_PREFIX + DOMAIN_FIELD_ADV + "-" + field.propertyId}
                             key={DOMAIN_FIELD_PREFIX + DOMAIN_FIELD_ADV + "-" + field.propertyId}>
                            <FontAwesomeIcon icon={faPencilAlt}/>
                        </div>
                    </OverlayTrigger>
                </Col>
            </Row>
        );
    }
};