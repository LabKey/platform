import React, { PureComponent } from 'react';

import { DragDropContext, Draggable, Droppable } from "react-beautiful-dnd";
import AuthRow from "./AuthRow";

interface Props {
    stateSection?: string;
    rowInfo?: Array<AuthConfig>;
    primaryProviders?: Object;
    secondaryProviders?: Object;
    canEdit?: boolean;
    isDragDisabled?: boolean;
    actionFunctions?: { [key: string]: Function; }
}

export default class DragAndDropPane extends PureComponent<Props> {
    constructor(props){
        super(props);
    }

    render() {
        const {primaryProviders, secondaryProviders} = this.props;
        const {onDragEnd, ...otherActionFunctions} = this.props.actionFunctions;
        const providers = (primaryProviders) ? primaryProviders : secondaryProviders;

        // console.log("PROPS of DND", this.props);

        let DragAndDropAuthRows =
            this.props.rowInfo.map((item, index) => ( // make this into a own variable
                <Draggable key={item.configuration} draggableId={item.configuration} index={index} isDragDisabled={this.props.isDragDisabled}>
                    {(provided) => (
                        <div
                            ref={provided.innerRef}
                            {...provided.draggableProps}
                            {...provided.dragHandleProps}
                        >

                            <AuthRow
                                index={index.toString()}
                                {...item} // contains data of an individual auth config
                                {...otherActionFunctions} // contains authRow-level functions
                                canEdit = {this.props.canEdit}
                                draggable={true}
                                modalType={(providers) && {...providers[item.provider]}}
                                stateSection={this.props.stateSection}
                            />
                        </div>
                    )}
                </Draggable>
            ));

        return (
            <DragDropContext onDragEnd={onDragEnd}>
                <Droppable droppableId={this.props.stateSection}>
                    {(provided) => ( //consider doing a function render row tightly coupled in there
                        <div ref={provided.innerRef} {...provided.droppableProps}>
                            {DragAndDropAuthRows}
                            {provided.placeholder}
                        </div>
                    )}
                </Droppable>
            </DragDropContext>
        )
    }
}