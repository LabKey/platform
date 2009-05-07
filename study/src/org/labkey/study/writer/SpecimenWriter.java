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

        pw.close();
    }
}
