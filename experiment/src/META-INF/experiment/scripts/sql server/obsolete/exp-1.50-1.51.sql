  alter table exp.PropertyDomain ADD Required BIT NOT NULL,
  CONSTRAINT DF_Required DEFAULT 0 FOR Required
  go
