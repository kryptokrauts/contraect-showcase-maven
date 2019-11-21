package com.kryptokrauts;

import com.kryptokrauts.aeternity.sdk.constants.Network;
import com.kryptokrauts.aeternity.sdk.constants.VirtualMachine;
import com.kryptokrauts.aeternity.sdk.domain.secret.impl.BaseKeyPair;
import com.kryptokrauts.aeternity.sdk.service.aeternity.AeternityServiceConfiguration;
import com.kryptokrauts.aeternity.sdk.service.keypair.KeyPairService;
import com.kryptokrauts.aeternity.sdk.service.keypair.KeyPairServiceFactory;
import com.kryptokrauts.generated.contraect.CryptoHamster;
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

  private static BaseKeyPair baseKeyPair;

  private static AeternityServiceConfiguration config;

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
}
