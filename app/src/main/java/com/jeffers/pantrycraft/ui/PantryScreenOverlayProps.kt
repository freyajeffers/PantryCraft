package com.jeffers.pantrycraft.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.jeffers.pantrycraft.data.PantryItem

internal data class PantryScreenOverlayProps(
    val showAddDialog: Boolean,
    val editorItem: PantryItem?,
    val onSaveDraft: (Boolean, PantryItem) -> Unit,
    val onDismissEditor: () -> Unit,
    val useItem: PantryItem?,
    val useAmount: String,
    val onAmountChange: (String) -> Unit,
    val onConfirmUse: (Float) -> Unit,
    val onDismissUse: () -> Unit,
    val storageError: String?,
    val onDismissStorageError: () -> Unit,
)

@Composable
internal fun PantryScreenErrorHandler(
    viewModel: PantryViewModel,
    snackbarHostState: SnackbarHostState,
    onStorageError: (String) -> Unit,
) {
    LaunchedEffect(viewModel) {
        viewModel.operationErrors.collect { message ->
            if (message.startsWith("Storage is full")) onStorageError(message)
            snackbarHostState.showSnackbar(message)
        }
    }
}

@Composable
internal fun PantryScreenOverlays(props: PantryScreenOverlayProps) {
    props.storageError?.let { message ->
        StorageDiagnosticDialog(
            message = message,
            onDismiss = props.onDismissStorageError,
        )
    }
    if (props.showAddDialog) {
        PantryItemEditorSheet(
            editorItem = props.editorItem,
            onSaveDraft = props.onSaveDraft,
            onDismiss = props.onDismissEditor,
        )
    }
    props.useItem?.let { item ->
        UsePantryItemDialog(
            item = item,
            amount = props.useAmount,
            onAmountChange = props.onAmountChange,
            onConfirm = props.onConfirmUse,
            onDismiss = props.onDismissUse,
        )
    }
}
