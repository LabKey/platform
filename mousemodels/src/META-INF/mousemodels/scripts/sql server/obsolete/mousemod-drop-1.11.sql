/*
 * Copyright (c) 2005-2008 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
ALTER TABLE mousemod.MouseModel
    DROP CONSTRAINT UQ_Model_Name
GO

ALTER TABLE mousemod.BreedingPair
    DROP CONSTRAINT FK_BreedingPair_ModelId
GO

ALTER TABLE mousemod.Litter
   DROP CONSTRAINT FK_Litter_BreedingPairId
GO

ALTER TABLE mousemod.Cage
   DROP CONSTRAINT FK_Cage_ModelId ,
  UQ_CageName
GO

ALTER TABLE mousemod.Mouse
    DROP CONSTRAINT UQ_Mouse_No,
FK_Mouse_CageId ,
FK_Mouse_LitterId ,
FK_Mouse_MouseModel
GO

ALTER TABLE mousemod.Slide
    DROP CONSTRAINT FK_Slide_SampleLSID ,
FK_Slide_StainId
GO


ALTER TABLE mousemod.MouseTask
    DROP CONSTRAINT FK_MouseTask_MouseEntityId ,
FK_MouseTask_TaskTypeId
GO

ALTER TABLE mousemod.Location
    DROP CONSTRAINT UQ_Location_One_Per_Slot
GO

ALTER TABLE mousemod.Mouse
    DROP CONSTRAINT CK_Mouse_Sex
GO

ALTER TABLE mousemod.MouseModel
    DROP CONSTRAINT FK_MouseModel_TargetGeneId,
FK_MouseModel_MouseStrainId,
FK_MouseModel_TreatmentId,
FK_MouseModel_IrradDoseId,
FK_MouseModel_GenotypeId
GO

ALTER TABLE mousemod.Sample
    DROP CONSTRAINT FK_Sample_SampleTypeId,
FK_Sample_OrganismId
GO

DROP VIEW mousemod.MouseView
GO

DROP VIEW mousemod.MouseSample
GO

DROP VIEW mousemod.MouseSlide
 GO

DROP PROCEDURE mousemod.populateLookups
GO


 DROP TABLE mousemod.Genotype
GO

 DROP TABLE mousemod.IrradDose
GO

 DROP TABLE mousemod.MouseModel
GO

 DROP TABLE mousemod.BreedingPair
GO

 DROP TABLE mousemod.Litter
GO

 DROP TABLE mousemod.Cage
GO

 DROP TABLE mousemod.Mouse
GO

 DROP TABLE mousemod.MouseStrain
GO

 DROP TABLE mousemod.TargetGene
GO

 DROP TABLE mousemod.SamplePreparation
GO

 DROP TABLE mousemod.Sample
GO

 DROP TABLE mousemod.SampleType
GO

 DROP TABLE mousemod.Treatment
GO

 DROP TABLE mousemod.Stain
GO

 DROP TABLE mousemod.Slide
GO

 DROP TABLE mousemod.TaskType
GO

 DROP TABLE mousemod.MouseTask
GO

 DROP TABLE mousemod.Location
GO

DELETE FROM exp.MaterialSource WHERE LSID='urn:lsid:proteomics.fhcrc.org:MaterialSource:BDIMouseModels'
GO

DELETE FROM core.Containers WHERE name='_mouseLookups'
GO

EXEC sp_droprole 'mousemod'
GO