/*
 * Copyright (c) 2018 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
import * as React from 'react'
import classNames from 'classnames'
import { Utils } from '@labkey/api'

interface Props {
    addContent?: React.ReactNode
    iconSpacer?: boolean
    label?: React.ReactNode
    onAddClick?: () => any
    showLabel?: boolean
}

export class FormSection extends React.Component<Props, any> {

    static defaultProps = {
        iconSpacer: true,
        showLabel: true
    };

    showLabel(): boolean {
        return this.props.showLabel && this.props.label !== undefined;
    }

    render() {
        const { label } = this.props;

        return (
            <>
                {this.showLabel() && (
                    <div className="row">
                        <div className="col-sm-12">
                            {Utils.isString(label) ? (
                                <label className="control-label text-left">
                                    <strong>{label}</strong>
                                </label>
                            ) : label}
                        </div>
                    </div>
                )}
                <div className="row">
                    <div className="col-sm-12">
                        <div className={classNames('wizard-row--container', {'wizard-row--spacer': this.props.iconSpacer})}>
                            {this.props.children}
                            {this.props.addContent && (
                                <div className="add-row--container" onClick={this.props.onAddClick}>
                                    {this.props.addContent}
                                </div>
                            )}
                        </div>
                    </div>
                </div>
            </>
        )
    }
}