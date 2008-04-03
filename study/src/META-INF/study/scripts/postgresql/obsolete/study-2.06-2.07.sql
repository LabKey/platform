ALTER TABLE study.StudyDesign
    ADD Active boolean NOT NULL DEFAULT FALSE;

UPDATE study.StudyDesign SET Active=true WHERE StudyEntityId IS NOT NULL;

ALTER TABLE study.StudyDesign
    DROP StudyEntityId,
    ADD SourceContainer ENTITYID;

UPDATE study.StudyDesign SET SourceContainer = Container;