# Instructions

## Prerequisites

- Java >= 1.8.0_201
- Apache Maven >= 3.3.9
- Docker >= 18.09.2
- Docker Compose >= 1.23.2

- Algorand sandbox (or equivalent service) [optional]
- Purestack account [optional]

## To test the project: 

1. clone the github repo in a folder of your choice:

`git clone https://github.com/david-ciamberlano/aa-notarization.git`

2. Create a free account on Purestack (https://developer.purestake.io/) and get you **api-key** 
(in alternative you can create your own Algorand node. See **Appendix**)

3. edit the alfresco-global.property file:

`alf-algo-platform/src/main/resources/alfresco/module/alf-algo-platform/alfresco-global.properties`

change conveniently the properties:
```
algorand.explorer.url=https://testnet.algoexplorer.io/tx/
algorand.account.passfrase=<24 worlds of your wallet>
algorand.account.address=<account address>
        
algorand.api.address=https://testnet-algorand.api.purestake.io/ps2
algorand.api.port=443
algorand.api.indexer=https://testnet-algorand.api.purestake.io/idx2
algorand.indexer.port=443

algorand.api.token=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
purestack.api.key=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
```

(You can obtain a wallet at https://www.algorand.com/wallet or https://wallet.myalgo.com/)  

4. Run Alfresco with `./run.sh build_start` or `./run.bat build_start` and verify that it

 * Runs Alfresco Content Service (ACS)
 * Runs Alfresco Share
 * Runs Alfresco Search Service (ASS)
 * Runs PostgreSQL database
 * Deploys the JAR assembled modules
 
All the services of the project are now run as docker containers. 

6. open the Alfresco Share UI
`localhost:8180/share` 

*username*: admin
*password*: admin

# Demo
For a demo please watch the video: "Documentation/Alfresco-Algorand-notarization-demo.mp4"

# The project
This add-on extends Alfresco’s capabilities with a notarization functionality. Notarization serves to prove documents existed at a certain time and they have not been modified afterwards.  
 
## Why Algorand
At the moment the only way to do that in an ECM is using a digital signature and a legal timestamp. However these services are expensive and not easy to implement and also you are required to use an external certification authority.
Many attempts have been made in recent years to use blockchain technology to achieve the same results. The most popular blockchains (Bitcoin and Ethereum) offer many advantages but also have some weaknesses, for example the confirmation time (that could takes minutes) or the fee required for the transaction.
 Using Algorand you can get all the advantages of the blockchain, without experiencing the weaknesses, since transactions are actually confirmed in less than 5 seconds and require a very low fee.
 
## Description
Documents can be any type of digital file (MSOffice docs, texts, images, videos, pdf, xml, …). Any document stored in Alfresco can be notarized, manually (with a single click) or in a batch process. It’s also possible to check if a previously notarized document is still valid (i.e.: it has not been modified after the notarization).

On the technical side, this extension computes the hash (sha256) of a document stored in Alfresco, builds a json object with the hash and other related metadata and creates a transaction in the Algorand blockchain. The json object is stored in the note field of the transaction and a new set of metadata related to that transaction (document hash, block id, transaction id, transaction time, account address) is associated with the document in Alfresco.

To verify the validity of a notarized document, a similar process is executed: the plugin it st

The project uses the Algorand Java SDK and the REST API v2.
It works on Alfresco Community 6.x and the Enterprise edition 6.x

The relevant code (regarding Algorand) is in the following java methods:
- src/main/java/it/davidlab/algorand/actions/notarizationActionExecuter.java
- src/main/java/it/davidlab/algorand/actions/notarizationCheckActionExecuter.java



## Appendix

### Run your own Algorand node

1. Run the algorand sandbox container (https://developer.algorand.org/docs/build-apps/setup/#2-use-docker-sandbox)

`./sandbox up`

2. link the sandbox container to the Alfresco network

`docker network connect docker_default sandbox`

and take note of the ip address of the sandbox container

`docker network inspect docker_default`

e.g. `name: sandbox [...] "IPv4Address": "172.17.0.2/16"`

if this ip address is different from the one you used in `algorand.api.address` you must replace it and reload acs

`./run.sh reload_acs`
 
