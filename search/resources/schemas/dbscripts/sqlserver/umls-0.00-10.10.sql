/*
 * Copyright (c) 2009-2010 LabKey Corporation
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

/* umls-0.00-10.10.sql */

EXEC sp_addapprole 'umls', 'password'
GO


CREATE TABLE umls.MRCOC (
    CUI1	char(8) NOT NULL,
    AUI1	nvarchar(9) NOT NULL,
    CUI2	char(8),
    AUI2	nvarchar(9),
    SAB	nvarchar(20) NOT NULL,
    COT	nvarchar(3) NOT NULL,
    COF	integer,
    COA	nvarchar(300),
    CVF	integer
)
go

CREATE TABLE umls.MRCOLS (
    COL	nvarchar(20),
    DES	nvarchar(200),
    REF	nvarchar(20),
    MIN	integer,
    AV	numeric(5,2),
    MAX	integer,
    FIL	nvarchar(50),
    DTY	nvarchar(20)
)

go
CREATE TABLE umls.MRCONSO (
    CUI	char(8) NOT NULL,
    LAT	char(3) NOT NULL,
    TS	char(1) NOT NULL,
    LUI	nvarchar(10) NOT NULL,
    STT	nvarchar(3) NOT NULL,
    SUI	nvarchar(10) NOT NULL,
    ISPREF	char(1) NOT NULL,
    AUI	nvarchar(9) NOT NULL,
    SAUI	nvarchar(50),
    SCUI	nvarchar(50),
    SDUI	nvarchar(50),
    SAB	nvarchar(20) NOT NULL,
    TTY	nvarchar(20) NOT NULL,
    CODE	nvarchar(50) NOT NULL,
    STR	nvarchar(3000) NOT NULL,
    SRL	integer NOT NULL,
    SUPPRESS	char(1) NOT NULL,
    CVF	integer
)
go

CREATE TABLE umls.MRCUI (
    CUI1	char(8) NOT NULL,
    VER	nvarchar(10) NOT NULL,
    REL	nvarchar(4) NOT NULL,
    RELA	nvarchar(100),
    MAPREASON	nvarchar(4000),
    CUI2	char(8),
    MAPIN	char(1)
)
go

CREATE TABLE umls.MRCXT (
    CUI	char(8),
    SUI	nvarchar(10),
    AUI	nvarchar(9),
    SAB	nvarchar(20),
    CODE	nvarchar(50),
    CXN	integer,
    CXL	char(3),
    RANK	integer,
    CXS	nvarchar(3000),
    CUI2	char(8),
    AUI2	nvarchar(9),
    HCD	nvarchar(50),
    RELA	nvarchar(100),
    XC	nvarchar(1),
    CVF	integer
)
go

CREATE TABLE umls.MRDEF (
    CUI	char(8) NOT NULL,
    AUI	nvarchar(9) NOT NULL,
    ATUI	nvarchar(11) NOT NULL,
    SATUI	nvarchar(50),
    SAB	nvarchar(20) NOT NULL,
    DEF	NTEXT NOT NULL,
    SUPPRESS	char(1) NOT NULL,
    CVF	integer
)
go

CREATE TABLE umls.MRDOC (
    DOCKEY	nvarchar(50) NOT NULL,
    VALUE	nvarchar(200),
    TYPE	nvarchar(50) NOT NULL,
    EXPL	nvarchar(1000)
);


CREATE TABLE umls.MRFILES (
    FIL	nvarchar(50),
    DES	nvarchar(200),
    FMT	nvarchar(300),
    CLS	integer,
    RWS	integer,
    BTS	integer
)
go

CREATE TABLE umls.MRHIER (
    CUI	char(8) NOT NULL,
    AUI	nvarchar(9) NOT NULL,
    CXN	integer NOT NULL,
    PAUI	nvarchar(10),
    SAB	nvarchar(20) NOT NULL,
    RELA	nvarchar(100),
    PTR	nvarchar(1000),
    HCD	nvarchar(50),
    CVF	integer
)
go

CREATE TABLE umls.MRHIST (
    CUI	char(8),
    SOURCEUI	nvarchar(50),
    SAB	nvarchar(20),
    SVER	nvarchar(20),
    CHANGETYPE	nvarchar(1000),
    CHANGEKEY	nvarchar(1000),
    CHANGEVAL	nvarchar(1000),
    REASON	nvarchar(1000),
    CVF	integer
)
go

CREATE TABLE umls.MRMAP (
    MAPSETCUI	char(8) NOT NULL,
    MAPSETSAB	nvarchar(20) NOT NULL,
    MAPSUBSETID	nvarchar(10),
    MAPRANK	integer,
    MAPID	nvarchar(50) NOT NULL,
    MAPSID	nvarchar(50),
    FROMID	nvarchar(50) NOT NULL,
    FROMSID	nvarchar(50),
    FROMEXPR	nvarchar(4000) NOT NULL,
    FROMTYPE	nvarchar(50) NOT NULL,
    FROMRULE	nvarchar(4000),
    FROMRES	nvarchar(4000),
    REL	nvarchar(4) NOT NULL,
    RELA	nvarchar(100),
    TOID	nvarchar(50),
    TOSID	nvarchar(50),
    TOEXPR	nvarchar(4000),
    TOTYPE	nvarchar(50),
    TORULE	nvarchar(4000),
    TORES	nvarchar(4000),
    MAPRULE	nvarchar(4000),
    MAPRES	nvarchar(4000),
    MAPTYPE	nvarchar(50),
    MAPATN	nvarchar(20),
    MAPATV	nvarchar(4000),
    CVF	integer
)
go

CREATE TABLE umls.MRRANK (
    RANK	integer NOT NULL,
    SAB	nvarchar(20) NOT NULL,
    TTY	nvarchar(20) NOT NULL,
    SUPPRESS	char(1) NOT NULL
)
go

CREATE TABLE umls.MRREL (
    CUI1	char(8) NOT NULL,
    AUI1	nvarchar(9),
    STYPE1	nvarchar(50) NOT NULL,
    REL	nvarchar(4) NOT NULL,
    CUI2	char(8) NOT NULL,
    AUI2	nvarchar(9),
    STYPE2	nvarchar(50) NOT NULL,
    RELA	nvarchar(100),
    RUI	nvarchar(10) NOT NULL,
    SRUI	nvarchar(50),
    SAB	nvarchar(20) NOT NULL,
    SL	nvarchar(20) NOT NULL,
    RG	nvarchar(10),
    DIR	nvarchar(1),
    SUPPRESS	char(1) NOT NULL,
    CVF	integer
)
go

CREATE TABLE umls.MRSAB (
    VCUI	char(8),
    RCUI	char(8) NOT NULL,
    VSAB	nvarchar(20) NOT NULL,
    RSAB	nvarchar(20) NOT NULL,
    SON	nvarchar(3000) NOT NULL,
    SF	nvarchar(20) NOT NULL,
    SVER	nvarchar(20),
    VSTART	char(8),
    VEND	char(8),
    IMETA	nvarchar(10) NOT NULL,
    RMETA	nvarchar(10),
    SLC	nvarchar(1000),
    SCC	nvarchar(1000),
    SRL	integer NOT NULL,
    TFR	integer,
    CFR	integer,
    CXTY	nvarchar(50),
    TTYL	nvarchar(300),
    ATNL	nvarchar(1000),
    LAT	char(3),
    CENC	nvarchar(20) NOT NULL,
    CURVER	char(1) NOT NULL,
    SABIN	char(1) NOT NULL,
    SSN	nvarchar(3000) NOT NULL,
    SCIT	nvarchar(4000) NOT NULL
)
go

CREATE TABLE umls.MRSAT (
    CUI	char(8) NOT NULL,
    LUI	nvarchar(10),
    SUI	nvarchar(10),
    METAUI	nvarchar(50),
    STYPE	nvarchar(50) NOT NULL,
    CODE	nvarchar(50),
    ATUI	nvarchar(11) NOT NULL,
    SATUI	nvarchar(50),
    ATN	nvarchar(50) NOT NULL,
    SAB	nvarchar(20) NOT NULL,
    ATV	nvarchar(4000),
    SUPPRESS	char(1) NOT NULL,
    CVF	integer
)
go

CREATE TABLE umls.MRSMAP (
    MAPSETCUI	char(8) NOT NULL,
    MAPSETSAB	nvarchar(20) NOT NULL,
    MAPID	nvarchar(50) NOT NULL,
    MAPSID	nvarchar(50),
    FROMEXPR	nvarchar(4000) NOT NULL,
    FROMTYPE	nvarchar(50) NOT NULL,
    REL	nvarchar(4) NOT NULL,
    RELA	nvarchar(100),
    TOEXPR	nvarchar(4000),
    TOTYPE	nvarchar(50),
    CVF	integer
)
go

CREATE TABLE umls.MRSTY (
    CUI	char(8) NOT NULL,
    TUI	char(4) NOT NULL,
    STN	nvarchar(100) NOT NULL,
    STY	nvarchar(50) NOT NULL,
    ATUI	nvarchar(11) NOT NULL,
    CVF	integer
)
go

CREATE TABLE umls.MRXNS_ENG (
    LAT	char(3) NOT NULL,
    NSTR	nvarchar(3000) NOT NULL,
    CUI	char(8) NOT NULL,
    LUI	nvarchar(10) NOT NULL,
    SUI	nvarchar(10) NOT NULL
)
go

CREATE TABLE umls.MRXNW_ENG (
    LAT	char(3) NOT NULL,
    NWD	nvarchar(100) NOT NULL,
    CUI	char(8) NOT NULL,
    LUI	nvarchar(10) NOT NULL,
    SUI	nvarchar(10) NOT NULL
)
go

CREATE TABLE umls.MRAUI (
    AUI1	nvarchar(9) NOT NULL,
    CUI1	char(8) NOT NULL,
    VER	nvarchar(10) NOT NULL,
    REL	nvarchar(4),
    RELA	nvarchar(100),
    MAPREASON	nvarchar(4000) NOT NULL,
    AUI2	nvarchar(9) NOT NULL,
    CUI2	char(8) NOT NULL,
    MAPIN	char(1) NOT NULL
)
go

CREATE TABLE umls.MRXW (
    LAT	char(3) NOT NULL,
    WD	nvarchar(200) NOT NULL,
    CUI	char(8) NOT NULL,
    LUI	nvarchar(10) NOT NULL,
    SUI	nvarchar(10) NOT NULL
);


CREATE TABLE umls.AMBIGSUI (
    SUI	nvarchar(10) NOT NULL,
    CUI	char(8) NOT NULL
)
go

CREATE TABLE umls.AMBIGLUI (
    LUI	nvarchar(10) NOT NULL,
    CUI	char(8) NOT NULL
)
go

CREATE TABLE umls.DELETEDCUI (
    PCUI	char(8) NOT NULL,
    PSTR	nvarchar(3000) NOT NULL
)
go

CREATE TABLE umls.DELETEDLUI (
    PLUI	nvarchar(10) NOT NULL,
    PSTR	nvarchar(3000) NOT NULL
)
go

CREATE TABLE umls.DELETEDSUI (
    PSUI	nvarchar(10) NOT NULL,
    LAT	char(3) NOT NULL,
    PSTR	nvarchar(3000) NOT NULL
)
go

CREATE TABLE umls.MERGEDCUI (
    PCUI	char(8) NOT NULL,
    CUI	char(8) NOT NULL
)
go

CREATE TABLE umls.MERGEDLUI (
    PLUI	nvarchar(10),
    LUI	nvarchar(10)
)
go


CREATE PROCEDURE umls.createIndexes AS
BEGIN
  CREATE INDEX X_MRCOC_CUI1 ON umls.MRCOC(CUI1);
  CREATE INDEX X_MRCOC_AUI1 ON umls.MRCOC(AUI1);
  CREATE INDEX X_MRCOC_CUI2 ON umls.MRCOC(CUI2);
  CREATE INDEX X_MRCOC_AUI2 ON umls.MRCOC(AUI2);
  CREATE INDEX X_MRCOC_SAB ON umls.MRCOC(SAB);
  CREATE INDEX X_MRCONSO_CUI ON umls.MRCONSO(CUI);
  ALTER TABLE umls.MRCONSO ADD CONSTRAINT X_MRCONSO_PK PRIMARY KEY (AUI);
  CREATE INDEX X_MRCONSO_SUI ON umls.MRCONSO(SUI);
  CREATE INDEX X_MRCONSO_LUI ON umls.MRCONSO(LUI);
  CREATE INDEX X_MRCONSO_CODE ON umls.MRCONSO(CODE);
  CREATE INDEX X_MRCONSO_SAB_TTY ON umls.MRCONSO(SAB,TTY);
  CREATE INDEX X_MRCONSO_SCUI ON umls.MRCONSO(SCUI);
  CREATE INDEX X_MRCONSO_SDUI ON umls.MRCONSO(SDUI);
--  CREATE INDEX X_MRCONSO_STR ON umls.MRCONSO(STR);
  CREATE INDEX X_MRCXT_CUI ON umls.MRCXT(CUI);
  CREATE INDEX X_MRCXT_AUI ON umls.MRCXT(AUI);
  CREATE INDEX X_MRCXT_SAB ON umls.MRCXT(SAB);
  CREATE INDEX X_MRDEF_CUI ON umls.MRDEF(CUI);
  CREATE INDEX X_MRDEF_AUI ON umls.MRDEF(AUI);
  ALTER TABLE umls.MRDEF ADD CONSTRAINT X_MRDEF_PK PRIMARY KEY (ATUI);
  CREATE INDEX X_MRDEF_SAB ON umls.MRDEF(SAB);
  CREATE INDEX X_MRHIER_CUI ON umls.MRHIER(CUI);
  CREATE INDEX X_MRHIER_AUI ON umls.MRHIER(AUI);
  CREATE INDEX X_MRHIER_SAB ON umls.MRHIER(SAB);
--  CREATE INDEX X_MRHIER_PTR ON umls.MRHIER(PTR);
  CREATE INDEX X_MRHIER_PAUI ON umls.MRHIER(PAUI);
  CREATE INDEX X_MRHIST_CUI ON umls.MRHIST(CUI);
  CREATE INDEX X_MRHIST_SOURCEUI ON umls.MRHIST(SOURCEUI);
  CREATE INDEX X_MRHIST_SAB ON umls.MRHIST(SAB);
  ALTER TABLE umls.MRRANK ADD CONSTRAINT X_MRRANK_PK PRIMARY KEY (SAB,TTY);
  CREATE INDEX X_MRREL_CUI1 ON umls.MRREL(CUI1);
  CREATE INDEX X_MRREL_AUI1 ON umls.MRREL(AUI1);
  CREATE INDEX X_MRREL_CUI2 ON umls.MRREL(CUI2);
  CREATE INDEX X_MRREL_AUI2 ON umls.MRREL(AUI2);
  ALTER TABLE umls.MRREL ADD CONSTRAINT X_MRREL_PK PRIMARY KEY (RUI);
  CREATE INDEX X_MRREL_SAB ON umls.MRREL(SAB);
  ALTER TABLE umls.MRSAB ADD CONSTRAINT X_MRSAB_PK PRIMARY KEY (VSAB);
  CREATE INDEX X_MRSAB_RSAB ON umls.MRSAB(RSAB);
  CREATE INDEX X_MRSAT_CUI ON umls.MRSAT(CUI);
  CREATE INDEX X_MRSAT_METAUI ON umls.MRSAT(METAUI);
  ALTER TABLE umls.MRSAT ADD CONSTRAINT X_MRSAT_PK PRIMARY KEY (ATUI);
  CREATE INDEX X_MRSAT_SAB ON umls.MRSAT(SAB);
  CREATE INDEX X_MRSAT_ATN ON umls.MRSAT(ATN);
  CREATE INDEX X_MRSTY_CUI ON umls.MRSTY(CUI);
  ALTER TABLE umls.MRSTY ADD CONSTRAINT X_MRSTY_PK PRIMARY KEY (ATUI);
  CREATE INDEX X_MRSTY_STY ON umls.MRSTY(STY);
--  CREATE INDEX X_MRXNS_ENG_NSTR ON umls.MRXNS_ENG(NSTR);
  CREATE INDEX X_MRXNW_ENG_NWD ON umls.MRXNW_ENG(NWD);
  CREATE INDEX X_MRXW_WD ON umls.MRXW(WD);
  CREATE INDEX X_AMBIGSUI_SUI ON umls.AMBIGSUI(SUI);
  CREATE INDEX X_AMBIGLUI_LUI ON umls.AMBIGLUI(LUI);
  CREATE INDEX X_MRAUI_CUI2 ON umls.MRAUI(CUI2);
  CREATE INDEX X_MRCUI_CUI2 ON umls.MRCUI(CUI2);
  CREATE INDEX X_MRMAP_MAPSETCUI ON umls.MRMAP(MAPSETCUI);
END
go

-- DROP PROCEDURE umls.dropIndexes
CREATE PROCEDURE umls.dropIndexes AS
BEGIN
  DROP INDEX X_MRCOC_CUI1 ON umls.MRCOC
  DROP INDEX X_MRCOC_AUI1 ON umls.MRCOC
  DROP INDEX X_MRCOC_CUI2 ON umls.MRCOC
  DROP INDEX X_MRCOC_AUI2 ON umls.MRCOC
  DROP INDEX X_MRCOC_SAB ON umls.MRCOC
  DROP INDEX X_MRCONSO_CUI ON umls.MRCONSO
  ALTER TABLE umls.MRCONSO DROP CONSTRAINT X_MRCONSO_PK
  DROP INDEX X_MRCONSO_SUI ON umls.MRCONSO
  DROP INDEX X_MRCONSO_LUI ON umls.MRCONSO
  DROP INDEX X_MRCONSO_CODE ON umls.MRCONSO
  DROP INDEX X_MRCONSO_SAB_TTY ON umls.MRCONSO
  DROP INDEX X_MRCONSO_SCUI ON umls.MRCONSO
  DROP INDEX X_MRCONSO_SDUI ON umls.MRCONSO
--  DROP INDEX X_MRCONSO_STR ON umls.MRCONSO
  DROP INDEX X_MRCXT_CUI ON umls.MRCXT
  DROP INDEX X_MRCXT_AUI ON umls.MRCXT
  DROP INDEX X_MRCXT_SAB ON umls.MRCXT
  DROP INDEX X_MRDEF_CUI ON umls.MRDEF
  DROP INDEX X_MRDEF_AUI ON umls.MRDEF
  ALTER TABLE umls.MRDEF DROP CONSTRAINT X_MRDEF_PK
  DROP INDEX X_MRDEF_SAB ON umls.MRDEF
  DROP INDEX X_MRHIER_CUI ON umls.MRHIER
  DROP INDEX X_MRHIER_AUI ON umls.MRHIER
  DROP INDEX X_MRHIER_SAB ON umls.MRHIER
--  DROP INDEX X_MRHIER_PTR ON umls.MRHIER
  DROP INDEX X_MRHIER_PAUI ON umls.MRHIER
  DROP INDEX X_MRHIST_CUI ON umls.MRHIST
  DROP INDEX X_MRHIST_SOURCEUI ON umls.MRHIST
  DROP INDEX X_MRHIST_SAB ON umls.MRHIST
  ALTER TABLE umls.MRRANK DROP CONSTRAINT X_MRRANK_PK
  DROP INDEX X_MRREL_CUI1 ON umls.MRREL
  DROP INDEX X_MRREL_AUI1 ON umls.MRREL
  DROP INDEX X_MRREL_CUI2 ON umls.MRREL
  DROP INDEX X_MRREL_AUI2 ON umls.MRREL
  ALTER TABLE umls.MRREL DROP CONSTRAINT X_MRREL_PK
  DROP INDEX X_MRREL_SAB ON umls.MRREL
  ALTER TABLE umls.MRSAB DROP CONSTRAINT X_MRSAB_PK
  DROP INDEX X_MRSAB_RSAB ON umls.MRSAB
  DROP INDEX X_MRSAT_CUI ON umls.MRSAT
  DROP INDEX X_MRSAT_METAUI ON umls.MRSAT
  ALTER TABLE umls.MRSAT DROP CONSTRAINT X_MRSAT_PK
  DROP INDEX X_MRSAT_SAB ON umls.MRSAT
  DROP INDEX X_MRSAT_ATN ON umls.MRSAT
  DROP INDEX X_MRSTY_CUI ON umls.MRSTY
  ALTER TABLE umls.MRSTY DROP CONSTRAINT X_MRSTY_PK
  DROP INDEX X_MRSTY_STY ON umls.MRSTY
--  DROP INDEX X_MRXNS_ENG_NSTR ON umls.MRXNS_ENG
  DROP INDEX X_MRXNW_ENG_NWD ON umls.MRXNW_ENG
  DROP INDEX X_MRXW_WD ON umls.MRXW
  DROP INDEX X_AMBIGSUI_SUI ON umls.AMBIGSUI
  DROP INDEX X_AMBIGLUI_LUI ON umls.AMBIGLUI
  DROP INDEX X_MRAUI_CUI2 ON umls.MRAUI
  DROP INDEX X_MRCUI_CUI2 ON umls.MRCUI
  DROP INDEX X_MRMAP_MAPSETCUI ON umls.MRMAP
END
go