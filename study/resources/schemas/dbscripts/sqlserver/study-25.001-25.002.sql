-- "publish" is the only supported type now, so migrate any "ancillary" studies to "publish"
UPDATE study.StudySnapshot SET Type = 'publish';
