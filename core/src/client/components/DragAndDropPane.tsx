import React, { PureComponent } from 'react';

import { DragDropContext, Draggable, Droppable } from 'react-beautiful-dnd';

import AuthRow from './AuthRow';

interface Props {
    stateSection?: string;
    rowInfo?: AuthConfig[];
    primaryProviders?: Record<string, any>;
    secondaryProviders?: Record<string, any>;
    canEdit?: boolean;
    isDragDisabled?: boolean;
    actionFunctions?: { [key: string]: Function };
}

export default class DragAndDropPane extends PureComponent<Props> {
    constructor(props) {
        super(props);
    }

    render() {
        const { primaryProviders, secondaryProviders, stateSection } = this.props;
        const { onDragEnd, ...otherActionFunctions } = this.props.actionFunctions;
        const providers = primaryProviders ? primaryProviders : secondaryProviders;

        const DragAndDropAuthRows = this.props.rowInfo.map((item, index) => (
            <Draggable
                key={item.configuration}
                draggableId={item.configuration}
                index={index}
                isDragDisabled={this.props.isDragDisabled}>
                {provided => (
                    <div ref={provided.innerRef} {...provided.draggableProps} {...provided.dragHandleProps}>
                        <AuthRow
                            index={index.toString()}
                            {...item} // contains data of an individual auth config
                            {...otherActionFunctions} // contains authRow-level functions
                            canEdit={this.props.canEdit}
                            draggable={true}
                            modalType={providers && { ...providers[item.provider] }}
                            stateSection={stateSection}
                        />
                    </div>
                )}
            </Draggable>
        ));

        return (
            <DragDropContext onDragEnd={onDragEnd}>
                <Droppable droppableId={stateSection}>
                    {provided => (
                        <div ref={provided.innerRef} {...provided.droppableProps}>
                            {DragAndDropAuthRows}
                            {provided.placeholder}
                        </div>
                    )}
                </Droppable>
            </DragDropContext>
        );
    }
}
