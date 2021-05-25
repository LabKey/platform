import React, { FC, memo, useCallback, useEffect, useState } from 'react';

import './ConceptInputPanel.scss'
import { ConceptPicker } from '@labkey/components';
import { ConceptModel } from '@labkey/components/dist/internal/components/ontology/models';

export interface AppContext {
    ontologyId: string;
    fieldValue?: string;
    fieldName: string;
    fieldLabel: string;
}

interface  Props {
    context: AppContext;
}

export const ConceptInputPanel: FC<Props> = memo((props: Props) => {
    const {fieldValue = '', fieldName } = props.context;
    const [conceptCode, setConceptCode] = useState<string>(fieldValue ?? '');
    const [concept, setConcept] = useState<ConceptModel>();

    const conceptSelectionHandler = useCallback((concept: ConceptModel) => {
        setConcept(concept);
        setConceptCode(concept.code);
    }, [setConcept]);

    const onChangeHandler = useCallback((event) => {
        setConceptCode(event.target.value);
    }, [setConceptCode]);

    return (
        <div className="concept-input-container">
            <input className="concept-input-textbox" type="text" name={`quf_${fieldName}`} value={conceptCode} onChange={onChangeHandler} />
            <ConceptPicker {...props.context} fieldValue={conceptCode} onConceptSelection={conceptSelectionHandler} />
        </div>
    );
});
