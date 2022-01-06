ALTER TABLE exp.Material ADD COLUMN SampleState INT;

ALTER TABLE exp.Material ADD CONSTRAINT FK_Material_SampleState FOREIGN KEY (SampleState) REFERENCES core.DataStates (RowId);

