ALTER TABLE core.qcstate RENAME TO DataStates;

ALTER TABLE core.DataStates ADD COLUMN StateType VARCHAR(20);
