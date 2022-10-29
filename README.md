# p2p_lending
## What it does
The idea is to allow uers to give out and take loans in a peer 2 peer manner. 
Users can advertise loans they want to give out, other users can then accept those loans and finally, the loan advertiser can choose the accept the user that want to take the loan.

Loans should be  accepted based on a few factor:
- The general reputation of the loan advertiser.
- Whether the loan advertiser is trusted by others who we trust


The app uses [ipv8](https://github.com/Tribler/kotlin-ipv8) so that the app can be used without a centralized server, and [trustchain](https://www.tudelft.nl/innovatie-impact/home-of-innovation/innovation-projects/trustchain) to record the loan agreements on a distributed ledger. In the future, trustchain can be used to easily measure the reputation of users. The loan agreements are stored on a distributed ledger, so we can check whether an agreement was broken.

I'm not sure about this, but you could potentially argue that the digital agreements can be seen as a contract, which could add a second layer of protection.

Currently, a token is used to represent a CBDC. When the app launches, the CBDC token will be acquired from the xrpl dex.

## Running the app

You can run the app by opening the project in Android Studio and running it from there.

I've only run the app on my phones for testing, so I can't be sure that the app will run on other phones. I recommend giving the app all permissions in the settings since I still need to add logic to ask for permissions.
