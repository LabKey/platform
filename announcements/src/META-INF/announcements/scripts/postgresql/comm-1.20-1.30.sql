CREATE TABLE comm.Renderers
     (
     RowId SERIAL NOT NULL,
     Label VARCHAR(50) NOT NULL,
     Name VARCHAR(30) NOT NULL,
     CONSTRAINT PK_Renderers PRIMARY KEY (RowId)
     );

INSERT INTO comm.Renderers(Label, Name) VALUES ('Radeox Engine', 'Radeox');

CREATE TABLE comm.PageVersions
     (
     RowId SERIAL NOT NULL,
     PageEntityId ENTITYID NOT NULL,
     Created TIMESTAMP,
     CreatedBy USERID,
     Owner USERID,
     Version INT4 NOT NULL,
     RendererId INT4 NOT NULL,
     Title VARCHAR(255),
     Body TEXT,
     CONSTRAINT PK_PageVersions PRIMARY KEY (RowId),
     CONSTRAINT FK_PageVersions_Pages FOREIGN KEY (PageEntityId) REFERENCES comm.Pages(EntityId),
     CONSTRAINT FK_PageVersions_Renderer FOREIGN KEY (RendererId) REFERENCES comm.Renderers(RowId),
     CONSTRAINT UQ_PageVersions UNIQUE (PageEntityId, Version)
     );

INSERT INTO comm.PageVersions(PageEntityId, Title, Body, Created, CreatedBy, Owner, Version, RendererId)
     SELECT EntityId, Title, Body, Modified, ModifiedBy, Owner, 1, 1 FROM comm.Pages;

ALTER TABLE comm.Pages DROP COLUMN Title;
ALTER TABLE comm.Pages DROP COLUMN Body;
