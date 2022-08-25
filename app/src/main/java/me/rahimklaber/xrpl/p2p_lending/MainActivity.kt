package me.rahimklaber.xrpl.p2p_lending

import android.bluetooth.BluetoothManager
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHost
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.squareup.sqldelight.android.AndroidSqliteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rahimklaber.xrpl.p2p_lending.model.AssetModel
import me.rahimklaber.xrpl.p2p_lending.model.BalanceModel
import me.rahimklaber.xrpl.p2p_lending.model.TakenLoanModel
import me.rahimklaber.xrpl.p2p_lending.screen.MainScreen
import me.rahimklaber.xrpl.p2p_lending.ui.theme.AppTheme
import nl.tudelft.ipv8.IPv8Configuration
import nl.tudelft.ipv8.Overlay
import nl.tudelft.ipv8.OverlayConfiguration
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.android.IPv8Android
import nl.tudelft.ipv8.android.keyvault.AndroidCryptoProvider
import nl.tudelft.ipv8.android.messaging.bluetooth.BluetoothLeDiscovery
import nl.tudelft.ipv8.android.peerdiscovery.NetworkServiceDiscovery
import nl.tudelft.ipv8.attestation.trustchain.TrustChainCommunity
import nl.tudelft.ipv8.attestation.trustchain.TrustChainSettings
import nl.tudelft.ipv8.attestation.trustchain.store.TrustChainSQLiteStore
import nl.tudelft.ipv8.keyvault.PrivateKey
import nl.tudelft.ipv8.peerdiscovery.DiscoveryCommunity
import nl.tudelft.ipv8.peerdiscovery.strategy.PeriodicSimilarity
import nl.tudelft.ipv8.peerdiscovery.strategy.RandomChurn
import nl.tudelft.ipv8.peerdiscovery.strategy.RandomWalk
import nl.tudelft.ipv8.sqldelight.Database
import nl.tudelft.ipv8.util.hexToBytes
import nl.tudelft.ipv8.util.toHex

val usd = AssetModel("US Dollar", "USD", "Federal Bank", "")
val balanceModels = (0..5).map { BalanceModel(usd, "5") }
val loanModels = (0..5).map { TakenLoanModel("Dude1", "200", usd) }


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initIPv8()
        setContent {
            val scaffoldState = rememberScaffoldState()
            val navController = rememberNavController()
            val viewModel = remember {
                WalletViewModel()
            }
            AppTheme {
                // A surface container using the 'background' color from the theme
                Scaffold(scaffoldState = scaffoldState,
                    bottomBar = {
                        BottomAppBar(contentPadding = PaddingValues(10.dp)) {
                            Button(onClick = {   viewModel.viewModelScope.launch {
                                navController.navigate("main")
                            }}) {
                                Text("home")
                            }
                            Button(onClick = {   viewModel.viewModelScope.launch {
                                navController.navigate("peers")
                            }}) {
                                Text("debug")
                            }
                            Button(onClick = {   viewModel.viewModelScope.launch {
                                navController.navigate("available_loans")
                            }}) {
                                Text("loans")
                            }

                        }
                    }) {
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(it),
                        color = MaterialTheme.colors.background
                    ) {
                        NavHost(navController = navController, startDestination = "main") {
                            composable("main") {
                                MainScreen(
                                    navController = navController,
                                    viewModel = viewModel,
                                    loans = loanModels
                                )
                            }
                            composable("peers") {
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
                            composable("create_loan") {
                                Column(Modifier.fillMaxWidth(),horizontalAlignment = Alignment.CenterHorizontally) {
                                    var amount by remember {
                                        mutableStateOf(0.0)
                                    }
                                    var termDays by remember {
                                        mutableStateOf(0)
                                    }
                                    var totalInterest by remember {
                                        mutableStateOf(0.0)
                                    }
                                    OutlinedTextField(
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        value = amount.toString(),
                                        onValueChange = { x -> amount = x.toDoubleOrNull()?:amount  },
                                        label = {
                                            Text(
                                                text = "amount"
                                            )
                                        })
                                    OutlinedTextField(
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        value = termDays.toString(),
                                        onValueChange = { x: String ->
                                            termDays = x.toIntOrNull() ?: termDays
                                        },
                                        label = {
                                            Text(
                                                text = "term days"
                                            )
                                        })
                                    OutlinedTextField(
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        value = totalInterest.toString(),
                                        onValueChange = { x: String ->
                                            totalInterest = x.toDoubleOrNull() ?: totalInterest
                                        },
                                        label = {
                                            Text(
                                                text = "total interest percent"
                                            )
                                        })
                                    Button(onClick = { viewModel.viewModelScope.launch(Dispatchers.Default) {
                                        viewModel.advertiseLoan(amount, termDays, totalInterest)
                                        viewModel.viewModelScope.launch() {
                                            navController.navigate("main")
                                        }
                                    } }) {
                                        Text("create")
                                    }
                                }
                            }
                            composable("available_loans"){
                                LazyColumn{
                                    items(viewModel.loanAdvertisements.map(Pair<Peer,AdvertiseLoanMessage>::second)){
                                       Column {
                                           Row{
                                               Column {
                                                   Text("amount : ${it.amount}")
                                                   Text("totalInterest : ${it.totalInterest}")
                                               }
                                               Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopEnd){
                                                   Text("term : ${it.termDays}")
                                               }
                                           }
                                           Button(onClick = {
                                               viewModel.acceptLoan(it.id)
                                           }) {
                                                Text("Accept")
                                           }
                                       }
                                        Divider(color = Color(0xFFB3B095), thickness = 1.dp)
                                    }
                                }
                            }
                        }
                    }
                }

            }
        }
    }

    private fun initIPv8() {
        val settings = TrustChainSettings()
        val driver = AndroidSqliteDriver(Database.Schema, this, "trustchain.db")
        val store = TrustChainSQLiteStore(Database(driver))
        val randomWalk = RandomWalk.Factory()
        val trustChainCommunity = OverlayConfiguration(
            TrustChainCommunity.Factory(settings, store),
            listOf(randomWalk)
        )
        val config = IPv8Configuration(
            overlays = listOf(
                createDiscoveryCommunity(),
                createLoanCommunity(),
                trustChainCommunity
            ), walkerInterval = 5.0
        )

        IPv8Android.Factory(application)
            .setConfiguration(config)
            .setPrivateKey(getPrivateKey())
            .init()



    }


    private fun createDiscoveryCommunity(): OverlayConfiguration<DiscoveryCommunity> {
        val randomWalk = RandomWalk.Factory()
        val randomChurn = RandomChurn.Factory()
        val periodicSimilarity = PeriodicSimilarity.Factory()

        val nsd = NetworkServiceDiscovery.Factory(getSystemService()!!)
//        val bluetoothManager = getSystemService<BluetoothManager>()
//            ?: throw IllegalStateException("BluetoothManager not available")
        val strategies = mutableListOf(
            randomWalk, randomChurn, periodicSimilarity, nsd
        )
//        if (bluetoothManager.adapter != null && Build.VERSION.SDK_INT >= 24) {
//            val ble = BluetoothLeDiscovery.Factory()
//            strategies += ble
//        }

        return OverlayConfiguration(
            DiscoveryCommunity.Factory(),
            strategies
        )
    }


    private fun createLoanCommunity(): OverlayConfiguration<LoanCommunity> {
        val randomWalk = RandomWalk.Factory()
        return OverlayConfiguration(
            Overlay.Factory(LoanCommunity::class.java),
            listOf(randomWalk)
        )
    }

    private fun getPrivateKey(): PrivateKey {
        // Load a key from the shared preferences
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val privateKey = prefs.getString(PREF_PRIVATE_KEY, null)
        return if (privateKey == null) {
            // Generate a new key on the first launch
            val newKey = AndroidCryptoProvider.generateKey()
            prefs.edit()
                .putString(PREF_PRIVATE_KEY, newKey.keyToBin().toHex())
                .apply()
            newKey
        } else {
            AndroidCryptoProvider.keyFromPrivateBin(privateKey.hexToBytes())
        }
    }

    companion object {
        private const val PREF_PRIVATE_KEY = "private_key"
        private const val BLOCK_TYPE = "demo_block"
    }
}


//@Preview(showBackground = true)
//@Composable
//fun DefaultPreview() {
//    AppTheme {
//        Surface(
//            modifier = Modifier.fillMaxSize(),
//            color = MaterialTheme.colors.background
//        ) {
//            MainScreen(balances = balanceModels, loans = loanModels)
//        }
//    }
//}