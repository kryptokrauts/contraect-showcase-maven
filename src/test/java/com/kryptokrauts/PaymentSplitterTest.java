package com.kryptokrauts;

import com.kryptokrauts.aeternity.sdk.domain.secret.impl.BaseKeyPair;
import com.kryptokrauts.aeternity.sdk.service.account.domain.AccountResult;
import com.kryptokrauts.aeternity.sdk.service.keypair.KeyPairServiceFactory;
import com.kryptokrauts.contraect.generated.PaymentSplitter;
import com.kryptokrauts.contraect.generated.PaymentSplitter.Address;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.javatuples.Pair;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@Slf4j
public class PaymentSplitterTest extends BaseTest {

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
    String txHash = deployment.getValue0();
    String contractId = deployment.getValue1();
    log.info("tx-hash of deployment: {}", txHash);
    log.info("contract id: {}", contractId);

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
