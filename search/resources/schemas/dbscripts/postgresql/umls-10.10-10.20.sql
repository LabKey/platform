/*
 * Copyright (c) 2010 LabKey Corporation
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

/* umls-10.10-10.11.sql */

/* umls-0.00-10.10.sql */

CREATE SCHEMA umls;

CREATE TABLE umls.MRCOC (
    CUI1 char(8) NOT NULL,
    AUI1 varchar(9) NOT NULL,
    CUI2 char(8),
    AUI2 varchar(9),
    SAB varchar(20) NOT NULL,
    COT varchar(3) NOT NULL,
    COF integer,
    COA varchar(300),
    CVF integer
);

CREATE TABLE umls.MRCOLS (
    COL varchar(20),
    DES varchar(200),
    REF varchar(20),
    MIN integer,
    AV numeric(5,2),
    MAX integer,
    FIL varchar(50),
    DTY varchar(20)
);

CREATE TABLE umls.MRCONSO (
    CUI char(8) NOT NULL,
    LAT char(3) NOT NULL,
    TS char(1) NOT NULL,
    LUI varchar(10) NOT NULL,
    STT varchar(3) NOT NULL,
    SUI varchar(10) NOT NULL,
    ISPREF char(1) NOT NULL,
    AUI varchar(9) NOT NULL,
    SAUI varchar(50),
    SCUI varchar(50),
    SDUI varchar(50),
    SAB varchar(20) NOT NULL,
    TTY varchar(20) NOT NULL,
    CODE varchar(50) NOT NULL,
    STR varchar(3000) NOT NULL,
    SRL integer NOT NULL,
    SUPPRESS char(1) NOT NULL,
    CVF integer
);

CREATE TABLE umls.MRCUI (
    CUI1 char(8) NOT NULL,
    VER varchar(10) NOT NULL,
    REL varchar(4) NOT NULL,
    RELA varchar(100),
    MAPREASON varchar(4000),
    CUI2 char(8),
    MAPIN char(1)
);

CREATE TABLE umls.MRCXT (
    CUI char(8),
    SUI varchar(10),
    AUI varchar(9),
    SAB varchar(20),
    CODE varchar(50),
    CXN integer,
    CXL char(3),
    RANK integer,
    CXS varchar(3000),
    CUI2 char(8),
    AUI2 varchar(9),
    HCD varchar(50),
    RELA varchar(100),
    XC varchar(1),
    CVF integer
);

CREATE TABLE umls.MRDEF (
    CUI char(8) NOT NULL,
    AUI varchar(9) NOT NULL,
    ATUI varchar(11) NOT NULL,
    SATUI varchar(50),
    SAB varchar(20) NOT NULL,
    DEF TEXT NOT NULL,
    SUPPRESS char(1) NOT NULL,
    CVF integer
);

CREATE TABLE umls.MRDOC (
    DOCKEY varchar(50) NOT NULL,
    VALUE varchar(200),
    TYPE varchar(50) NOT NULL,
    EXPL varchar(1000)
);


CREATE TABLE umls.MRFILES (
    FIL varchar(50),
    DES varchar(200),
    FMT varchar(300),
    CLS integer,
    RWS integer,
    BTS integer
);

CREATE TABLE umls.MRHIER (
    CUI char(8) NOT NULL,
    AUI varchar(9) NOT NULL,
    CXN integer NOT NULL,
    PAUI varchar(10),
    SAB varchar(20) NOT NULL,
    RELA varchar(100),
    PTR varchar(1000),
    HCD varchar(50),
    CVF integer
);

CREATE TABLE umls.MRHIST (
    CUI char(8),
    SOURCEUI varchar(50),
    SAB varchar(20),
    SVER varchar(20),
    CHANGETYPE varchar(1000),
    CHANGEKEY varchar(1000),
    CHANGEVAL varchar(1000),
    REASON varchar(1000),
    CVF integer
);

CREATE TABLE umls.MRMAP (
    MAPSETCUI char(8) NOT NULL,
    MAPSETSAB varchar(20) NOT NULL,
    MAPSUBSETID varchar(10),
    MAPRANK integer,
    MAPID varchar(50) NOT NULL,
    MAPSID varchar(50),
    FROMID varchar(50) NOT NULL,
    FROMSID varchar(50),
    FROMEXPR varchar(4000) NOT NULL,
    FROMTYPE varchar(50) NOT NULL,
    FROMRULE varchar(4000),
    FROMRES varchar(4000),
    REL varchar(4) NOT NULL,
    RELA varchar(100),
    TOID varchar(50),
    TOSID varchar(50),
    TOEXPR varchar(4000),
    TOTYPE varchar(50),
    TORULE varchar(4000),
    TORES varchar(4000),
    MAPRULE varchar(4000),
    MAPRES varchar(4000),
    MAPTYPE varchar(50),
    MAPATN varchar(20),
    MAPATV varchar(4000),
    CVF integer
);

CREATE TABLE umls.MRRANK (
    RANK integer NOT NULL,
    SAB varchar(20) NOT NULL,
    TTY varchar(20) NOT NULL,
    SUPPRESS char(1) NOT NULL
);

CREATE TABLE umls.MRREL (
    CUI1 char(8) NOT NULL,
    AUI1 varchar(9),
    STYPE1 varchar(50) NOT NULL,
    REL varchar(4) NOT NULL,
    CUI2 char(8) NOT NULL,
    AUI2 varchar(9),
    STYPE2 varchar(50) NOT NULL,
    RELA varchar(100),
    RUI varchar(10) NOT NULL,
    SRUI varchar(50),
    SAB varchar(20) NOT NULL,
    SL varchar(20) NOT NULL,
    RG varchar(10),
    DIR varchar(1),
    SUPPRESS char(1) NOT NULL,
    CVF integer
);

CREATE TABLE umls.MRSAB (
    VCUI char(8),
    RCUI char(8) NOT NULL,
    VSAB varchar(20) NOT NULL,
    RSAB varchar(20) NOT NULL,
    SON varchar(3000) NOT NULL,
    SF varchar(20) NOT NULL,
    SVER varchar(20),
    VSTART char(8),
    VEND char(8),
    IMETA varchar(10) NOT NULL,
    RMETA varchar(10),
    SLC varchar(1000),
    SCC varchar(1000),
    SRL integer NOT NULL,
    TFR integer,
    CFR integer,
    CXTY varchar(50),
    TTYL varchar(300),
    ATNL varchar(1000),
    LAT char(3),
    CENC varchar(20) NOT NULL,
    CURVER char(1) NOT NULL,
    SABIN char(1) NOT NULL,
    SSN varchar(3000) NOT NULL,
    SCIT varchar(4000) NOT NULL
);

CREATE TABLE umls.MRSAT (
    CUI char(8) NOT NULL,
    LUI varchar(10),
    SUI varchar(10),
    METAUI varchar(50),
    STYPE varchar(50) NOT NULL,
    CODE varchar(50),
    ATUI varchar(11) NOT NULL,
    SATUI varchar(50),
    ATN varchar(50) NOT NULL,
    SAB varchar(20) NOT NULL,
    ATV varchar(4000),
    SUPPRESS char(1) NOT NULL,
    CVF integer
);

CREATE TABLE umls.MRSMAP (
    MAPSETCUI char(8) NOT NULL,
    MAPSETSAB varchar(20) NOT NULL,
    MAPID varchar(50) NOT NULL,
    MAPSID varchar(50),
    FROMEXPR varchar(4000) NOT NULL,
    FROMTYPE varchar(50) NOT NULL,
    REL varchar(4) NOT NULL,
    RELA varchar(100),
    TOEXPR varchar(4000),
    TOTYPE varchar(50),
    CVF integer
);

CREATE TABLE umls.MRSTY (
    CUI char(8) NOT NULL,
    TUI char(4) NOT NULL,
    STN varchar(100) NOT NULL,
    STY varchar(50) NOT NULL,
    ATUI varchar(11) NOT NULL,
    CVF integer
);

CREATE TABLE umls.MRXNS_ENG (
    LAT char(3) NOT NULL,
    NSTR varchar(3000) NOT NULL,
    CUI char(8) NOT NULL,
    LUI varchar(10) NOT NULL,
    SUI varchar(10) NOT NULL
);

CREATE TABLE umls.MRXNW_ENG (
    LAT char(3) NOT NULL,
    NWD varchar(100) NOT NULL,
    CUI char(8) NOT NULL,
    LUI varchar(10) NOT NULL,
    SUI varchar(10) NOT NULL
);

CREATE TABLE umls.MRAUI (
    AUI1 varchar(9) NOT NULL,
    CUI1 char(8) NOT NULL,
    VER varchar(10) NOT NULL,
    REL varchar(4),
    RELA varchar(100),
    MAPREASON varchar(4000) NOT NULL,
    AUI2 varchar(9) NOT NULL,
    CUI2 char(8) NOT NULL,
    MAPIN char(1) NOT NULL
);

CREATE TABLE umls.MRXW (
    LAT char(3) NOT NULL,
    WD varchar(200) NOT NULL,
    CUI char(8) NOT NULL,
    LUI varchar(10) NOT NULL,
    SUI varchar(10) NOT NULL
);


CREATE TABLE umls.AMBIGSUI (
    SUI varchar(10) NOT NULL,
    CUI char(8) NOT NULL
);

CREATE TABLE umls.AMBIGLUI (
    LUI varchar(10) NOT NULL,
    CUI char(8) NOT NULL
);

CREATE TABLE umls.DELETEDCUI (
    PCUI char(8) NOT NULL,
    PSTR varchar(3000) NOT NULL
);

CREATE TABLE umls.DELETEDLUI (
    PLUI varchar(10) NOT NULL,
    PSTR varchar(3000) NOT NULL
);

CREATE TABLE umls.DELETEDSUI (
    PSUI varchar(10) NOT NULL,
    LAT char(3) NOT NULL,
    PSTR varchar(3000) NOT NULL
);

CREATE TABLE umls.MERGEDCUI (
    PCUI char(8) NOT NULL,
    CUI char(8) NOT NULL
);

CREATE TABLE umls.MERGEDLUI (
    PLUI varchar(10),
    LUI varchar(10)
);


CREATE OR REPLACE FUNCTION umls.createIndexes() RETURNS integer AS $$
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
    RETURN 1;
END;
$$ LANGUAGE plpgsql;



CREATE OR REPLACE FUNCTION umls.dropIndexes() RETURNS integer AS $$
BEGIN
    DROP INDEX umls.X_MRCOC_CUI1;
    DROP INDEX umls.X_MRCOC_AUI1;
    DROP INDEX umls.X_MRCOC_CUI2;
    DROP INDEX umls.X_MRCOC_AUI2;
    DROP INDEX umls.X_MRCOC_SAB;
    DROP INDEX umls.X_MRCONSO_CUI;
    ALTER TABLE umls.MRCONSO DROP CONSTRAINT X_MRCONSO_PK;
    DROP INDEX umls.X_MRCONSO_SUI;
    DROP INDEX umls.X_MRCONSO_LUI;
    DROP INDEX umls.X_MRCONSO_CODE;
    DROP INDEX umls.X_MRCONSO_SAB_TTY;
    DROP INDEX umls.X_MRCONSO_SCUI;
    DROP INDEX umls.X_MRCONSO_SDUI ;
  --  DROP INDEX umls.X_MRCONSO_STR;
    DROP INDEX umls.X_MRCXT_CUI;
    DROP INDEX umls.X_MRCXT_AUI;
    DROP INDEX umls.X_MRCXT_SAB;
    DROP INDEX umls.X_MRDEF_CUI;
    DROP INDEX umls.X_MRDEF_AUI;
    ALTER TABLE umls.MRDEF DROP CONSTRAINT X_MRDEF_PK;
    DROP INDEX umls.X_MRDEF_SAB;
    DROP INDEX umls.X_MRHIER_CUI;
    DROP INDEX umls.X_MRHIER_AUI;
    DROP INDEX umls.X_MRHIER_SAB;
  --  DROP INDEX umls.X_MRHIER_PTR;
    DROP INDEX umls.X_MRHIER_PAUI;
    DROP INDEX umls.X_MRHIST_CUI;
    DROP INDEX umls.X_MRHIST_SOURCEUI;
    DROP INDEX umls.X_MRHIST_SAB;
    ALTER TABLE umls.MRRANK DROP CONSTRAINT X_MRRANK_PK;
    DROP INDEX umls.X_MRREL_CUI1;
    DROP INDEX umls.X_MRREL_AUI1;
    DROP INDEX umls.X_MRREL_CUI2;
    DROP INDEX umls.X_MRREL_AUI2;
    ALTER TABLE umls.MRREL DROP CONSTRAINT X_MRREL_PK;
    DROP INDEX umls.X_MRREL_SAB;
    ALTER TABLE umls.MRSAB DROP CONSTRAINT X_MRSAB_PK;
    DROP INDEX umls.X_MRSAB_RSAB;
    DROP INDEX umls.X_MRSAT_CUI;
    DROP INDEX umls.X_MRSAT_METAUI;
    ALTER TABLE umls.MRSAT DROP CONSTRAINT X_MRSAT_PK;
    DROP INDEX umls.X_MRSAT_SAB;
    DROP INDEX umls.X_MRSAT_ATN;
    DROP INDEX umls.X_MRSTY_CUI;
    ALTER TABLE umls.MRSTY DROP CONSTRAINT X_MRSTY_PK;
    DROP INDEX umls.X_MRSTY_STY;
  --  DROP INDEX umls.X_MRXNS_ENG_NSTR;
    DROP INDEX umls.X_MRXNW_ENG_NWD;
    DROP INDEX umls.X_MRXW_WD;
    DROP INDEX umls.X_AMBIGSUI_SUI;
    DROP INDEX umls.X_AMBIGLUI_LUI;
    DROP INDEX umls.X_MRAUI_CUI2;
    DROP INDEX umls.X_MRCUI_CUI2;
    DROP INDEX umls.X_MRMAP_MAPSETCUI;
    RETURN 1;
END;
$$ LANGUAGE plpgsql;