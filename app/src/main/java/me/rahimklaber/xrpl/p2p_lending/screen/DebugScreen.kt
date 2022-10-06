package me.rahimklaber.xrpl.p2p_lending.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Card
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import me.rahimklaber.xrpl.p2p_lending.WalletViewModel
import nl.tudelft.ipv8.Peer

@Composable
fun DebugScreen(viewModel: WalletViewModel){
    val peers = remember { mutableStateListOf<Peer>() }
    var lookig_for_peers by remember { mutableStateOf(false) }
    LaunchedEffect(key1 = null) {
        while (true) {

            delay(1000)
            peers.clear()
            viewModel.ipv8.overlays.forEach { (_, overlay) ->
                lookig_for_peers = true
                peers.addAll(overlay.getPeers())
            }
        }
    }
    LazyColumn {
        item() {
            Card() {
                Column {
                    Text("my mid: ${viewModel.ipv8.myPeer.mid}")
                    Text("Looking checking for our peers : $lookig_for_peers")
                    Text("Amount of peers : ${peers.size}")
                }
            }
        }
        for (it in peers) {
            item {
                Card(Modifier.padding(vertical = 5.dp)) {
                    Column {
                        Text(it.toString())
                    }
                }
            }
        }
    }
}