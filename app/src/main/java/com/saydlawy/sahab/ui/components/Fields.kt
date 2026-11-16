package com.saydlawy.sahab.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.saydlawy.sahab.ui.components.SahabIcon as Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saydlawy.sahab.data.Category
import com.saydlawy.sahab.ui.LocalLightEffects
import com.saydlawy.sahab.ui.theme.Palette
import com.saydlawy.sahab.ui.theme.CairoFamily
import com.saydlawy.sahab.util.Ar

/**
 * Semi-transparent dark input. The icon sits at the end of the field (left in RTL),
 * the border glows cyan when the field has focus.
 */
@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    leading: ImageVector? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done,
    password: Boolean = false,
    ltr: Boolean = false,
    onImeAction: () -> Unit = {},
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val focusManager = LocalFocusManager.current
    val light = LocalLightEffects.current
    var reveal by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)

    val textStyle = TextStyle(
        color = Palette.Text,
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        fontFamily = CairoFamily,
        textDirection = if (ltr) TextDirection.Ltr else TextDirection.Content,
        textAlign = if (ltr) TextAlign.Left else TextAlign.Start,
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(
                if (focused && !light) Modifier.shadow(14.dp, shape, ambientColor = Palette.Glow, spotColor = Palette.Glow)
                else Modifier,
            )
            .clip(shape)
            .background(Palette.InputBg)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Palette.Glow else Color.White.copy(alpha = 0.12f),
                shape = shape,
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(placeholder, fontSize = 15.sp, color = Palette.TextDim.copy(alpha = 0.7f), maxLines = 1)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = textStyle,
                cursorBrush = SolidColor(Palette.Glow),
                interactionSource = interaction,
                visualTransformation = if (password && !reveal) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) },
                    onDone = {
                        focusManager.clearFocus()
                        onImeAction()
                    },
                    onSearch = {
                        focusManager.clearFocus()
                        onImeAction()
                    },
                ),
            )
        }
        if (password) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (reveal) Ar.hide else Ar.show,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Palette.Glow,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { reveal = !reveal }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
        if (leading != null) {
            Spacer(Modifier.width(10.dp))
            Icon(
                leading,
                contentDescription = null,
                tint = if (focused) Palette.Glow else Palette.TextDim,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
fun SearchBox(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    GlassTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = placeholder,
        leading = Ic.Search,
        imeAction = ImeAction.Search,
    )
}

/**
 * Sidebar navigation item of the identity: glass row, label on the start side, a count pill or icon
 * on the end side. When selected it gets the royal gradient, a cyan border and a glowing edge bar.
 */
@Composable
fun SideNavItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    count: Int? = null,
    icon: ImageVector? = null,
    iconTint: Color = Palette.Glow,
    trailingText: String? = null,
    trailingColor: Color = Palette.Gold,
) {
    val isTv = com.saydlawy.sahab.ui.LocalIsTv.current
    GlassCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        selected = selected,
        focusScale = if (isTv) 1.02f else 1f,
    ) { focused ->
        if (selected && isTv) {
            Box(Modifier.matchParentSize()) {
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxHeight()
                        .width(5.dp)
                        .background(Palette.Glow),
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = if (isTv) 54.dp else 44.dp)
                .padding(horizontal = if (isTv) 18.dp else 12.dp, vertical = if (isTv) 12.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                fontSize = if (isTv) 16.sp else 13.5.sp,
                fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Medium,
                color = if (selected || focused) Palette.Text else Palette.TextDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (trailingText != null) {
                Spacer(Modifier.width(8.dp))
                Text(trailingText, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = trailingColor)
            }
            if (count != null) {
                Spacer(Modifier.width(8.dp))
                if (selected) {
                    Text(
                        count.toString(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Palette.Glow,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Palette.Royal.copy(alpha = 0.40f))
                            .padding(horizontal = 10.dp, vertical = 3.dp),
                    )
                } else {
                    Text(count.toString(), fontSize = 12.sp, color = Palette.TextDim.copy(alpha = 0.75f))
                }
            }
            if (icon != null) {
                Spacer(Modifier.width(8.dp))
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/**
 * Category picker: a glass sidebar on wide screens (televisions, tablets), a row of chips on phones.
 */
@Composable
fun CategoryPane(
    categories: List<Category>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    wide: Boolean,
    modifier: Modifier = Modifier,
    counts: Map<String, Int> = emptyMap(),
) {
    val listState = rememberLazyListState()
    if (wide) {
        LazyColumn(
            modifier = modifier.focusRestorer(),
            state = listState,
            contentPadding = PaddingValues(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(categories, key = { it.id }) { category ->
                SideNavItem(
                    label = category.name,
                    selected = category.id == selectedId,
                    onClick = { onSelect(category.id) },
                    count = counts[category.id]?.takeIf { it > 0 },
                )
            }
        }
    } else {
        LazyRow(
            modifier = modifier.focusRestorer(),
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(categories, key = { it.id }) { category ->
                Chip(category.name, category.id == selectedId, { onSelect(category.id) })
            }
        }
    }
}
