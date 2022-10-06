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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.rahimklaber.xrpl.p2p_lending.model.BalanceModel
import me.rahimklaber.xrpl.p2p_lending.model.TakenLoanModel
import me.rahimklaber.xrpl.p2p_lending.state.Wallet
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.android.IPv8Android
import nl.tudelft.ipv8.attestation.trustchain.BlockListener
import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
import nl.tudelft.ipv8.attestation.trustchain.TrustChainCommunity
import nl.tudelft.ipv8.util.hexToBytes
import nl.tudelft.ipv8.util.toHex
import okio.ByteString.Companion.decodeHex
import org.xrpl.xrpl4j.model.transactions.Address

typealias LoanBlock = TrustChainBlock

val LoanBlock.loanGiverXrpAddress
    get() = transaction["loan_giver_xrp_address"]!! as String
val LoanBlock.loanTakerXrpAddress
    get() = transaction["loan_taker_xrp_address"]!! as String

class WalletViewModel : ViewModel() {
    private lateinit var wallet: Wallet
    var walletInitDone: Boolean by mutableStateOf(false)
    var balances by mutableStateOf(listOf<BalanceModel>())
    var takenLoans = mutableStateListOf<TakenLoanModel>()
    var loanAdvertisements = mutableStateListOf<Pair<Peer, AdvertiseLoanMessage>>()
    var myLoanAdvertisements = mutableStateListOf<AdvertiseLoanMessage>()
    var ipv8 = IPv8Android.getInstance()
    val loanCommunity: LoanCommunity
        get() = ipv8.getOverlay()!!

    val trustChainCommunity: TrustChainCommunity
        get() = ipv8.getOverlay()!!

    init {
        viewModelScope.launch(Dispatchers.IO) {
            wallet = Wallet()
            reloadAsync().await()
            walletInitDone = true
        }


        loanCommunity.setOnLoanReceived { peer, advertiseLoanMessage ->

            viewModelScope.launch(Dispatchers.Default) {
                loanAdvertisements.add(peer to advertiseLoanMessage)
            }
        }

        trustChainCommunity.addListener("loan", object : BlockListener {
            override fun onBlockReceived(block: TrustChainBlock) {
                if (block.isProposal && block.publicKey.contentEquals(
                        ipv8.myPeer.key.pub().keyToBin()
                    )
                ) {
                    trustChainCommunity.createAgreementBlock(block, mapOf<String, Int>())
                }
            }
        })

        loanCommunity.onNewPeer { peer ->
            viewModelScope.launch(Dispatchers.Default) {
                trustChainCommunity.crawlChain(peer)
            }
        }

        loanCommunity.setOnAcceptLoanReceived { peer, acceptLoan, raw_payload ->
            viewModelScope.launch(Dispatchers.Default) {
                val loan = myLoanAdvertisements.first { loanadvert ->
                    loanadvert.id == acceptLoan.id
                }
                myLoanAdvertisements.removeIf { loanadvert ->
                    loanadvert.id == acceptLoan.id
                }
                val proposalBlock = trustChainCommunity
                    .createProposalBlock(
                        LoanBlocks.LOAN, mapOf(
                            "loan_giver_xrp_address" to acceptLoan.accepterAddress,
                            "loan_taker_xrp_address" to wallet.wallet.classicAddress().toString(),
                            "amount" to loan.amount.toString(),
                            "term_days" to loan.termDays.toString(),
                            "total_interest_percent" to loan.totalInterest.toString()
                        ), peer.key.pub().keyToBin()
                    )
                //todo, when sending funds to the person taking the loan, there should be a reference to this block
                trustChainCommunity.sendBlock(proposalBlock, peer, 10)
                wallet.sendTo(
                    loan.amount.toString(),
                    acceptLoan.accepterAddress,
                    proposalBlock.calculateHash().toHex()
                )
                reloadAsync().await()
            }
        }
    }

    fun getReputationOfPeer(peer: Peer) : Int{
        val blocks =
            // for now, assume these blocks have a corresponding agreement block
            trustChainCommunity.database.getMutualBlocks(peer.key.pub().keyToBin(), 500)
                .filter{it.type==LoanBlocks.LOAN}
                .filter(TrustChainBlock::isProposal)
        if(blocks.isEmpty())
            return 0

        val takenLoans = mutableSetOf<String>()

        val repaidLoans = mutableSetOf<String>()


        blocks.first().let { block ->
            wallet.client.accountTransactions(Address.of(block.loanTakerXrpAddress))
                .transactions()
                .forEach { tx ->

                    val memo = tx.resultTransaction().transaction().memos().firstOrNull() ?: return@forEach
                    //todo add constants somewhere
                    val decodedMemoType = memo.memo().memoType().orElseGet { "" }.hexToBytes().decodeToString()

                    val decodedMemo = memo.memo().memoData().orElseGet{""}

                    if (decodedMemoType !in setOf("trustchain_loan", "trustchain_loan_repayment"))
                        return@forEach

                    Log.d("P2P_DEBUG", "getReputationOfPeer: found xrp tx with hash : ${tx.resultTransaction().hash()}, that was a part of a loan ")
                    when(decodedMemoType){
                        "trustchain_loan" -> takenLoans.add(decodedMemo)
                        "trustchain_loan_repayment" -> repaidLoans.add(decodedMemo)
                        else -> Log.d("P2P_DEBUG", "getReputationOfPeer: found xrp tx with unknown memotype : $decodedMemoType ")

                    }

                }

            return takenLoans.fold(0){acc,hash ->
                acc + if (hash in repaidLoans){
                    1
                }else {
                    -1
                }
            }

        }


    }

    fun acceptLoan(id: String) {
        val (peer, loan) = loanAdvertisements.first { it.second.id == id }
        loanCommunity.acceptLoan(peer, id, wallet.wallet.classicAddress().toString())
        takenLoans.add(
            TakenLoanModel(
                loan.advertiserXrpAddress.take(5),
                loan.amount.toString(),
                balances.first().asset
            )
        )
        viewModelScope.launch(Dispatchers.Default) {

            loanAdvertisements.removeIf { (_, loanadvert) ->
                loanadvert.id == id
            }
            val oldBalance = balances.first().amount
            for (i in 0..10) { //maybe do this automaticcally. So ocasionally check for balance
                reloadAsync().await()
                delay(4000)
                if (balances.first().amount != oldBalance) {
                    break
                }
            }
        }

    }

    fun advertiseLoan(amount: Double, termDays: Int, totalInterest: Double) {
        val id = loanCommunity.advertiseLoan(
            wallet.wallet.classicAddress().toString(),
            amount,
            termDays,
            totalInterest
        )
        myLoanAdvertisements.add(
            AdvertiseLoanMessage(
                id.toString(),
                "",
                amount,
                termDays,
                totalInterest
            )
        )
    }

    suspend fun reloadAsync() = viewModelScope.async {
        wallet.balance()?.let {
            balances = listOf(it)
        }
    }

}