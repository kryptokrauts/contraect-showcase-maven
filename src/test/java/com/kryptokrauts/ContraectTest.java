package com.kryptokrauts;

import com.kryptokrauts.aeternity.sdk.constants.Network;
import com.kryptokrauts.aeternity.sdk.constants.VirtualMachine;
import com.kryptokrauts.aeternity.sdk.domain.secret.impl.BaseKeyPair;
import com.kryptokrauts.aeternity.sdk.service.account.domain.AccountResult;
import com.kryptokrauts.aeternity.sdk.service.aeternity.AeternityServiceConfiguration;
import com.kryptokrauts.aeternity.sdk.service.aeternity.AeternityServiceFactory;
import com.kryptokrauts.aeternity.sdk.service.aeternity.impl.AeternityService;
import com.kryptokrauts.aeternity.sdk.service.keypair.KeyPairService;
import com.kryptokrauts.aeternity.sdk.service.keypair.KeyPairServiceFactory;
import com.kryptokrauts.aeternity.sdk.service.unit.UnitConversionService;
import com.kryptokrauts.aeternity.sdk.service.unit.impl.DefaultUnitConversionServiceImpl;
import com.kryptokrauts.contraect.generated.CryptoHamster;
import com.kryptokrauts.contraect.generated.FungibleToken;
import com.kryptokrauts.contraect.generated.FungibleToken.Meta_info;
import com.kryptokrauts.contraect.generated.PaymentSplitter;
import com.kryptokrauts.contraect.generated.PaymentSplitter.Address;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.javatuples.Pair;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@Slf4j
public class ContraectTest {

  private static final String PRIVATE_KEY =
      "79816BBF860B95600DDFABF9D81FEE81BDB30BE823B17D80B9E48BE0A7015ADF";

  private static final String HAMSTER_NAME = "kryptokrauts";

  private static final String HAMSTER_DNA =
      "#9227ef8e61954f009822f1d1b9b4077f95ab81be96f21b37937d1f85effe261b";

  private static UnitConversionService unitConversionService18Decimals =
      new DefaultUnitConversionServiceImpl();

  private static final BigDecimal KRAUT_TOKEN_TOTAL_SUPPLY = new BigDecimal("21000000");

  private static BaseKeyPair baseKeyPair;

  private static AeternityServiceConfiguration config;

  private static AeternityService aeternityService;

  private static KeyPairService keyPairService = new KeyPairServiceFactory().getService();

  @BeforeAll
  public static void init() {
    KeyPairService keyPairService = new KeyPairServiceFactory().getService();
    baseKeyPair = keyPairService.generateBaseKeyPairFromSecret(PRIVATE_KEY);
    config =
        AeternityServiceConfiguration.configure()
            .compilerBaseUrl("http://localhost:3080")
            .baseUrl("http://localhost")
            .baseKeyPair(baseKeyPair)
            .network(Network.DEVNET)
            .targetVM(VirtualMachine.FATE)
            .millisBetweenTrailsToWaitForConfirmation(100l)
            .compile();
    aeternityService = new AeternityServiceFactory().getService(config);
  }

  @Test
  public void cryptoHamsterTest() {
    CryptoHamster cryptoHamsterInstance = new CryptoHamster(config, null);

    // expecting error as contract doesn't exists and no contractId provided
    Assertions.assertThrows(Exception.class, () -> cryptoHamsterInstance.nameExists(HAMSTER_NAME));

    // deploy contract
    Pair<String, String> deployment = cryptoHamsterInstance.deploy();
    String contractId = deployment.getValue0();
    String txHash = deployment.getValue1();
    log.info("contract id: {}", contractId);
    log.info("tx-hash of deployment: {}", txHash);
    Assertions.assertNotNull(contractId);

    // expect hamster to not exist
    Assertions.assertFalse(cryptoHamsterInstance.nameExists(HAMSTER_NAME));

    // create hamster
    cryptoHamsterInstance.createHamster(HAMSTER_NAME);
    Assertions.assertTrue(cryptoHamsterInstance.nameExists(HAMSTER_NAME));

    // verify hamster dna
    String hamsterDna = cryptoHamsterInstance.getHamsterDNA(HAMSTER_NAME).toString();
    log.info("hamster dna: {}", hamsterDna);
    Assertions.assertEquals(HAMSTER_DNA, hamsterDna);
  }

  @Test
  public void fungibleTokenTest() {
    FungibleToken krautTokenInstance = new FungibleToken(config, null);
    BigDecimal tokensToSendAE = new BigDecimal("1.337");
    Pair<String, String> deployment =
        krautTokenInstance.deploy("kryptokrauts", BigInteger.valueOf(18), "KRAUT");
    String contractId = deployment.getValue0();
    String txHash = deployment.getValue1();
    log.info("contract id: {}", contractId);
    log.info("tx-hash of deployment: {}", txHash);

    // verify total token supply
    Assertions.assertEquals(
        KRAUT_TOKEN_TOTAL_SUPPLY,
        unitConversionService18Decimals.toBiggestUnit(krautTokenInstance.total_supply()));

    // get and verify Meta_info
    FungibleToken.Meta_info krautTokenMetaInfo = krautTokenInstance.meta_info();
    Assertions.assertEquals(
        new Meta_info("kryptokrauts", "KRAUT", BigInteger.valueOf(18)), krautTokenMetaInfo);

    // verify that owner owns all tokens
    BigInteger ownerBalance =
        krautTokenInstance.balance(new FungibleToken.Address(baseKeyPair.getPublicKey())).get();
    Assertions.assertEquals(
        KRAUT_TOKEN_TOTAL_SUPPLY, unitConversionService18Decimals.toBiggestUnit(ownerBalance));

    // send 1.337 KRAUT to new account
    BaseKeyPair recipientKeyPair = keyPairService.generateBaseKeyPair();
    BigInteger tokensToSend = unitConversionService18Decimals.toSmallestUnit(tokensToSendAE);
    krautTokenInstance.transfer(
        new FungibleToken.Address(recipientKeyPair.getPublicKey()), tokensToSend);

    // verify new balance of recipient
    BigInteger recipientBalance =
        krautTokenInstance
            .balance(new FungibleToken.Address(recipientKeyPair.getPublicKey()))
            .get();
    Assertions.assertEquals(tokensToSend, recipientBalance);

    log.info(krautTokenInstance.balances().toString());

    // verify new balance of owner
    ownerBalance =
        krautTokenInstance.balance(new FungibleToken.Address(baseKeyPair.getPublicKey())).get();
    log.info(ownerBalance.toString());
    Assertions.assertEquals(
        KRAUT_TOKEN_TOTAL_SUPPLY.subtract(tokensToSendAE),
        unitConversionService18Decimals.toBiggestUnit(ownerBalance));

    // verify that a fresh account doesn't own a token
    Optional<BigInteger> noKRAUTlerBalance =
        krautTokenInstance.balance(
            new FungibleToken.Address(keyPairService.generateBaseKeyPair().getPublicKey()));
    log.info(noKRAUTlerBalance.toString());
    Assertions.assertEquals(Optional.empty().toString(), noKRAUTlerBalance.toString());
  }

  @Test
  public void paymentSplitterTest() {
    PaymentSplitter paymentSplitterInstance = new PaymentSplitter(config, null);
    BaseKeyPair initialRecipient1 = new KeyPairServiceFactory().getService().generateBaseKeyPair();
    BaseKeyPair initialRecipient2 = new KeyPairServiceFactory().getService().generateBaseKeyPair();
    BaseKeyPair initialRecipient3 = new KeyPairServiceFactory().getService().generateBaseKeyPair();
    Map<Address, BigInteger> recipientConditions = new HashMap<>();
    recipientConditions.put(new Address(initialRecipient1.getPublicKey()), BigInteger.valueOf(60));
    recipientConditions.put(new Address(initialRecipient2.getPublicKey()), BigInteger.valueOf(30));
    recipientConditions.put(new Address(initialRecipient3.getPublicKey()), BigInteger.valueOf(10));
    Pair<String, String> deployment = paymentSplitterInstance.deploy(recipientConditions);
    String contractId = deployment.getValue0();
    String txHash = deployment.getValue1();
    log.info("contract id: {}", contractId);
    log.info("tx-hash of deployment: {}", txHash);

    // should fail when providing amount of 0 AE to the payAndSplit method
    try {
      paymentSplitterInstance.payAndSplit(BigInteger.ZERO);
    } catch (Exception e) {
      Assertions.assertTrue(e.getMessage().contains("contract didn't receive any payment"));
    }
    // payAndSplit 10 AE
    BigInteger amountAettos = unitConversionService18Decimals.toSmallestUnit("10");
    paymentSplitterInstance.payAndSplit(amountAettos);

    // verify a total amount of 10 AE has been splitted
    BigInteger totalAmountSplittedAettos = paymentSplitterInstance.getTotalAmountSplitted();
    Assertions.assertEquals(amountAettos, totalAmountSplittedAettos);

    // verify accounts got the correct amount of AE
    AccountResult accountResult1 =
        aeternityService.accounts.blockingGetAccount(Optional.of(initialRecipient1.getPublicKey()));
    AccountResult accountResult2 =
        aeternityService.accounts.blockingGetAccount(Optional.of(initialRecipient2.getPublicKey()));
    AccountResult accountResult3 =
        aeternityService.accounts.blockingGetAccount(Optional.of(initialRecipient3.getPublicKey()));
    Assertions.assertEquals(
        unitConversionService18Decimals.toSmallestUnit("6"), accountResult1.getBalance());
    Assertions.assertEquals(
        unitConversionService18Decimals.toSmallestUnit("3"), accountResult2.getBalance());
    Assertions.assertEquals(
        unitConversionService18Decimals.toSmallestUnit("1"), accountResult3.getBalance());
  }
}
