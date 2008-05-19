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
;

ALTER TABLE mousemod.BreedingPair
    DROP CONSTRAINT FK_BreedingPair_ModelId
;

ALTER TABLE mousemod.Litter
   DROP CONSTRAINT FK_Litter_BreedingPairId
;

ALTER TABLE mousemod.Cage
   DROP CONSTRAINT FK_Cage_ModelId ,
   DROP CONSTRAINT UQ_CageName
;

ALTER TABLE mousemod.Mouse
    DROP CONSTRAINT UQ_Mouse_No,
    DROP CONSTRAINT FK_Mouse_CageId ,
    DROP CONSTRAINT FK_Mouse_LitterId ,
    DROP CONSTRAINT FK_Mouse_MouseModel
;

ALTER TABLE mousemod.Slide
    DROP CONSTRAINT FK_Slide_SampleLSID ,
    DROP CONSTRAINT FK_Slide_StainId
;


ALTER TABLE mousemod.MouseTask
    DROP CONSTRAINT FK_MouseTask_MouseEntityId ,
    DROP CONSTRAINT FK_MouseTask_TaskTypeId
;

ALTER TABLE mousemod.Location
    DROP CONSTRAINT UQ_Location_One_Per_Slot
;

ALTER TABLE mousemod.Mouse
    DROP CONSTRAINT CK_Mouse_Sex
;

ALTER TABLE mousemod.MouseModel
    DROP CONSTRAINT FK_MouseModel_TargetGeneId,
    DROP CONSTRAINT FK_MouseModel_MouseStrainId,
    DROP CONSTRAINT FK_MouseModel_TreatmentId,
    DROP CONSTRAINT FK_MouseModel_IrradDoseId,
    DROP CONSTRAINT FK_MouseModel_GenotypeId
;

ALTER TABLE mousemod.Sample
    DROP CONSTRAINT FK_Sample_SampleTypeId,
    DROP CONSTRAINT FK_Sample_OrganismId
;

DROP VIEW mousemod.MouseView
;

DROP VIEW mousemod.MouseSample
;

DROP VIEW mousemod.MouseSlide
 ;

DROP FUNCTION mousemod.populateLookups(text)
;


 DROP TABLE mousemod.Genotype
;

 DROP TABLE mousemod.IrradDose
;

 DROP TABLE mousemod.MouseModel
;

 DROP TABLE mousemod.BreedingPair
;

 DROP TABLE mousemod.Litter
;

 DROP TABLE mousemod.Cage
;

 DROP TABLE mousemod.Mouse
;

 DROP TABLE mousemod.MouseStrain
;

 DROP TABLE mousemod.TargetGene
;

 DROP TABLE mousemod.SamplePreparation
;

 DROP TABLE mousemod.Sample
;

 DROP TABLE mousemod.SampleType
;

 DROP TABLE mousemod.Treatment
;

 DROP TABLE mousemod.Stain
;

 DROP TABLE mousemod.Slide
;

 DROP TABLE mousemod.TaskType
;

 DROP TABLE mousemod.MouseTask
;

 DROP TABLE mousemod.Location
;

DELETE FROM exp.MaterialSource WHERE LSID='urn:lsid:proteomics.fhcrc.org:MaterialSource:BDIMouseModels'
;