/*
 * Copyright (c) 2020-2020 LabKey Corporation
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

DROP PROCEDURE IF EXISTS umls.createIndexes;
DROP PROCEDURE IF EXISTS umls.dropIndexes;
GO

DROP TABLE IF EXISTS umls.MRCOC;
DROP TABLE IF EXISTS umls.MRCOLS;
DROP TABLE IF EXISTS umls.MRCONSO;
DROP TABLE IF EXISTS umls.MRCUI;
DROP TABLE IF EXISTS umls.MRCXT;
DROP TABLE IF EXISTS umls.MRDEF;
DROP TABLE IF EXISTS umls.MRDOC;
DROP TABLE IF EXISTS umls.MRFILES;
DROP TABLE IF EXISTS umls.MRHIER;
DROP TABLE IF EXISTS umls.MRHIST;
DROP TABLE IF EXISTS umls.MRMAP;
DROP TABLE IF EXISTS umls.MRRANK;
DROP TABLE IF EXISTS umls.MRREL;
DROP TABLE IF EXISTS umls.MRSAB;
DROP TABLE IF EXISTS umls.MRSAT;
DROP TABLE IF EXISTS umls.MRSMAP;
DROP TABLE IF EXISTS umls.MRSTY;
DROP TABLE IF EXISTS umls.MRXNS_ENG;
DROP TABLE IF EXISTS umls.MRXNW_ENG;
DROP TABLE IF EXISTS umls.MRAUI;
DROP TABLE IF EXISTS umls.MRXW;
DROP TABLE IF EXISTS umls.AMBIGSUI;
DROP TABLE IF EXISTS umls.AMBIGLUI;
DROP TABLE IF EXISTS umls.DELETEDCUI;
DROP TABLE IF EXISTS umls.DELETEDLUI;
DROP TABLE IF EXISTS umls.DELETEDSUI;
DROP TABLE IF EXISTS umls.MERGEDCUI;
DROP TABLE IF EXISTS umls.MERGEDLUI;
GO

DROP SCHEMA IF EXISTS umls;
GO