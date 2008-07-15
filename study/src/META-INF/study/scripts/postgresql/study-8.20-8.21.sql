ALTER TABLE study.Study
    ADD COLUMN SecurityType VARCHAR(32);

UPDATE study.Study
  SET SecurityType = 'ADVANCED'
  WHERE
  StudySecurity = TRUE;

UPDATE study.Study
  SET SecurityType = 'EDITABLE_DATASETS'
  WHERE
  StudySecurity = FALSE AND
  DatasetRowsEditable = TRUE;

UPDATE study.Study
  SET SecurityType = 'BASIC'
  WHERE
  StudySecurity = FALSE AND
  DatasetRowsEditable = FALSE;

ALTER TABLE study.Study
  DROP COLUMN StudySecurity;

ALTER TABLE study.Study
  DROP COLUMN DatasetRowsEditable;

ALTER TABLE study.Study
  ALTER COLUMN SecurityType SET NOT NULL;
