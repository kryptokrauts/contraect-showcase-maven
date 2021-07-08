package com.kryptokrauts;

import com.kryptokrauts.aeternity.sdk.domain.secret.KeyPair;
import com.kryptokrauts.aeternity.sdk.service.account.domain.AccountResult;
import com.kryptokrauts.aeternity.sdk.service.keypair.KeyPairServiceFactory;
import com.kryptokrauts.contraect.generated.PaymentSplitter;
import com.kryptokrauts.contraect.generated.PaymentSplitter.Address;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.javatuples.Pair;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PaymentSplitterTest extends BaseTest {

  private static String contractId;

  private static KeyPair initialRecipient1;
  private static KeyPair initialRecipient2;
  private static KeyPair initialRecipient3;

  @BeforeAll
  public static void deploy() {
    PaymentSplitter paymentSplitterInstance = new PaymentSplitter(config, null);
    initialRecipient1 = new KeyPairServiceFactory().getService().generateKeyPair();
    initialRecipient2 = new KeyPairServiceFactory().getService().generateKeyPair();
    initialRecipient3 = new KeyPairServiceFactory().getService().generateKeyPair();
    Map<Address, BigInteger> recipientConditions = new HashMap<>();
    // should receive 60%
    recipientConditions.put(new Address(initialRecipient1.getAddress()), BigInteger.valueOf(60));
    // should receive 30%
    recipientConditions.put(new Address(initialRecipient2.getAddress()), BigInteger.valueOf(30));
    // should receive 10%
    recipientConditions.put(new Address(initialRecipient3.getAddress()), BigInteger.valueOf(10));
    Pair<String, String> deployment = paymentSplitterInstance.deploy(recipientConditions);
    String txHash = deployment.getValue0();
    contractId = deployment.getValue1();
    log.info("tx-hash of deployment: {}", txHash);
    log.info("contract id: {}", contractId);
  }

  @Test
  public void payAndSplitSucceeds() {
    // initialize instance with previously deployed contract
    PaymentSplitter paymentSplitterInstance = new PaymentSplitter(config, contractId);

    // payAndSplit 10 AE
    BigInteger amountAettos = unitConversionService18Decimals.toSmallestUnit("10");
    paymentSplitterInstance.payAndSplit(amountAettos);

    // verify a total amount of 10 AE has been splitted
    BigInteger totalAmountSplittedAettos = paymentSplitterInstance.getTotalAmountSplitted();
    Assertions.assertEquals(amountAettos, totalAmountSplittedAettos);

    // verify accounts got the correct amount of AE
    AccountResult accountResult1 =
        aeternityService.accounts.blockingGetAccount(initialRecipient1.getAddress());
    AccountResult accountResult2 =
        aeternityService.accounts.blockingGetAccount(initialRecipient2.getAddress());
    AccountResult accountResult3 =
        aeternityService.accounts.blockingGetAccount(initialRecipient3.getAddress());
    Assertions.assertEquals(
        unitConversionService18Decimals.toSmallestUnit("6"), accountResult1.getBalance());
    Assertions.assertEquals(
        unitConversionService18Decimals.toSmallestUnit("3"), accountResult2.getBalance());
    Assertions.assertEquals(
        unitConversionService18Decimals.toSmallestUnit("1"), accountResult3.getBalance());
  }
}
