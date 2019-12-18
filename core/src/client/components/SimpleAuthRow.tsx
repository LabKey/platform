import React, { PureComponent } from 'react';
import { Col } from 'react-bootstrap';

import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faTimesCircle } from '@fortawesome/free-solid-svg-icons';

// todo:
// remove the margin-left
// fix the classname-down issue

interface Props {
    handle?: JSX.Element;
    description: string | JSX.Element;
    url?: string | JSX.Element;
    name: string;
    enabled: string | JSX.Element;
    edit?: JSX.Element;
    modal?: boolean | JSX.Element;
    key?: any;
}
interface State {
    color: boolean;
}

export default class SimpleAuthRow extends PureComponent<any, State> {
    constructor(props) {
        super(props);
        this.state = {
            color: false,
        };
    }

    render() {
        return (
            <div style={{ paddingBottom: '20px' }}>
                <div
                    className="domain-field-row domain-row-border-default"
                    onMouseOver={() => {
                        this.setState({ color: true });
                    }}
                    onMouseOut={() => {
                        this.setState({ color: false });
                    }}>
                    <div className="domain-row-container row">
                        <div className="domain-row-handle">{this.props.handle}</div>

                        <div className="domain-row-main">
                            <Col xs={9} className="domain-row-base-fields">
                                <Col xs={4} className="down">
                                    {this.props.description}
                                </Col>
                                <Col xs={4} className="down">
                                    {this.props.details}
                                </Col>
                                <Col xs={1} className="down">
                                    {this.props.name}
                                </Col>
                            </Col>

                            <Col xs={1} />

                            <Col xs={2} className="domain-row-base-fields">
                                <Col xs={7}>{this.props.enabled}</Col>

                                <Col xs={1}>{this.props.delete}</Col>

                                <Col xs={3}>{this.props.editIcon}</Col>
                            </Col>

                            {this.props.modal}
                        </div>
                    </div>
                </div>
            </div>
        );
    }
}
