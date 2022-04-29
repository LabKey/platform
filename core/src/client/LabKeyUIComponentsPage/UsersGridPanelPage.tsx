/*
 * Copyright (c) 2019 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
import React from 'react';
import { List } from 'immutable';
import { getServerContext, PermissionTypes, Utils } from '@labkey/api';
import {
    Alert,
    LoadingSpinner,
    InjectedPermissionsPage,
    SecurityPolicy,
    UsersGridPanel,
    fetchContainerSecurityPolicy,
    queryGridInvalidate,
    SCHEMAS,
    User,
    withPermissionsPage,
} from '@labkey/components';

interface State {
    policy: SecurityPolicy
    loading: boolean
    error: string
    message: string
}

class UsersGridPanelPageImpl extends React.PureComponent<InjectedPermissionsPage, State> {

    constructor(props: InjectedPermissionsPage) {
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
        const { rolesByUniqueName } = this.props;
        const { loading, error, message, policy } = this.state;
        const user = new User({
            ...getServerContext().user,
            permissionsList: getServerContext().user.isRootAdmin ? List.of(PermissionTypes.UserManagement) : List<string>(),
        });

        if (loading) {
            return <LoadingSpinner/>
        }
        else if (error) {
            return <Alert>{error}</Alert>
        }

        return (
            <>
                <Alert bsStyle={'info'}>NOTE: if you have the proper permissions, this will actually update users for this server.</Alert>
                {message && <Alert bsStyle={'success'}>{message}</Alert>}
                <UsersGridPanel
                    user={user}
                    showDetailsPanel={user.isRootAdmin}
                    onCreateComplete={this.onSuccess}
                    onUsersStateChangeComplete={this.onSuccess}
                    policy={policy}
                    rolesByUniqueName={rolesByUniqueName}
                />
            </>
        )
    }
}

export const UsersGridPanelPage = withPermissionsPage<{}>(UsersGridPanelPageImpl);

