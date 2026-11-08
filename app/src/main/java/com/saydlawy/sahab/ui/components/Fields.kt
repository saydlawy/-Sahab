package com.saydlawy.sahab.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
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
import com.saydlawy.sahab.ui.theme.Palette
import com.saydlawy.sahab.ui.theme.TajawalFamily
import com.saydlawy.sahab.util.Ar

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
    var reveal by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(20.dp)

    val textStyle = TextStyle(
        color = Palette.Text,
        fontSize = 18.sp,
        fontFamily = TajawalFamily,
        textDirection = if (ltr) TextDirection.Ltr else TextDirection.Content,
        textAlign = if (ltr) TextAlign.Left else TextAlign.Start,
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .clip(shape)
            .background(Color.White.copy(alpha = if (focused) 0.20f else 0.10f))
            .border(
                width = if (focused) 3.dp else 1.dp,
                brush = if (focused) Brush.linearGradient(listOf(Palette.Violet, Palette.Pink, Palette.Cyan))
                else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.40f), Color.White.copy(alpha = 0.06f))),
                shape = shape,
            )
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Icon(leading, contentDescription = null, tint = Palette.TextDim, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(placeholder, fontSize = 18.sp, color = Palette.TextDim.copy(alpha = 0.75f), maxLines = 1)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = textStyle,
                cursorBrush = SolidColor(Palette.Pink),
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
            Spacer(Modifier.width(10.dp))
            Text(
                text = if (reveal) Ar.hide else Ar.show,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Palette.Cyan,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { reveal = !reveal }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
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
 * Category picker: a side list on wide screens (televisions, tablets), a row of chips on phones.
 */
@Composable
fun CategoryPane(
    categories: List<Category>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    wide: Boolean,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    if (wide) {
        LazyColumn(
            modifier = modifier.focusRestorer(),
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(categories, key = { it.id }) { category ->
                GlassCard(
                    onClick = { onSelect(category.id) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    selected = category.id == selectedId,
                ) {
                    Text(
                        text = category.name,
                        fontSize = 17.sp,
                        fontWeight = if (category.id == selectedId) FontWeight.Bold else FontWeight.Medium,
                        color = Palette.Text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    )
                }
            }
        }
    } else {
        LazyRow(
            modifier = modifier.focusRestorer(),
            state = listState,
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(categories, key = { it.id }) { category ->
                Chip(category.name, category.id == selectedId, { onSelect(category.id) })
            }
        }
    }
}
