ALTER TABLE Assay.Well ADD SampleId INTEGER NULL;
ALTER TABLE Assay.Well ADD CONSTRAINT FK_SampleId_ExpMaterial FOREIGN KEY (SampleId) REFERENCES exp.material (RowId);