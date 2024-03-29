package com.kryptokrauts;

import com.kryptokrauts.aeternity.sdk.constants.Network;
import com.kryptokrauts.aeternity.sdk.constants.VirtualMachine;
import com.kryptokrauts.aeternity.sdk.domain.secret.KeyPair;
import com.kryptokrauts.aeternity.sdk.service.aeternity.AeternityServiceConfiguration;
import com.kryptokrauts.aeternity.sdk.service.aeternity.AeternityServiceFactory;
import com.kryptokrauts.aeternity.sdk.service.aeternity.impl.AeternityService;
import com.kryptokrauts.aeternity.sdk.service.keypair.KeyPairService;
import com.kryptokrauts.aeternity.sdk.service.keypair.KeyPairServiceFactory;
import com.kryptokrauts.aeternity.sdk.service.unit.UnitConversionService;
import com.kryptokrauts.aeternity.sdk.service.unit.impl.DefaultUnitConversionServiceImpl;
import org.junit.jupiter.api.BeforeAll;

public class BaseTest {

  private static final String PRIVATE_KEY =
      "79816BBF860B95600DDFABF9D81FEE81BDB30BE823B17D80B9E48BE0A7015ADF";

  protected static UnitConversionService unitConversionService18Decimals =
      new DefaultUnitConversionServiceImpl();

  protected static KeyPair baseKeyPair;

  protected static AeternityServiceConfiguration config;

  protected static AeternityService aeternityService;

  protected static KeyPairService keyPairService = new KeyPairServiceFactory().getService();

  @BeforeAll
  public static void init() {
    KeyPairService keyPairService = new KeyPairServiceFactory().getService();
    baseKeyPair = keyPairService.recoverKeyPair(PRIVATE_KEY);
    config =
        AeternityServiceConfiguration.configure()
            .compilerBaseUrl("http://localhost:3080")
            .baseUrl("http://localhost")
            .network(Network.DEVNET)
            .mdwBaseUrl("http://localhost:4000")
            .keyPair(baseKeyPair)
            .targetVM(VirtualMachine.FATE)
            .millisBetweenTrailsToWaitForConfirmation(100l)
            .compile();
    aeternityService = new AeternityServiceFactory().getService(config);
  }
}
