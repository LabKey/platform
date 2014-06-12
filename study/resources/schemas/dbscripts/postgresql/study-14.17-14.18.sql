
ALTER TABLE study.StudyDesignAssays RENAME COLUMN Target TO Type;
ALTER TABLE study.StudyDesignAssays RENAME COLUMN Methodology TO Platform;

ALTER TABLE study.StudyDesignAssays ADD AlternateName VARCHAR(200);
ALTER TABLE study.StudyDesignAssays ADD Lab VARCHAR(200);
ALTER TABLE study.StudyDesignAssays ADD LabPI VARCHAR(200);