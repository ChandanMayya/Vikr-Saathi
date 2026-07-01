package com.kex.vikrsaathi.ui.help

import android.content.Context
import com.kex.vikrsaathi.R

object HelpContentProvider {

    fun get(context: Context, screen: HelpScreen): HelpGuide {
        return when (screen) {
            HelpScreen.DASHBOARD -> guide(
                context,
                R.string.help_dashboard_title,
                R.string.help_dashboard_overview,
                section(R.string.help_dashboard_s1_title, R.array.help_dashboard_s1),
                section(R.string.help_dashboard_s2_title, R.array.help_dashboard_s2)
            )
            HelpScreen.NEW_BILL -> guide(
                context,
                R.string.help_new_bill_title,
                R.string.help_new_bill_overview,
                section(R.string.help_new_bill_s1_title, R.array.help_new_bill_s1),
                section(R.string.help_new_bill_s2_title, R.array.help_new_bill_s2),
                section(R.string.help_new_bill_s3_title, R.array.help_new_bill_s3)
            )
            HelpScreen.BILL_VIEW -> guide(
                context,
                R.string.help_bill_view_title,
                R.string.help_bill_view_overview,
                section(R.string.help_bill_view_s1_title, R.array.help_bill_view_s1),
                section(R.string.help_bill_view_s2_title, R.array.help_bill_view_s2)
            )
            HelpScreen.HELD_BILLS -> guide(
                context,
                R.string.help_held_bills_title,
                R.string.help_held_bills_overview,
                section(R.string.help_held_bills_s1_title, R.array.help_held_bills_s1)
            )
            HelpScreen.BILL_PREVIEW -> guide(
                context,
                R.string.help_bill_preview_title,
                R.string.help_bill_preview_overview,
                section(R.string.help_bill_preview_s1_title, R.array.help_bill_preview_s1)
            )
            HelpScreen.CUSTOMERS -> guide(
                context,
                R.string.help_customers_title,
                R.string.help_customers_overview,
                section(R.string.help_customers_s1_title, R.array.help_customers_s1),
                section(R.string.help_customers_s2_title, R.array.help_customers_s2)
            )
            HelpScreen.ITEMS -> guide(
                context,
                R.string.help_items_title,
                R.string.help_items_overview,
                section(R.string.help_items_s1_title, R.array.help_items_s1),
                section(R.string.help_items_s2_title, R.array.help_items_s2)
            )
            HelpScreen.BILLS_HISTORY -> guide(
                context,
                R.string.help_bills_history_title,
                R.string.help_bills_history_overview,
                section(R.string.help_bills_history_s1_title, R.array.help_bills_history_s1),
                section(R.string.help_bills_history_s2_title, R.array.help_bills_history_s2)
            )
            HelpScreen.EXCEL_UPLOAD -> guide(
                context,
                R.string.help_excel_upload_title,
                R.string.help_excel_upload_overview,
                section(R.string.help_excel_upload_s1_title, R.array.help_excel_upload_s1)
            )
            HelpScreen.SETTINGS -> guide(
                context,
                R.string.help_settings_title,
                R.string.help_settings_overview,
                section(R.string.help_settings_s1_title, R.array.help_settings_s1)
            )
            HelpScreen.GENERAL_SETTINGS -> guide(
                context,
                R.string.help_general_settings_title,
                R.string.help_general_settings_overview,
                section(R.string.help_general_settings_s1_title, R.array.help_general_settings_s1)
            )
            HelpScreen.INVOICE_CONFIGURATION -> guide(
                context,
                R.string.help_invoice_config_title,
                R.string.help_invoice_config_overview,
                section(R.string.help_invoice_config_s1_title, R.array.help_invoice_config_s1)
            )
            HelpScreen.INVOICE_IMAGE -> guide(
                context,
                R.string.help_invoice_image_title,
                R.string.help_invoice_image_overview,
                section(R.string.help_invoice_image_s1_title, R.array.help_invoice_image_s1)
            )
            HelpScreen.INVOICE_COUNTER -> guide(
                context,
                R.string.help_invoice_counter_title,
                R.string.help_invoice_counter_overview,
                section(R.string.help_invoice_counter_s1_title, R.array.help_invoice_counter_s1)
            )
            HelpScreen.INVOICE_TEMPLATES -> guide(
                context,
                R.string.help_invoice_templates_title,
                R.string.help_invoice_templates_overview,
                section(R.string.help_invoice_templates_s1_title, R.array.help_invoice_templates_s1)
            )
            HelpScreen.INVOICE_BUILDER -> guide(
                context,
                R.string.help_invoice_builder_title,
                R.string.help_invoice_builder_overview,
                section(R.string.help_invoice_builder_s1_title, R.array.help_invoice_builder_s1),
                section(R.string.help_invoice_builder_s2_title, R.array.help_invoice_builder_s2)
            )
            HelpScreen.BACKUP -> guide(
                context,
                R.string.help_backup_title,
                R.string.help_backup_overview,
                section(R.string.help_backup_s1_title, R.array.help_backup_s1)
            )
            HelpScreen.RESET -> guide(
                context,
                R.string.help_reset_title,
                R.string.help_reset_overview,
                section(R.string.help_reset_s1_title, R.array.help_reset_s1)
            )
        }
    }

    private data class SectionRes(val titleRes: Int, val itemsRes: Int)

    private fun section(titleRes: Int, itemsRes: Int) = SectionRes(titleRes, itemsRes)

    private fun guide(
        context: Context,
        titleRes: Int,
        overviewRes: Int,
        vararg sections: SectionRes
    ): HelpGuide {
        return HelpGuide(
            title = context.getString(titleRes),
            overview = context.getString(overviewRes),
            sections = sections.map { section ->
                HelpSection(
                    title = context.getString(section.titleRes),
                    items = context.resources.getStringArray(section.itemsRes).toList()
                )
            }
        )
    }
}
