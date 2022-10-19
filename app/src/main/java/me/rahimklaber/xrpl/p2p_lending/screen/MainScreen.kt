package me.rahimklaber.xrpl.p2p_lending.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.rahimklaber.xrpl.p2p_lending.AdvertiseLoanMessage
import me.rahimklaber.xrpl.p2p_lending.WalletViewModel
import me.rahimklaber.xrpl.p2p_lending.balanceModels
import me.rahimklaber.xrpl.p2p_lending.model.BalanceModel
import me.rahimklaber.xrpl.p2p_lending.model.GiverLoanModel
import me.rahimklaber.xrpl.p2p_lending.model.TakenLoanModel
import okhttp3.Dispatcher

@Composable
fun MainScreen(navController: NavController,viewModel : WalletViewModel){
    if (!viewModel.isNicknameSet){
       Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally){
           var nickname by remember{ mutableStateOf("")}
           OutlinedTextField(
               value = nickname,
               onValueChange = { x: String ->
                   nickname = x
               },
               label = {
                   Text(
                       text = "nickname"
                   )
               })
           Button(onClick = {
                viewModel.viewModelScope.launch(Dispatchers.Default) {
                    viewModel.isNicknameSet = true
                    viewModel.setNickName(nickname)
                }
           }) {
               Text(text = "Set nickname")
           }
       }
    }else{
        LazyColumn(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally){
            if(viewModel.walletInitDone){
                item {
                    Button(onClick = { navController.navigate("create_loan")}) {
                        Text("Create Loan")
                    }
                }
                balances(viewModel.balances)
                takenLoans(viewModel.takenLoans)
                givenLoans(viewModel.givenLoans)
                advertisedLoans(viewModel.myLoanAdvertisements)
            }else{
                item{
                    CircularProgressIndicator(Modifier)
                }
            }

        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
//@Composable
fun LazyListScope.takenLoans(loans: List<TakenLoanModel>) {
    stickyHeader {
        Box(
            Modifier
                .background(MaterialTheme.colors.background)
                .border(1.dp, Color(0xFFB3B095))
                .fillMaxWidth()
        ) {
            Text(text = "Taken Loans", Modifier.padding(5.dp), fontSize = 22.sp)
        }
    }
    itemsIndexed(loans) { index, model ->
        TakenLoanItem(model)

        if (index < loans.lastIndex)
            Divider(color = Color(0xFFB3B095), thickness = 1.dp)
    }
}
@OptIn(ExperimentalFoundationApi::class)
//@Composable
fun LazyListScope.advertisedLoans(loans: List<AdvertiseLoanMessage>) {
    stickyHeader {
        Box(
            Modifier
                .background(MaterialTheme.colors.background)
                .border(1.dp, Color(0xFFB3B095))
                .fillMaxWidth()
        ) {
            Text(text = "My Advertised loans", Modifier.padding(5.dp), fontSize = 22.sp)
        }
    }
    itemsIndexed(loans) { index, model ->
        AdvertiseLoanItem(model)

        if (index < loans.lastIndex)
            Divider(color = Color(0xFFB3B095), thickness = 1.dp)
    }
}

@Composable
fun AdvertiseLoanItem(model: AdvertiseLoanMessage) =
    Box {
        Row(Modifier.padding(2.dp)) {
            Image(
                painterResource(me.rahimklaber.xrpl.p2p_lending.R.drawable.usd),
                null,
                modifier = Modifier.size(50.dp)
            )
            Column {
                // TODO: do this better
                Text("currency : ${"CBDC"}", Modifier.padding(start= 5.dp,top = 5.dp))
                Text("interest : ${model.totalInterest}", Modifier.padding(start= 5.dp,top = 1.dp))
            }
            Box(modifier = Modifier.fillMaxWidth(), Alignment.CenterEnd) {
                Text(model.amount.toString(),Modifier.padding(5.dp))
            }
        }
    }
@OptIn(ExperimentalFoundationApi::class)
//@Composable
fun LazyListScope.givenLoans(loans: List<GiverLoanModel>) {
    stickyHeader {
        Box(
            Modifier
                .background(MaterialTheme.colors.background)
                .border(1.dp, Color(0xFFB3B095))
                .fillMaxWidth()
        ) {
            Text(text = "Given Loans", Modifier.padding(5.dp), fontSize = 22.sp)
        }
    }
    itemsIndexed(loans) { index, model ->
        GivenLoanItem(model)

        if (index < loans.lastIndex)
            Divider(color = Color(0xFFB3B095), thickness = 1.dp)
    }
}

@Composable
fun GivenLoanItem(model: GiverLoanModel) =
    Box {
        Row(Modifier.padding(2.dp)) {
            Image(
                painterResource(me.rahimklaber.xrpl.p2p_lending.R.drawable.usd),
                null,
                modifier = Modifier.size(50.dp)
            )
            Column {
                Text("currency : ${model.asset.name}", Modifier.padding(start= 5.dp,top = 5.dp))
                Text("to : ${model.to}", Modifier.padding(start= 5.dp,top = 1.dp))
            }
            Box(modifier = Modifier.fillMaxWidth(), Alignment.CenterEnd) {
                Text(model.amount,Modifier.padding(5.dp))
            }
        }
    }

@Composable
fun TakenLoanItem(model: TakenLoanModel) =
    Box {
        Row(Modifier.padding(2.dp)) {
            Image(
                painterResource(me.rahimklaber.xrpl.p2p_lending.R.drawable.usd),
                null,
                modifier = Modifier.size(50.dp)
            )
            Column {
                Text("currency : ${model.asset.name}", Modifier.padding(start= 5.dp,top = 5.dp))
                Text("from : ${model.from}", Modifier.padding(start= 5.dp,top = 1.dp))
            }
            Box(modifier = Modifier.fillMaxWidth(), Alignment.CenterEnd) {
                Text(model.amount,Modifier.padding(5.dp))
            }
        }
    }


@OptIn(ExperimentalFoundationApi::class)
//@Composable
fun LazyListScope.balances(balances: List<BalanceModel>) {
    stickyHeader {
        Box(
            Modifier
                .background(MaterialTheme.colors.background)
                .border(1.dp, Color(0xFFB3B095))
                .fillMaxWidth()
        ) {
            Text(text = "Balances", Modifier.padding(5.dp), fontSize = 22.sp)
        }
    }
    itemsIndexed(balances) { index, model ->
        BalanceItem(model)

        if (index < balances.lastIndex)
            Divider(color = Color(0xFFB3B095), thickness = 1.dp)
    }
}

@Composable
fun BalanceItem(model: BalanceModel) =
    Box {
        Row(Modifier.padding(2.dp)) {
            Image(
                painterResource(me.rahimklaber.xrpl.p2p_lending.R.drawable.usd),
                null,
                modifier = Modifier.size(50.dp)
            )
            Text(model.asset.name, Modifier.padding(5.dp))
            Box(modifier = Modifier.fillMaxWidth(), Alignment.CenterEnd) {
                Text(model.amount,Modifier.padding(5.dp))
            }
        }
    }

@Preview
@Composable
fun Preview() {
    BalanceItem(model = balanceModels.first())
}