/*
 * Copyright (c) 2009 LabKey Corporation
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
package org.labkey.study.writer;

import org.labkey.study.model.Study;
import org.labkey.api.util.VirtualFile;

import java.io.PrintWriter;

/**
 * User: adam
 * Date: May 7, 2009
 * Time: 3:49:32 PM
 */
public class SpecimenWriter implements Writer<Study>
{
    public void write(Study study, ExportContext ctx, VirtualFile fs) throws Exception
    {
        PrintWriter pw = fs.getPrintWriter("specimens.tsv");

        pw.println("# specimens");
        pw.println("record_id\trecord_source\tglobal_unique_specimen_id\tlab_id\toriginating_location\tunique_specimen_id\tptid\tparent_specimen_id\tdraw_timestamp\tsal_receipt_date\tspecimen_number\tclass_id\tvisit_value\tprotocol_number\tvisit_description\tother_specimen_id\tvolume\tvolume_units\tstored\tstorage_flag\tstorage_date\tship_flag\tship_batch_number\tship_date\timported_batch_number\tlab_receipt_date\texpected_time_value\texpected_time_unit\tgroup_protocol\tsub_additive_derivative\tcomments\tprimary_specimen_type_id\tderivative_type_id\tadditive_type_id\tspecimen_condition\tsample_number\tx_sample_origin\texternal_location\tupdate_timestamp\tshipped_from_lab\tshipped_to_lab\tfreezer\tfr_level1\tfr_level2\tfr_container\tfr_position\trequestable");

        pw.println("746\tldms\tAAA07XK5-01\t20\t21\t235978\t999320812\t235977\t12/13/2005 15:02\t1/13/2006\t350V06002948\tLABK\t601\t39\tVst\tCP058612766\t2\tML\t\t\t2/2/2008\t\t\t2/5/2008\t\t2/1/2008\t0\t\t23209\tN/A\t\t1\t47\t10\tSAT\t\t\t\t29:53.1\t21\t20\tFRED\tSHELF 2\t-80 RACK 24 BOXES 8\tUNC -80 BOX 1\t10\t");
        pw.println("747\tldms\tAAA07XK5-01\t19\t21\t235978\t999320812\t235977\t12/13/2005 15:02\t1/13/2006\t350V0600294A\tLABK\t601\t39\tVst\tCP058612766\t1\tML\t\t\t2/7/2008\t\t\t\t\t2/7/2008\t0\t\t23209\tN/A\t\t1\t47\t10\tSAT\t\t\t\t29:53.1\t21\t19\tLABK REVCO 18 REP 2\tMIX SHELF 5\t5D - 390 RACK 1\t390 BATCH 2\t\"1,010\"\t");
        pw.println("748\tldms\tAAA07XK5-02\t19\t21\t235979\t999320812\t235977\t12/13/2005 15:02\t1/13/2006\t350V0600294B\tLABK\t601\t39\tVst\tCP058612766\t1\tML\t\t\t2/2/2008\t\t\t2/5/2008\t\t2/1/2008\t0\t\t23209\tN/A\t\t1\t47\t10\tSAT\t\t\t\t29:53.1\t21\t19\t\t\t\t\t\t");
        pw.println("748\tldms\tAAA07XK5-02\t20\t21\t235979\t999320812\t235977\t12/13/2005 15:02\t1/13/2006\t350V0600294B\tLABK\t601\t39\tVst\tCP058612766\t1\tML\t\t\t2/7/2008\t\t\t2/9/2008\t\t2/7/2008\t0\t\t23209\tN/A\t\t56\t65\t36\tSAT\t\t\t\t29:53.1\t21\t19\tLABK LN2\tRACK 1\t\t391 BOX B\t\"4,008\"\t");
        pw.println("748\tldms\tAAA07XK5-02\t21\t21\t235979\t999320812\t235977\t12/13/2005 15:02\t1/13/2006\t350V0600294B\tLABK\t601\t39\tVst\tCP058612766\t1\tML\t\t\t2/10/2008\t\t\t\t\t2/10/2008\t0\t\t23209\tN/A\t\t1\t47\t10\tSAT\t\t\t\t29:53.1\t21\t19\t\t\t\t\t\t");

        pw.close();
    }
}
