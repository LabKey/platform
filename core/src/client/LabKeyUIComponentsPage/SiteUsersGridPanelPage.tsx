/*
 * Copyright (c) 2019 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
import React from 'react'
import { getServerContext, Utils } from '@labkey/api'
import {
    Alert,
    LoadingSpinner,
    PermissionsPageContextProvider,
    PermissionsProviderProps,
    SecurityPolicy,
    SiteUsersGridPanel,
    fetchContainerSecurityPolicy,
    queryGridInvalidate,
    SCHEMAS
} from "@labkey/components";

type Props = PermissionsProviderProps;

interface State {
    policy: SecurityPolicy
    loading: boolean
    error: string
    message: string
}

class SiteUsersGridPanelPageImpl extends React.PureComponent<Props, State> {

    constructor(props: Props) {
        super(props);

        this.state = {
            policy: undefined,
            loading: true,
            error: undefined,
            message: undefined
        };
    }

    componentDidMount() {
        fetchContainerSecurityPolicy(getServerContext().container.id, this.props.principalsById)
            .then((policy) => {
                this.setState(() => ({loading: false, policy}));
            })
            .catch((response) => {
                this.setState(() => ({loading: false, error: response.exception}));
            });
    }

    onSuccess = (response: any) => {
        queryGridInvalidate(SCHEMAS.CORE_TABLES.USERS);

        let message;
        if (response.users) {
            let countNew = 0;
            response.users.forEach((user) => {
                if (user.isNew) {
                    countNew = countNew + 1;
                }
            });

            message = 'Successfully created ' + Utils.pluralBasic(countNew, 'user') + '.';
            if (response.users.length > countNew) {
                message = message + ' ' + Utils.pluralBasic(response.users.length - countNew, 'user') + ' already existed.'
            }
        }
        else if (response.delete) {
            message = 'Successfully deleted ' + Utils.pluralBasic(response.userIds.length, 'user') + '.';
        }
        else {
            message = 'Successfully ' + (response.activate ? 'reactivated' : 'deactivated') + ' ' + Utils.pluralBasic(response.userIds.length, 'user') + '.';
        }

        this.setState(() => ({message}));
        window.setTimeout(() => {
            this.setState(() => ({message: undefined}));
        }, 5000);
    };

    render() {
        const { loading, error, message, policy } = this.state;

        if (loading) {
            return <LoadingSpinner/>
        }
        else if (error) {
            return <Alert>{error}</Alert>
        }

        return (
            <>
                <Alert bsStyle={'info'}>NOTE: if you have the proper permissions, this will actually update site users for this server.</Alert>
                {message && <Alert bsStyle={'success'}>{message}</Alert>}
                <SiteUsersGridPanel
                    onCreateComplete={this.onSuccess}
                    onUsersStateChangeComplete={this.onSuccess}
                    policy={policy}
                    rolesByUniqueName={this.props.rolesByUniqueName}
                />
            </>
        )
    }
}

export const SiteUsersGridPanelPage = PermissionsPageContextProvider<Props>(SiteUsersGridPanelPageImpl);

