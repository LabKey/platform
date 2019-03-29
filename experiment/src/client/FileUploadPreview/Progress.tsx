/*
 * Copyright (c) 2018 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
import * as React from 'react'
import { Modal, ProgressBar } from 'react-bootstrap'

interface Props {
    delay?: number
    estimate?: number
    modal?: boolean
    title?: React.ReactNode
    toggle: boolean
    updateIncrement?: number
}

interface State {
    duration?: number
    percent?: number
    show?: boolean
}

export class Progress extends React.Component<Props, State> {

    static defaultProps = {
        delay: 350,
        estimate: 500,
        modal: false,
        updateIncrement: 50
    };

    delayTimer: number;
    timer: number;

    constructor(props: Props) {
        super(props);

        this.end = this.end.bind(this);
        this.start = this.start.bind(this);

        this.state = {
            duration: 0,
            percent: 0,
            show: false
        };
    }

    componentWillUnmount() {
        this.end(true);
    }

    componentWillReceiveProps(nextProps: Props) {
        if (!this.props.toggle && nextProps.toggle) {
            if (this.props.delay) {
                this.delayTimer = setTimeout(() => {
                    this.cycle(true);
                    this.start();
                }, this.props.delay);
            }
            else {
                this.start();
            }
        }
        else if (this.props.toggle && !nextProps.toggle) {
            this.end();
        }
    }

    cycle(fromDelay?: boolean) {
        const newDuration = this.state.duration + (fromDelay ? this.props.delay : this.props.updateIncrement);
        let newPercent = Math.ceil((newDuration / this.props.estimate) * 100);

        this.setState({
            duration: newDuration,
            percent: newPercent > 100 ? 100 : newPercent,
            show: true
        });
    }

    end(fromUnmount?: boolean) {
        clearTimeout(this.delayTimer);
        clearTimeout(this.timer);

        if (fromUnmount !== true) {
            this.setState({
                percent: 0,
                show: false
            });
        }
    }

    start() {
        clearTimeout(this.timer);
        this.timer = setTimeout(() => {
            this.timer = null;
            this.cycle();
            this.start();
        }, this.props.updateIncrement);
    }

    render() {
        const { children, modal, title } = this.props;
        const { show } = this.state;
        let element = null;
        const indicator = show && (
            <ProgressBar active now={this.state.percent} bsStyle={this.state.percent === 100 ? "success" : undefined}/>
        );

        if (modal) {
            element = (
                <Modal bsSize="large" show={show}>
                    {title && (
                        <Modal.Header>
                            <Modal.Title>{title}</Modal.Title>
                        </Modal.Header>
                    )}
                    <Modal.Body>
                        {children}
                        {indicator}
                    </Modal.Body>
                </Modal>
            )
        }
        else if (show) {
            element = indicator;
        }

        return element;
    }
}