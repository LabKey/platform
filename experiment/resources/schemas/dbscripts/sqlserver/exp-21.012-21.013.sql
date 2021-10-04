ALTER TABLE exp.Material ADD SampleState INT;
GO
ALTER TABLE exp.Material ADD CONSTRAINT FK_Material_SampleState FOREIGN KEY (SampleState) REFERENCES core.DataStates (RowId);
