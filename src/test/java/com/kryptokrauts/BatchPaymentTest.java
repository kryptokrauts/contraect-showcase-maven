package com.kryptokrauts;

import com.kryptokrauts.aeternity.sdk.domain.secret.impl.BaseKeyPair;
import com.kryptokrauts.aeternity.sdk.service.account.domain.AccountResult;
import com.kryptokrauts.aeternity.sdk.service.keypair.KeyPairServiceFactory;
import com.kryptokrauts.contraect.generated.BatchPayment;
import com.kryptokrauts.contraect.generated.BatchPayment.Address;
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
public class BatchPaymentTest extends BaseTest {

  private static String contractId;

  private static BaseKeyPair recipient1;
  private static BaseKeyPair recipient2;
  private static BaseKeyPair recipient3;

  @BeforeAll
  public static void deploy() {
    BatchPayment batchPaymentInstance = new BatchPayment(config, null);
    // deploy contract
    Pair<String, String> deployment = batchPaymentInstance.deploy();
    String txHash = deployment.getValue0();
    contractId = deployment.getValue1();
    log.info("tx-hash of deployment: {}", txHash);
    log.info("contract id: {}", contractId);
    Assertions.assertNotNull(contractId);
  }

  @Test
  public void proceedBatchPayment() {
    // initialize instance with previously deployed contract
    BatchPayment batchPaymentInstance = new BatchPayment(config, contractId);

    recipient1 = new KeyPairServiceFactory().getService().generateBaseKeyPair();
    BigInteger recipient1Amount = unitConversionService18Decimals.toSmallestUnit("3.6465");
    recipient2 = new KeyPairServiceFactory().getService().generateBaseKeyPair();
    BigInteger recipient2Amount = unitConversionService18Decimals.toSmallestUnit("8.23424");
    recipient3 = new KeyPairServiceFactory().getService().generateBaseKeyPair();
    BigInteger recipient3Amount = unitConversionService18Decimals.toSmallestUnit("17.111");

    Map<Address, BigInteger> recipientMap = new HashMap<>();
    recipientMap.put(new Address(recipient1.getPublicKey()), recipient1Amount);
    recipientMap.put(new Address(recipient2.getPublicKey()), recipient2Amount);
    recipientMap.put(new Address(recipient3.getPublicKey()), recipient3Amount);

    batchPaymentInstance.proceedBatchPayment(recipientMap, getTotalAmount(recipientMap));

    // verify accounts got the correct amount of AE
    AccountResult accountResult1 =
        aeternityService.accounts.blockingGetAccount(Optional.of(recipient1.getPublicKey()));
    AccountResult accountResult2 =
        aeternityService.accounts.blockingGetAccount(Optional.of(recipient2.getPublicKey()));
    AccountResult accountResult3 =
        aeternityService.accounts.blockingGetAccount(Optional.of(recipient3.getPublicKey()));
    Assertions.assertEquals(recipient1Amount, accountResult1.getBalance());
    Assertions.assertEquals(recipient2Amount, accountResult2.getBalance());
    Assertions.assertEquals(recipient3Amount, accountResult3.getBalance());
  }

  private BigInteger getTotalAmount(Map<Address, BigInteger> recipientMap) {
    return recipientMap.values().stream().reduce(BigInteger.ZERO, BigInteger::add);
  }
}
