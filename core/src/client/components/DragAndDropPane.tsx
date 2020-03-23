import React, { PureComponent } from 'react';

import { DragDropContext, Draggable, Droppable } from 'react-beautiful-dnd';

import AuthRow from './AuthRow';

interface Props {
    configType: string;
    authConfigs: AuthConfig[];
    providers: AuthConfigProvider[];
    canEdit: boolean;
    isDragDisabled: boolean;
    actions: Actions;
}

export default class DragAndDropPane extends PureComponent<Props> {
    render() {
        const { providers, configType, authConfigs } = this.props;
        const { onDragEnd, onDelete, toggleModalOpen, updateAuthRowsAfterSave } = this.props.actions;

        const DragAndDropAuthRows = authConfigs.map((authConfig, index) => (
            <Draggable
                key={authConfig.configuration}
                draggableId={authConfig.configuration}
                index={index}
                isDragDisabled={this.props.isDragDisabled}>
                {provided => (
                    <div ref={provided.innerRef} {...provided.draggableProps} {...provided.dragHandleProps}>
                        <AuthRow
                            index={index.toString()}
                            authConfig={authConfig}
                            canEdit={this.props.canEdit}
                            draggable={true}
                            modalType={providers ? providers[authConfig.provider] : null }
                            configType={configType}
                            onDelete={onDelete}
                            toggleModalOpen={toggleModalOpen}
                            updateAuthRowsAfterSave={updateAuthRowsAfterSave}
                        />
                    </div>
                )}
            </Draggable>
        ));

        return (
            <DragDropContext onDragEnd={onDragEnd}>
                <Droppable droppableId={configType}>
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
