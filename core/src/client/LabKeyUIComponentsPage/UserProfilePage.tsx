/*
 * Copyright (c) 2019 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
import React from 'react'
import { Map, fromJS } from 'immutable'
import { Alert, getUserProperties, LoadingSpinner, Section, UserProfile } from "@labkey/components";

interface State {
    userProperties: Map<string, any>
    message: string
}

export class UserProfilePage extends React.Component<any, State> {

    constructor(props: any) {
        super(props);

        this.state = {
            userProperties: undefined,
            message: undefined
        };
    }

    componentDidMount() {
        getUserProperties(this.props.user.id).then((response) => {
            this.setState(() => ({
                userProperties: fromJS(response.props)
            }));
        });
    }

    onSuccess = (result: {}, shouldReload: boolean) => {
        if (shouldReload) {
            window.location.reload();
        }
        else {
            this.setMessage('Successfully updated your user details.');
            window.setTimeout(() => {
                this.setMessage();
            }, 5000);
        }
    };

    onCancel = () => {
        console.log('Cancel click on UserProfile.');
        this.setMessage();
    };

    setMessage(message?: string) {
        this.setState(() => ({message}));
    }

    render() {
        const { user } = this.props;
        const { userProperties, message } = this.state;

        if (!userProperties) {
            return <LoadingSpinner/>
        }

        return (
            <Section>
                {message && <Alert bsStyle={'success'}>{message}</Alert>}
                <UserProfile
                    user={user}
                    userProperties={userProperties}
                    onSuccess={this.onSuccess}
                    onCancel={this.onCancel}
                />
            </Section>
        )
    }
}

