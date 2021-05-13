# Akka Typed Blockchain

This is an Actor-based application in Scala that runs a cryptocurrency with a decentralized blockchain ledger on distributed cluster nodes.  It simulates mining activities by individual miners to compete for adding new blocks to the blockchain by means of a consensual algorithm. 

The blockchain application involves using of hash functions, [Merkle trees](https://blog.genuine.com/2020/02/merkle-tree-implementation-in-scala/) and some basic public key cryptographic functions.  [Proof-of-Work](https://en.wikipedia.org/wiki/Proof_of_work) is adopted as the concensus algorithm.  The core functionality for operating the decentralized blockchain is implemented using [Akka Typed actors](https://doc.akka.io/docs/akka/2.6/typed/actors.html) with Distributed Publish/Subscribe on an Akka cluster.

For an overview of the application, please visit [Genuine Blog](https://blog.genuine.com/2021/05/actor-based-blockchain-in-akka-typed/).  Feature-wise, this application is identical to the old [Akka Blockchain](https://github.com/oel/akka-blockchain) application which uses the loosely typed Akka classic API.  There is a [mini blog series](https://blog.genuine.com/2021/03/from-akka-untyped-to-typed-actors/) that details how to migrate relevant features from the old API to Akka Typed.

With the default configuration, the application will launch an Akka cluster locally on a single host with two seed nodes at port *2551* and *2552*, allowing additional nodes bound to different ports to join the cluster.

The main program takes 2 arguments: a port# and a path to the miner's public-key file; and an optional 3rd argument: "test" for a quick test (as opposed to entering a mining loop):

```bash
$ sbt "runMain akkablockchain.Main port# /path/to/minerPublicKey [test]"
```

To save time for cryptographic key generation (required for user accounts) in application startup, a few public-keys (*accountX_public.pem*; *X=0,..,9*) have been created and saved under "*{project-root}/src/main/resources/keys/*".  To generate additional keys, method *generateKeyPairPemFiles()* within the included *Crypto* class can be used.

## Running akka-typed-blockchain on separate JVMs

Git-clone the repo to a local disk, open up separate shell command line terminals and launch the application from the *project-root* on separate terminals by binding them to different port#.

1. Start 1st cluster seed node at port *2551* using *account0* as 1st miner's account
```bash
$ sbt "runMain akkablockchain.Main 2551 src/main/resources/keys/account0_public.pem [test]"
```
2. Start 2nd cluster seed node at port *2552* using *account1* as 2nd miner's account
```bash
$ sbt "runMain akkablockchain.Main 2552 src/main/resources/keys/account1_public.pem [test]"
```
3. Start another cluster node at port *2553* using *account2* as 3rd miner's account
```bash
$ sbt "runMain akkablockchain.Main 2553 src/main/resources/keys/account2_public.pem [test]"
```
4. Start cluster nodes for additional miners at other ports
