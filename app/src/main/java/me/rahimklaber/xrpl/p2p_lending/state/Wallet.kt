package me.rahimklaber.xrpl.p2p_lending.state

import android.util.Log
import com.google.common.primitives.UnsignedInteger
import com.google.common.primitives.UnsignedLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import me.rahimklaber.xrpl.p2p_lending.model.AssetModel
import me.rahimklaber.xrpl.p2p_lending.model.BalanceModel
import nl.tudelft.ipv8.util.toHex
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.xrpl.xrpl4j.client.JsonRpcClientErrorException
import org.xrpl.xrpl4j.client.XrplClient
import org.xrpl.xrpl4j.client.faucet.FaucetClient
import org.xrpl.xrpl4j.client.faucet.FundAccountRequest
import org.xrpl.xrpl4j.model.client.accounts.AccountInfoRequestParams
import org.xrpl.xrpl4j.model.client.accounts.AccountLinesRequestParams
import org.xrpl.xrpl4j.model.client.common.LedgerIndex
import org.xrpl.xrpl4j.model.flags.Flags
import org.xrpl.xrpl4j.model.transactions.*
import org.xrpl.xrpl4j.wallet.DefaultWalletFactory
import org.xrpl.xrpl4j.wallet.Wallet


class Wallet {
    val wallet: Wallet
    val client: XrplClient

    init {
        // Construct a network client
        val rippledUrl = "https://s.altnet.rippletest.net:51234/".toHttpUrl()
        client = XrplClient(rippledUrl)

        val walletFactory = DefaultWalletFactory.getInstance()
        wallet = walletFactory.randomWallet(true).wallet()

        val faucetClient = FaucetClient
            .construct("https://faucet.altnet.rippletest.net".toHttpUrl())
        faucetClient.fundAccount(FundAccountRequest.of(wallet.classicAddress()))
        Thread.sleep(6000)

        val sequence = runBlocking { sequence() }

        val trustSet: ImmutableTrustSet = TrustSet.builder()
            .account(wallet.classicAddress())
            .fee(XrpCurrencyAmount.ofDrops(10000000))
            .sequence(sequence)
            .limitAmount(
                IssuedCurrencyAmount.builder()
                    .currency("CBC")
                    .issuer(Address.of("ra2hGw4yUo8Wmvd1wcUGsuCq63fncVosoa"))
                    .value("1000000000")
                    .build()
            )
            .signingPublicKey(wallet.publicKey())
            .build()

        val resultTrust = client.submit(wallet,trustSet)
        Thread.sleep(6000)
        Log.d("P2P_DEBUG","${resultTrust}")
        Log.d("P2P_DEBUG","${resultTrust.transactionResult().hash()}")

          val offer = OfferCreate
              .builder()
              .account(wallet.classicAddress())
              .sequence(sequence.plus(UnsignedInteger.ONE))
              .takerPays(
                  IssuedCurrencyAmount.builder()
                      .currency("CBC")
                      .issuer(Address.of("ra2hGw4yUo8Wmvd1wcUGsuCq63fncVosoa"))
                      .value("500")
                      .build()
              )
              .takerGets(
                  XrpCurrencyAmount.builder()
                      .value(UnsignedLong.valueOf("500"))
                      .build())
              .fee(XrpCurrencyAmount.ofDrops(10000000))
              .signingPublicKey(wallet.publicKey())
              .build()

          val offerTx = client.signTransaction(wallet,offer)
          val offerres = client.submit(offerTx)
          Log.d("P2P_DEBUG","offer : $offerres")
    }

    private suspend fun sequence() = withContext(Dispatchers.IO){
        client.accountInfo(
            AccountInfoRequestParams.builder()
                .ledgerIndex(LedgerIndex.CURRENT)
                .account(wallet.classicAddress())
                .build()
        ).accountData().sequence()
    }

    suspend fun sendTo(amount : String, to : String, txHash: String) = withContext(Dispatchers.IO){
        val payment: Payment = Payment.builder()
            .account(wallet.classicAddress())
            .fee(XrpCurrencyAmount.ofDrops(10000000))
            .sequence(sequence())
            .destination(Address.of(to))
            .amount(IssuedCurrencyAmount.builder()
                .currency("CBC")
                .issuer(Address.of("ra2hGw4yUo8Wmvd1wcUGsuCq63fncVosoa"))
                .value(amount)
                .build())
            .signingPublicKey(wallet.publicKey())
            .addMemos(MemoWrapper.builder().memo(Memo.builder()
                .memoType("trustchain_loan".toByteArray().toHex())
                .memoData(txHash)
                .build()).build())
            .build()
        val res = client.submit(wallet,payment)
        Log.d("P2P_DEBUG","tx hash = ${res.transactionResult().hash()}")
        Log.d("P2P_DEBUG","tx hash = ${res}")

    }

    suspend fun balance(): BalanceModel? = withContext(Dispatchers.IO) {
        val response = this@Wallet.client.accountLines(
            AccountLinesRequestParams.builder().account(this@Wallet.wallet.classicAddress())
                .build()
        )
            .lines()
            // just hardcode for now. todo: add some config
            .firstOrNull { it.currency() == "CBC" }
        response?.let {
            BalanceModel(
                asset = AssetModel(
                    code = "CBDC",
                    name = "Example CBDC",
                    imageUrl = "",
                    issuer = ""
                ),
                amount = it.balance()
            )
        }
    }
}