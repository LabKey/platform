import * as React from "react";
import SimpleAuthRow from "./SimpleAuthRow";
import DraggableAuthRow from "./DraggableAuthRow";


import { DragDropContext, Draggable, Droppable } from "react-beautiful-dnd";


interface Props {
    className: string
    rowInfo: Array<any> // Temp until final shape of rowInfo decided
    onDragEnd: any
    handleChangeToPrimary: any
    handlePrimaryToggle: any
}
export default class DragAndDropPane extends React.Component<Props>{
    constructor(props){
        super(props);
        this.forTestingFunc = this.forTestingFunc.bind(this);
    }

    forTestingFunc(){
        console.log("ohboy");
        const configs = this.props.rowInfo;
        console.log("your props: ", configs);
        console.log("state: ", this.state)
    }

    render() {
        this.forTestingFunc();

        const primaryConfigsWithoutDatabase = this.props.rowInfo.slice(0, -1);
        const dataBaseConfig = this.props.rowInfo.slice(-1)[0];

        return(
            <div>
                <DragDropContext onDragEnd={this.props.onDragEnd}>
                    <Droppable droppableId="auth-config-droppable">
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
                                                <DraggableAuthRow
                                                    id={index.toString()}
                                                    rowId={item.id}
                                                    authName={item.name}
                                                    url={""}
                                                    enabled={item.enabled}
                                                    description={item.description}
                                                    handleChangeToPrimary={this.props.handleChangeToPrimary}
                                                    handlePrimaryToggle={this.props.handlePrimaryToggle}
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

                {/*{dataBaseConfig && <SimpleAuthRow*/}
                {/*        handle={null}*/}
                {/*        description={dataBaseConfig.description}*/}
                {/*        name={dataBaseConfig.name}*/}
                {/*        enabled={(dataBaseConfig.enabled) ? "Enabled" : "Disabled"}*/}
                {/*/>}*/}
            </div>
        )
    }
}