<p align="center">
  <a href="https://kryptokrauts.com">
    <img alt="kryptokrauts" src="https://kryptokrauts.com/img/logo.svg" width="60" />
  </a>
</p>
<h1 align="center">
  kryptokrauts.com
</h1>

This project showcases how to use the [aepp-sdk-java](https://github.com/kryptokrauts/aepp-sdk-java) along with the [contraect-maven-plugin](https://github.com/kryptokrauts/contraect-maven-plugin) in order
to interact with Smart Contracts on the [æternity blockchain](https://aeternity.com).

## Requirements
- Docker
- Java 15

## Local environment
Make sure to run the local docker setup before compiling and running the tests by spinning up a local environment:

- `docker-compose up -d`

This command will spin up a local node and a local Sophia http compiler which are both needed in the setup of this repository.

## Generate the Java classes
Run `mvn contraect:generate-contraects` to generate all the Java classes
that are used in the tests to deploy and interact with smart contracts on the æternity blockchain.

After that you can execute each test in your favorite IDE like you prefer it.

## Contracts and tests
The **contracts** are all located under `resources/contraects`.

The **generated classes** are all located under `target/generated-sources/contraect`.

The **tests** are all located under `src/test/java/com/kryptokrauts`.

## Highlights
All the contracts and tests are good examples to get started but following tests are definitely noteworthy:

- [AENSDelegationTest](src/test/java/com/kryptokrauts/AENSDelegationTest.java)
    - showcases how to delegate [AENS](https://aeternity.com/protocol/AENS.html) control to a smart contract
    - can potentially be used to create an AENS marketplace
- [FungibleTokenTest](src/test/java/com/kryptokrauts/FungibleTokenTest.java)
    - shows how to deploy and use an [AEX-9](https://github.com/aeternity/AEXs/blob/master/AEXS/aex-9.md) compliant fungible token
- [GAMultiSigTest](src/test/java/com/kryptokrauts/GAMultiSigTest.java)
    - showcases the [Generalized Accounts](https://aeternity.com/protocol/generalized_accounts/ga_explained.html) feature with a simple MultiSig account
- [OracleDelegation](src/test/java/com/kryptokrauts/OracleDelegationTest.java)
    - showcases how to delegate [Oracle](https://aeternity.com/protocol/oracles/index.html) control to a smart contract
    
## Support us

If you like this project we would appreciate your support. You can find multiple ways to support us here:

- https://kryptokrauts.com/support