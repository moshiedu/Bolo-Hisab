package com.bolohisab.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.MoneyOff
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bolohisab.R
import com.bolohisab.data.LedgerEntry
import com.bolohisab.nlu.EntryType
import com.bolohisab.nlu.ItemLine
import com.bolohisab.ui.format.Bn
import com.bolohisab.ui.theme.LedgerTheme
import com.bolohisab.ui.theme.MoneyStyle

private data class TypeLook(val icon: ImageVector, val tint: Color, val container: Color)

@Composable
private fun EntryType.look(): TypeLook {
    val c = LedgerTheme.colors
    return when (this) {
        EntryType.CASH_SALE -> TypeLook(
            Icons.Rounded.Storefront,
            MaterialTheme.colorScheme.onSecondaryContainer,
            MaterialTheme.colorScheme.secondaryContainer,
        )
        EntryType.CREDIT_SALE -> TypeLook(Icons.AutoMirrored.Rounded.ReceiptLong, c.due, c.dueContainer)
        EntryType.PAYMENT_RECEIVED -> TypeLook(Icons.Rounded.Payments, c.paid, c.paidContainer)
        EntryType.EXPENSE -> TypeLook(Icons.Rounded.MoneyOff, c.expense, c.expenseContainer)
    }
}

fun itemsSummary(items: List<ItemLine>): String = items.joinToString(", ") { item ->
    val qty = item.quantity?.let { q -> " " + Bn.qty(q) + (item.unit?.let { " ${it.label}" } ?: "") }.orEmpty()
    item.name + qty
}

/**
 * One ledger line. The amount shown is what matters for that type:
 * credit shows the due it added, a payment what came in.
 */
@Composable
fun EntryRow(entry: LedgerEntry, showCustomer: Boolean = true, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val look = entry.type.look()
    val c = LedgerTheme.colors
    val customerName = entry.customer?.name?.takeIf { showCustomer }
    val title = when {
        customerName != null -> customerName
        entry.type == EntryType.EXPENSE -> entry.note ?: stringResource(R.string.entry_expense)
        entry.type == EntryType.CASH_SALE -> stringResource(R.string.entry_walk_in)
        else -> entry.type.label()
    }
    val note = entry.note
    val detail = when {
        entry.items.isNotEmpty() -> itemsSummary(entry.items)
        !note.isNullOrBlank() && entry.type != EntryType.EXPENSE -> note
        else -> entry.type.label()
    }
    val (amount, amountColor) = when (entry.type) {
        EntryType.CREDIT_SALE -> Bn.taka(entry.balanceDelta) to c.due
        EntryType.PAYMENT_RECEIVED -> Bn.taka(entry.paid) to c.paid
        EntryType.EXPENSE -> Bn.taka(entry.total) to c.expense
        EntryType.CASH_SALE -> Bn.taka(entry.total) to MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(42.dp).background(look.container, CircleShape), contentAlignment = Alignment.Center) {
            Icon(look.icon, contentDescription = entry.type.label(), tint = look.tint, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                amount,
                style = MaterialTheme.typography.titleMedium.merge(MoneyStyle),
                fontWeight = FontWeight.SemiBold,
                color = amountColor,
            )
            val sub = if (entry.type == EntryType.CREDIT_SALE && entry.paid.value > 0) {
                stringResource(R.string.entry_paid_part, Bn.taka(entry.paid))
            } else {
                Bn.time(entry.createdAt)
            }
            Text(sub, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun EntryType.label(): String = stringResource(
    when (this) {
        EntryType.CASH_SALE -> R.string.type_cash_sale
        EntryType.CREDIT_SALE -> R.string.type_credit_sale
        EntryType.PAYMENT_RECEIVED -> R.string.type_payment
        EntryType.EXPENSE -> R.string.type_expense
    },
)

/**
 * A customer's profile photo if they have one, otherwise an initial-letter avatar. Bangla
 * initials include their vowel sign, so the first grapheme (not the first UTF-16 char) is taken.
 */
@Composable
fun Avatar(name: String, photoPath: String? = null, size: Dp = 42.dp, modifier: Modifier = Modifier) {
    if (photoPath != null) {
        AsyncImage(
            model = photoPath,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size).clip(CircleShape),
        )
        return
    }
    val initial = name.trim().let { n ->
        val it = java.text.BreakIterator.getCharacterInstance()
        it.setText(n)
        val end = it.next()
        if (end > 0) n.substring(0, end) else "?"
    }
    Box(
        modifier.size(size).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(initial, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}
