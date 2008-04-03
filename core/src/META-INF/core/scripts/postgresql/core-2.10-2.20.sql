CREATE TABLE core.MappedDirectories
(
   EntityId ENTITYID NOT NULL,
   Container ENTITYID NOT NULL,
   Relative BOOLEAN NOT NULL,
   Name VARCHAR(80),
   Path VARCHAR(255),
   CONSTRAINT PK_MappedDirecctories PRIMARY KEY (EntityId),
   CONSTRAINT UQ_MappedDirectories UNIQUE (Container,Name)

   );
