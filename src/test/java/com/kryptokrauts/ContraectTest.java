package com.kryptokrauts;

import com.kryptokrauts.aeternity.sdk.constants.Network;
import com.kryptokrauts.aeternity.sdk.constants.VirtualMachine;
import com.kryptokrauts.aeternity.sdk.domain.secret.impl.BaseKeyPair;
import com.kryptokrauts.aeternity.sdk.service.aeternity.AeternityServiceConfiguration;
import com.kryptokrauts.aeternity.sdk.service.aeternity.AeternityServiceFactory;
import com.kryptokrauts.aeternity.sdk.service.aeternity.impl.AeternityService;
import com.kryptokrauts.aeternity.sdk.service.keypair.KeyPairService;
import com.kryptokrauts.aeternity.sdk.service.keypair.KeyPairServiceFactory;
import com.kryptokrauts.aeternity.sdk.util.UnitConversionUtil;
import com.kryptokrauts.aeternity.sdk.util.UnitConversionUtil.Unit;
import com.kryptokrauts.contraect.generated.CryptoHamster;
import com.kryptokrauts.contraect.generated.FungibleToken;
import com.kryptokrauts.contraect.generated.datatypes.Address;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
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

  private static final BigInteger KRAUT_TOKEN_TOTAL_SUPPLY =
      new BigInteger("21000000000000000000000000");

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
            .compile();
    aeternityService = new AeternityServiceFactory().getService(config);
  }

  @Test
  public void cryptoHamsterTest() {
    CryptoHamster cryptoHamsterInstance = new CryptoHamster(config, null);

    // expecting error as contract doesn't exists and no contractId provided
    Assertions.assertThrows(Exception.class, () -> cryptoHamsterInstance.nameExists(HAMSTER_NAME));

    // deploy contract
    String contractId = cryptoHamsterInstance.deploy();
    log.info("contract id: {}", contractId);
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
    // currently needed for the values that are returned as Option
    Pattern pattern = Pattern.compile(".*\\[ *(.*) *\\].*");

    FungibleToken krautTokenInstance = new FungibleToken(config, null);

    String contractId = krautTokenInstance.deploy("kryptokrauts", BigInteger.valueOf(18), "KRAUT");
    log.info("contract id: {}", contractId);
    Assertions.assertEquals(KRAUT_TOKEN_TOTAL_SUPPLY, krautTokenInstance.total_supply());

    BaseKeyPair recipientKeyPair = keyPairService.generateBaseKeyPair();

    BigInteger tokensToSend =
        UnitConversionUtil.toAettos(new BigDecimal("1.337"), Unit.AE).toBigInteger();

    Object krautTokenMetaInfo = krautTokenInstance.meta_info();
    log.info(krautTokenMetaInfo.toString());

    Object ownerBalance = krautTokenInstance.balance(new Address(baseKeyPair.getPublicKey()));
    log.info(ownerBalance.toString());
    Matcher m = pattern.matcher(ownerBalance.toString());
    m.find();
    log.info(m.group(1));
    Assertions.assertEquals(KRAUT_TOKEN_TOTAL_SUPPLY.toString(), m.group(1));

    krautTokenInstance.transfer(new Address(recipientKeyPair.getPublicKey()), tokensToSend);

    Object recipientBalance =
        krautTokenInstance.balance(new Address(recipientKeyPair.getPublicKey()));
    log.info(recipientBalance.toString());
    m = pattern.matcher(recipientBalance.toString());
    m.find();
    Assertions.assertEquals(tokensToSend.toString(), m.group(1));

    log.info(krautTokenInstance.balances().toString());

    ownerBalance = krautTokenInstance.balance(new Address(baseKeyPair.getPublicKey()));
    log.info(ownerBalance.toString());
    m = pattern.matcher(ownerBalance.toString());
    m.find();
    Assertions.assertEquals(KRAUT_TOKEN_TOTAL_SUPPLY.subtract(tokensToSend).toString(), m.group(1));

    Object noKRAUTlerBalance =
        krautTokenInstance.balance(
            new Address(keyPairService.generateBaseKeyPair().getPublicKey()));
    log.info(noKRAUTlerBalance.toString());
    Assertions.assertEquals("None", noKRAUTlerBalance.toString());
  }
}
