package me.rahimklaber.xrpl.p2p_lending

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import nl.tudelft.ipv8.IPv4Address
import nl.tudelft.ipv8.Community
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.messaging.*
import nl.tudelft.ipv8.messaging.payload.IntroductionResponsePayload
import java.util.*


object LoanBlocks{
    const val LOAN = "LOAN"
}

class AdvertiseLoanMessage(val id : String,val advertiserXrpAddress: String,val amount : Double, val termDays : Int, val totalInterest : Double) : Serializable {
    override fun serialize(): ByteArray {
        return "$id#$advertiserXrpAddress#$amount#$termDays#$totalInterest".toByteArray()
    }

    companion object Deserializer : Deserializable<AdvertiseLoanMessage> {
        const val MESSAGE_ID = 1
        override fun deserialize(buffer: ByteArray, offset: Int): Pair<AdvertiseLoanMessage, Int> {
            val (id,advertiserXrpAddress,amount, term, interest) = buffer.toList().subList(offset,buffer.size).toByteArray().toString(Charsets.UTF_8).split("#")
            return Pair(AdvertiseLoanMessage(id,advertiserXrpAddress,amount.toDouble(),term.toInt(),interest.toDouble()), buffer.size)
        }
    }
}

class AcceptLoanMessage(val id: String, val accepterAddress: String) : Serializable{
    override fun serialize(): ByteArray {
        return "$id#$accepterAddress".toByteArray()
    }

    companion object Deserializer : Deserializable<AcceptLoanMessage> {
        const val MESSAGE_ID = 2
        override fun deserialize(buffer: ByteArray, offset: Int): Pair<AcceptLoanMessage, Int> {
            val (id,address) = buffer.toList().subList(offset,buffer.size).toByteArray().toString(Charsets.UTF_8).split("#")
            return Pair(AcceptLoanMessage(id, address), buffer.size)
        }
    }

}

class LoanCommunity : Community() {
    override val serviceId = "5ce0ffb9123b60537030b1312783a0ebcf5fd92f"

    val discoveredAddressesContacted: MutableMap<IPv4Address, Date> = mutableMapOf()
    val lastTrackerResponses = mutableMapOf<IPv4Address, Date>()
    val crawling = mutableSetOf<String>()
    override fun walkTo(address: IPv4Address) {
        super.walkTo(address)

        discoveredAddressesContacted[address] = Date()
    }
    var onNewPeerCb : ((Peer) -> Unit)? = null

    fun onNewPeer(cb : (peer: Peer)->Unit){
        onNewPeerCb = cb
    }

    override fun onIntroductionResponse(peer: Peer, payload: IntroductionResponsePayload) {
        super.onIntroductionResponse(peer, payload)
        if (peer.mid !in crawling){
            crawling.add(peer.mid)
            onNewPeerCb?.invoke(peer)
        }
        if (peer.address in DEFAULT_ADDRESSES) {
            lastTrackerResponses[peer.address] = Date()
        }
    }

    fun setOnLoanReceived(f : (Peer,AdvertiseLoanMessage) -> Unit){
        messageHandlers[AdvertiseLoanMessage.MESSAGE_ID] = {packet ->
            Log.d("loancommunity","received loan advertisement")
            val (peer, payload) = packet.getAuthPayload(AdvertiseLoanMessage.Deserializer)
            f(peer,payload)
        }
    }

    fun setOnAcceptLoanReceived(f : (Peer,AcceptLoanMessage, ByteArray) -> Unit){
        messageHandlers[AcceptLoanMessage.MESSAGE_ID] = {packet ->
            val (peer, payload) = packet.getAuthPayload(AcceptLoanMessage.Deserializer)
            if (peer.mid !in crawling){
                crawling.add(peer.mid)
                onNewPeerCb?.invoke(peer)
            }
            f(peer,payload,packet.data/*raw payload*/)
        }
    }

    fun acceptLoan(peer: Peer,id:String, address: String){
        scope.launch {
            send(peer,serializePacket(AcceptLoanMessage.MESSAGE_ID,AcceptLoanMessage(id,address)))
        }
    }

    fun advertiseLoan(xrpAddress : String, amount : Double, termDays: Int, totalInterest: Double): Long {
        val id = kotlin.random.Random.nextLong()
        val msg = AdvertiseLoanMessage(id.toString(),xrpAddress, amount,termDays, totalInterest)
        val packet = serializePacket(AdvertiseLoanMessage.MESSAGE_ID,msg)
        scope.launch {
            for (peer in getPeers()){
                send(peer,packet)
            }
        }
        return id
    }
}