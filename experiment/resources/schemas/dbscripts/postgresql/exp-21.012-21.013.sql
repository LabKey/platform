ALTER TABLE exp.Material ADD COLUMN Status INT;

ALTER TABLE exp.Material ADD CONSTRAINT FK_Material_Status FOREIGN KEY (Status) REFERENCES core.DataStates (RowId);

