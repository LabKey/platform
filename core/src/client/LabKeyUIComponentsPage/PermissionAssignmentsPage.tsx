/*
 * Copyright (c) 2019 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
import React from 'react'
import {
    fetchContainerSecurityPolicy,
    Alert,
    LoadingSpinner,
    PermissionAssignments,
    PermissionsPageContextProvider,
    PermissionsProviderProps,
    SecurityPolicy
} from "@labkey/components";
import { getServerContext } from "@labkey/api";

type Props = PermissionsProviderProps;

interface State {
    policy: SecurityPolicy
    loading: boolean
    error: string
    message: string
}

class PermissionAssignmentsPageImpl extends React.PureComponent<Props, State> {

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
        this.loadSecurityPolicy();
    }

    loadSecurityPolicy() {
        fetchContainerSecurityPolicy(getServerContext().container.id, this.props.principalsById)
            .then((policy) => {
                this.setState(() => ({loading: false, policy}));
            })
            .catch((response) => {
                this.setState(() => ({loading: false, error: response.exception}));
            });
    }

    onChange = (policy: SecurityPolicy) => {
        this.setState(() => ({policy}));
    };

    onSuccess = () => {
        this.setState(() => ({message: 'Successfully updated permissions roles and assignments.'}));
        window.setTimeout(() => {
            this.setState(() => ({message: undefined}));
        }, 5000);

        this.loadSecurityPolicy();
    };

    render() {
        const { loading, error, message } = this.state;

        if (loading) {
            return <LoadingSpinner/>
        }
        else if (error) {
            return <Alert>{error}</Alert>
        }

        return (
            <>
                <Alert bsStyle={'info'}>NOTE: if you have the proper permissions, this will actually update permission role assignments for this container.</Alert>
                {message && <Alert bsStyle={'success'}>{message}</Alert>}
                <PermissionAssignments
                    {...this.props}
                    {...this.state}
                    containerId={getServerContext().container.id}
                    onChange={this.onChange}
                    onSuccess={this.onSuccess}
                />
            </>
        )
    }
}

export const PermissionAssignmentsPage = PermissionsPageContextProvider<Props>(PermissionAssignmentsPageImpl);

