import React, { FC, useState, useCallback } from 'react';

import {OntologyBrowserPanel} from "@labkey/components";

export const OntologyBrowserPage: FC = () => {
    const [ontology, setOntology] = useState<string>();
    const [ontologyInput, setOntologyInput] = useState<string>();
    const onOntologyChange = useCallback((evt) => { setOntologyInput(evt.target.value); }, [setOntologyInput]);
    const onGo = useCallback(() => { setOntology(ontologyInput); }, [setOntology, ontologyInput]);

    return (
        <>
            {!ontology && (
                <div style={{paddingBottom: '20px'}}>
                    Ontology: <input type="text" name="ontologyField" onChange={onOntologyChange}/>
                    <span className="labkey-button" onClick={onGo}>Go</span>
                </div>
            )}
            {ontology && <OntologyBrowserPanel ontologyId={ontology} />}
        </>
    )
};

