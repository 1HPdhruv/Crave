package com.srmfood.gag.feature.support

/**
 * Ticket notifications reuse the existing `notifications` table: type GENERAL plus
 * deep_link = "ticket:<ticketId>:<event key>". The event key makes each notification
 * unique server-side so retries cannot produce duplicates (see DRAFT_support_notifications.sql).
 */
private val TICKET_LINK = Regex("^ticket:([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})(?::.*)?$")

fun ticketIdFromDeepLink(deepLink: String?): String? =
    deepLink?.let { TICKET_LINK.matchEntire(it)?.groupValues?.get(1) }
