package me.rahimklaber.xrpl.p2p_lending

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import me.rahimklaber.xrpl.p2p_lending.model.BalanceModel
import me.rahimklaber.xrpl.p2p_lending.model.TakenLoanModel
import me.rahimklaber.xrpl.p2p_lending.state.Wallet
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.android.IPv8Android
import nl.tudelft.ipv8.attestation.trustchain.TrustChainCommunity

class WalletViewModel : ViewModel() {
    private lateinit var wallet : Wallet
    var walletInitDone : Boolean by mutableStateOf(false)
    var balances by mutableStateOf(listOf<BalanceModel>())
    var takenLoans = mutableStateListOf<TakenLoanModel>()
    var loanAdvertisements = mutableStateListOf<Pair<Peer,AdvertiseLoanMessage>>()
    var myLoanAdvertisements = mutableStateListOf<AdvertiseLoanMessage>()
    var ipv8 = IPv8Android.getInstance()
    val loanCommunity : LoanCommunity
        get() = ipv8.getOverlay()!!

    val trustChainCommunity : TrustChainCommunity
        get() = ipv8.getOverlay()!!

    init {
        viewModelScope.launch(Dispatchers.IO) {
            wallet = Wallet()
            reloadAsync().await()
            walletInitDone = true
        }
        loanCommunity.setOnLoanReceived { peer, advertiseLoanMessage ->
            viewModelScope.launch(Dispatchers.Default){
                loanAdvertisements.add(peer to advertiseLoanMessage)
            }
        }

        loanCommunity.setOnAcceptLoanReceived{ peer, acceptLoan, raw_payload ->
            viewModelScope.launch(Dispatchers.Default){
                val loan = myLoanAdvertisements.first {  loanadvert ->
                    loanadvert.id == acceptLoan.id
                }
                myLoanAdvertisements.removeIf { loanadvert ->
                    loanadvert.id == acceptLoan.id
                }
                trustChainCommunity
                    .createProposalBlock("loan", mapOf("accepted" to raw_payload.map(Byte::toInt), "terms" to loan.serialize().toString(Charsets.UTF_8)),ipv8.myPeer.key.pub().keyToBin())
                wallet.sendTo(loan.amount.toString(),acceptLoan.accepterAddress)
                reloadAsync().await()
            }
        }
    }

    fun acceptLoan(id: String){
        val (peer, loan) = loanAdvertisements.first { it.second.id == id }
        loanCommunity.acceptLoan(peer, id,wallet.wallet.classicAddress().toString())
        takenLoans.add(TakenLoanModel(loan.advertiserXrpAddress.take(5),loan.amount.toString(),balances.first().asset))
        viewModelScope.launch(Dispatchers.Default) {

            loanAdvertisements.removeIf { (_, loanadvert) ->
                loanadvert.id == id
            }
            val oldBalance = balances.first().amount
                while (true){
                    reloadAsync().await()
                    if (balances.first().amount != oldBalance){
                        break
                    }
                }
        }

    }

    fun advertiseLoan(amount : Double, termDays: Int, totalInterest: Double){
        val id = loanCommunity.advertiseLoan(wallet.wallet.classicAddress().toString(),amount, termDays, totalInterest)
        myLoanAdvertisements.add(AdvertiseLoanMessage(id.toString(),"", amount ,termDays, totalInterest))
    }

    suspend fun reloadAsync() = viewModelScope.async{
        wallet.balance()?.let {
            balances = listOf(it)
        }
    }

}