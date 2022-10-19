package me.rahimklaber.xrpl.p2p_lending.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rahimklaber.xrpl.p2p_lending.WalletViewModel

@Composable
fun UsersScreen(viewModel: WalletViewModel) {
    LazyColumn {
        items(viewModel.foundPeers) {
            var reputation by remember {
                mutableStateOf<Int?>(null)
            }
            LaunchedEffect(key1 = null) {
                if (reputation != null)
                    return@LaunchedEffect
                reputation = withContext(Dispatchers.IO) {
                    viewModel.getReputationOfPeer(it)
                }
            }
            Card {
                Row {
                    Column {
                        Text("User : ${viewModel.getNicknameOfMid(it.mid) ?: "UNKNOWN"}")
                        Text("Reputation : ${reputation ?: "loading..."}")
                    }
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        Button(enabled = !viewModel.trustedPeersMids.contains(it.mid), onClick = {
                            viewModel.viewModelScope.launch(Dispatchers.Default) {
                                viewModel.trustUser(it)
                            }
                        }) {
                            Text("Trust user")
                        }
                    }
                }
            }
        }
    }
}