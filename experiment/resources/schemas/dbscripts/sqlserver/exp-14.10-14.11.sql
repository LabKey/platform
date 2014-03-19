
ALTER TABLE exp.PropertyDescriptor ADD KeyVariable BIT NOT NULL DEFAULT '0';
ALTER TABLE exp.PropertyDescriptor ADD DefaultScale NVARCHAR(40) NOT NULL DEFAULT 'LINEAR';