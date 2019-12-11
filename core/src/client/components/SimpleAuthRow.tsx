import * as React from 'react'
import { Col } from 'react-bootstrap'

// todo:
// remove the margin-left
// fix the classname-down issue

interface Props {
    handle?: JSX.Element
    description: String | JSX.Element
    url?: String | JSX.Element
    name: String
    enabled: String | JSX.Element
    edit?: JSX.Element
    modal?: boolean | JSX.Element
    key?: any
}

interface State {
    color: boolean
}

export default class SimpleAuthRow extends React.PureComponent<Props, State>{
    constructor(props){
        super(props);
        this.state = {
            color: false
        };
    }

    render(){
        return(
            <div style={{paddingBottom:"20px"}}>
                <div
                    className="domain-field-row domain-row-border-default"
                    onMouseOver={() => {this.setState({color: true})}}
                    onMouseOut={() => {this.setState({color: false})}}
                >
                    <div className="domain-row-container row">
                        <div className="domain-row-handle">
                            {this.props.handle}
                        </div>

                        <div className="domain-row-main row-flex">

                            <Col xs={9} className='domain-row-base-fields'>
                                <Col xs={4} className="down">
                                    {this.props.description}
                                </Col>
                                <Col xs={4} className="down">
                                    {this.props.url}
                                </Col>
                                <Col xs={1} className="down">
                                    {this.props.name}
                                </Col>
                            </Col>

                            <Col xs={1}/>

                            <Col xs={2} className={"domain-row-base-fields"}>
                                <Col xs={8}>
                                    {this.props.enabled}
                                </Col>

                                <Col xs={4} >
                                    {this.props.edit}
                                </Col>
                            </Col>

                            {this.props.modal}
                        </div>
                    </div>
                </div>
            </div>
        )}
}