/*
 *   Copyright 2022 Benoit LETONDOR
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package com.benoitletondor.pixelminimalwatchface.compose.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.wear.compose.material.*

@Composable
fun SettingToggleChip(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    @DrawableRes iconDrawable: Int?,
    modifier: Modifier = Modifier,
) {
    ToggleChip(
        label = {
            Text(
                text = label,
            )
        },
        toggleControl = {
            Icon (
                imageVector = ToggleChipDefaults.switchIcon(checked),
                contentDescription = null,
            )
        },
        colors = ToggleChipDefaults.toggleChipColors(
            checkedStartBackgroundColor = MaterialTheme.colors.surface,
            checkedEndBackgroundColor = MaterialTheme.colors.surface,
        ),
        checked = checked,
        appIcon = iconDrawable?.let {
            { Icon(
                painter = painterResource(id = it),
                contentDescription = null,
                modifier = Modifier.size(ChipDefaults.SmallIconSize)
            ) }
        },
        onCheckedChange = onCheckedChange,
        modifier = modifier
            .fillMaxWidth(),
    )
}