import * as React from "react";
import SimpleAuthRow from "./SimpleAuthRow";
import EditableAuthRow from "./EditableAuthRow";


import { DragDropContext, Draggable, Droppable } from "react-beautiful-dnd";
import {FontAwesomeIcon} from "@fortawesome/react-fontawesome";
import {faGripVertical} from "@fortawesome/free-solid-svg-icons";


interface Props {
    className: string
    rowInfo: Array<any> // Temp until final shape of rowInfo decided
    onDragEnd: any
    handleChangeToPrimary: any
    handlePrimaryToggle: any
    stateSection: string
}
export default class DragAndDropPane extends React.Component<Props>{
    constructor(props){
        super(props);
        this.forTestingFunc = this.forTestingFunc.bind(this);
    }

    forTestingFunc(){
        // console.log("ohboy");
        // const configs = this.props.rowInfo;
        // console.log("your props: ", configs);
        // console.log("state: ", this.state)
    }

    render() {
        this.forTestingFunc();

        const primaryConfigsWithoutDatabase = this.props.rowInfo.slice(0, -1);
        const dataBaseConfig = this.props.rowInfo.slice(-1)[0];

        return(
            <div>
                <DragDropContext onDragEnd={this.props.onDragEnd}>
                    <Droppable droppableId={this.props.stateSection}>
                        {(provided) => (
                            <div ref={provided.innerRef} {...provided.droppableProps}>
                                {this.props.rowInfo.map((item, index) => (
                                    <Draggable key={item.id} draggableId={item.id} index={index} >
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
                                                    description={item.description}
                                                    handleChangeToPrimary={this.props.handleChangeToPrimary}
                                                    handlePrimaryToggle={this.props.handlePrimaryToggle}
                                                    stateSection={this.props.stateSection}
                                                />
                                            </div>
                                        )}
                                    </Draggable>
                                ))}
                                {provided.placeholder}
                            </div>
                        )}
                    </Droppable>
                </DragDropContext>
            </div>
        )
    }
}


interface HandleProps {
    highlight: boolean
}
interface HandleState { }

class LightupHandle extends React.Component<HandleProps, HandleState>{
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