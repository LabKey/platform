import React, { PureComponent } from 'react';
import SimpleAuthRow from "./SimpleAuthRow";
import EditableAuthRow from "./EditableAuthRow";

import { DragDropContext, Draggable, Droppable } from "react-beautiful-dnd";
import {FontAwesomeIcon} from "@fortawesome/react-fontawesome";
import {faGripVertical} from "@fortawesome/free-solid-svg-icons";

interface Props {
    className: string
    rowInfo: Array<any> // Temp until final shape of rowInfo decided
    onDragEnd: any
    handlePrimaryToggle: any
    stateSection: string
}

export default class DragAndDropPane extends PureComponent<any> {
    constructor(props){
        super(props);
    }

    forTestingFunc = () => {
        const configs = this.props.rowInfo;
        console.log("your props: ", this.props.primaryProviders["CAS"]);
        // console.log("state: ", this.state)
    };

    render() {
        // this.forTestingFunc();
        const {primaryProviders, secondaryProviders} = this.props;
        const providers = (primaryProviders) ? primaryProviders : secondaryProviders;
        // console.log("PROPS ", this.props.rowInfo);

        let DragAndDropAuthRows =
            this.props.rowInfo.map((item, index) => ( // make this into a own variable
                <Draggable key={item.configuration} draggableId={item.configuration} index={index} isDragDisabled={this.props.isDragDisabled}>
                    {(provided) => (
                        <div
                            ref={provided.innerRef}
                            {...provided.draggableProps}
                            {...provided.dragHandleProps}
                        >
                            <EditableAuthRow
                                index={index.toString()}
                                {...item}
                                modalType={(providers) && {...providers[item.provider]}}
                                stateSection={this.props.stateSection}
                                deleteAction={this.props.deleteAction}
                                updateAuthRowsAfterSave={this.props.updateAuthRowsAfterSave}
                                toggleSomeModalOpen={this.props.toggleSomeModalOpen}
                            />
                        </div>
                    )}
                </Draggable>
            ));

        return (
            <DragDropContext onDragEnd={this.props.onDragEnd}>
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


interface HandleProps {
    highlight: boolean
}
interface HandleState { }

class LightupHandle extends PureComponent<HandleProps, HandleState> {
    render(){
        const HIGHLIGHT_BLUE = '#2980B9';
        const NOT_HIGHLIGHT_GRAY = '#999999';

        return(
            <div>
                <FontAwesomeIcon size='lg' color={(this.props.highlight) ? HIGHLIGHT_BLUE : NOT_HIGHLIGHT_GRAY} icon={faGripVertical}/>
            </div>
        )
    }
}
