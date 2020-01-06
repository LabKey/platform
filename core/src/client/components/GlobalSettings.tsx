import React, { PureComponent } from 'react';

import { Panel } from 'react-bootstrap';
import { Ajax, ActionURL } from '@labkey/api';

import FACheckBox from './FACheckBox';

let ROW_TEXTS = [
    { id: 'SelfRegistration', text: 'Allow self sign up' },
    { id: 'SelfServiceEmailChanges', text: 'Allow users to edit their own email addresses' },
    { id: 'AutoCreateAccounts', text: 'Auto-create authenticated users' },
];

// Todo:
// Interface
// use Immutable in handleCheckbox
// move render const into a const folder?
// might be a better way to do the rowTexts thing you're doing
// hook up default email domain
interface Props {
    checkGlobalAuthBox: any;
    // autoCreateAuthUsers: boolean
    selfSignUp: boolean;
    userEmailEdit: boolean;
}
interface State {
    SelfRegistration: boolean;
    SelfServiceEmailChanges: boolean;
    AutoCreateAccounts: boolean;
}

export default class GlobalSettings extends PureComponent<any, any> {
    constructor(props) {
        super(props);
        this.state = {
            ...this.props,
        };
    }

    componentDidMount() {
        if (this.props.hideAutoCreateAccounts){
            this.setState({AutoCreateAccounts : null});
        }
    }

    render() {
        let rowTexts = ROW_TEXTS;
        if (this.props.authCount == 1){
            rowTexts = ROW_TEXTS.slice(0,-1);
        }

        const rowTextComponents = rowTexts.map(text => (
            <div className="bottom-margin" key={text.id}>
                <FACheckBox
                    key={text.id}
                    checked={this.props[text.id]}
                    onClick={
                        this.props.canEdit
                            ? () => { this.props.checkGlobalAuthBox(text.id) }
                            : () => {}
                    }
                />

                <span style={{ marginLeft: '15px' }}>{text.text}</span>
            </div>
        ));

        return (
            <Panel>
                <Panel.Heading>
                    <span className="boldText">Global Authentication Configurations</span>
                </Panel.Heading>

                <Panel.Body>
                    <span className="boldText"> Sign up and email options</span>

                    <br />
                    <br />

                    {rowTextComponents}

                </Panel.Body>
            </Panel>
        );
    }
}
