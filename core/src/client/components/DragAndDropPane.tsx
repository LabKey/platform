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
        // console.log("ohboy");
        const configs = this.props.rowInfo;
        console.log("your props: ", this.props.primary["CAS"]);
        // console.log("state: ", this.state)
    };





    render() {
        // this.forTestingFunc();

        // console.log("PROPS ", this.props.rowInfo);

        let DragAndDropAuthRows =
            this.props.rowInfo.map((item, index) => ( // make this into a own variable
                <Draggable key={item.id} draggableId={item.id} index={index}>
                    {(provided) => (
                        <div
                            ref={provided.innerRef}
                            {...provided.draggableProps}
                            {...provided.dragHandleProps}
                        >
                            <EditableAuthRow
                                id={index.toString()}
                                rowId={item.id}
                                authName={item.name}
                                url={""}
                                enabled={item.enabled}
                                deleteURL={item.deleteUrl}
                                description={item.description}
                                handlePrimaryToggle={this.props.handlePrimaryToggle}
                                stateSection={this.props.stateSection}
                                deleteAction={this.props.deleteAction}
                                modalType={(this.props.primary) && {...this.props.primary[item.name]}}
                                // modalType={""}
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
