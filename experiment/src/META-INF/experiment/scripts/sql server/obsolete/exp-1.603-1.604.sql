ALTER TABLE exp.DataInput ADD PropertyId INT NULL;
ALTER TABLE exp.DataInput ADD CONSTRAINT FK_DataInput_PropertyDescriptor FOREIGN KEY(PropertyId) REFERENCES exp.PropertyDescriptor(PropertyId);

ALTER TABLE exp.MaterialInput ADD PropertyId INT NULL;
ALTER TABLE exp.MaterialInput ADD CONSTRAINT FK_MaterialInput_PropertyDescriptor FOREIGN KEY(PropertyId) REFERENCES exp.PropertyDescriptor(PropertyId);