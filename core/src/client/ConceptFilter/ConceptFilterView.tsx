import React, { FC, memo, useCallback, useEffect, useState } from 'react';
import { OntologyBrowserFilterPanel, } from '@labkey/components';

import './ConceptFilterView.scss';
import { NameAndLinkingOptions } from '@labkey/components/dist/internal/components/domainproperties/NameAndLinkingOptions';
import { ConceptModel } from '@labkey/components/dist/internal/components/ontology/models';
// import { ActionButton } from '@labkey/components/dist/internal/components/buttons/ActionButton';

export interface AppContext {
    onConceptSelect: () => void;
    ontologyId: string;
    selectedCode?: string;
}

interface Props {
    context: AppContext;
}

export const ConceptFilterView: FC<Props> = memo(props => {
    const {context} = props;
    const {onConceptSelect, ontologyId, selectedCode} = context;
    const [hidden, setHidden] = useState<boolean>(true);
    const [selectedConcept, setSelectedConcept] = useState<ConceptModel>();

    const clickHandler = useCallback(() => {
        setHidden(!hidden);
    },[hidden, setHidden]);

    return (
        <div className="concept-filter-view">
            <a onClick={clickHandler}>{hidden ? 'Find Concepts by tree' : 'Close Browser'}</a>
            {!hidden && <OntologyBrowserFilterPanel ontologyId={ontologyId} onConceptSelection={onConceptSelect} selectedConcept={selectedConcept}  /> }
        </div>
    );
});
