package com.kryptokrauts;

import com.kryptokrauts.contraect.generated.CryptoHamster;
import com.kryptokrauts.contraect.generated.CryptoHamster.Hash;
import lombok.extern.slf4j.Slf4j;
import org.javatuples.Pair;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@Slf4j
public class CryptoHamsterTest extends BaseTest {

  private static final String HAMSTER_NAME = "kryptokrauts";

  private static final String HAMSTER_DNA =
      "#9227ef8e61954f009822f1d1b9b4077f95ab81be96f21b37937d1f85effe261b";

  private static String contractId;

  @BeforeAll
  public static void deploy() {
    CryptoHamster cryptoHamsterInstance = new CryptoHamster(config, null);

    // expecting error as contract doesn't exist and no contractId provided
    Assertions.assertThrows(Exception.class, () -> cryptoHamsterInstance.nameExists(HAMSTER_NAME));

    // deploy contract
    Pair<String, String> deployment = cryptoHamsterInstance.deploy();
    String txHash = deployment.getValue0();
    contractId = deployment.getValue1();
    log.info("tx-hash of deployment: {}", txHash);
    log.info("contract id: {}", contractId);
    Assertions.assertNotNull(contractId);
  }

  @Test
  public void createAndVerifyHamster() {
    // initialize instance with previously deployed contract
    CryptoHamster cryptoHamsterInstance = new CryptoHamster(config, contractId);

    // expect hamster to not exist
    Assertions.assertFalse(cryptoHamsterInstance.nameExists(HAMSTER_NAME));

    // create hamster
    cryptoHamsterInstance.createHamster(HAMSTER_NAME);
    Assertions.assertTrue(cryptoHamsterInstance.nameExists(HAMSTER_NAME));

    // verify hamster dna
    Hash hamsterDna = cryptoHamsterInstance.getHamsterDNA(HAMSTER_NAME);
    log.info("hamster dna: {}", hamsterDna);
    Assertions.assertEquals(HAMSTER_DNA, hamsterDna.getHash());
  }
}
