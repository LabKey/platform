ALTER TABLE core.qcstate RENAME TO dataStates;

ALTER TABLE core.dataStates ADD COLUMN stateType VARCHAR(20);
