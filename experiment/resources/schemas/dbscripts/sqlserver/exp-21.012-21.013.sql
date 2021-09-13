ALTER TABLE exp.Material ADD Status INT;
GO
ALTER TABLE exp.Material ADD CONSTRAINT FK_Material_Status FOREIGN KEY (Status) REFERENCES core.DataStates (RowId);
