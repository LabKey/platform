import React, { FC } from 'react';

import './CreatePipelineTrigger.scss';

interface Field {
    // TODO
}

interface FormSchema {
    fields: Field[];
}

export interface Props {
    detailsFormSchema: FormSchema;
    taskFormSchemas: Record<string, FormSchema>;
    data: any; // TODO: properly type this
}

export const CreatePipelineTrigger: FC<Props> = ({ data, detailsFormSchema, taskFormSchemas }) => {
    // TODO
    return (
        <div className="create-pipeline-trigger">
            Create Pipeline Trigger Component.
        </div>
    );
};
