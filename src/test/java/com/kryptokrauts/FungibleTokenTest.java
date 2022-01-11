package com.kryptokrauts;

import com.kryptokrauts.aeternity.sdk.domain.secret.KeyPair;
import com.kryptokrauts.contraect.generated.FungibleToken;
import com.kryptokrauts.contraect.generated.FungibleToken.Meta_info;
import com.kryptokrauts.contraect.generated.FungibleTokenInterface;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.javatuples.Pair;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class FungibleTokenTest extends BaseTest {

  private static final BigInteger KRAUT_TOKEN_TOTAL_SUPPLY =
      unitConversionService18Decimals.toSmallestUnit(new BigDecimal("21000000"));

  private static String contractId;

  @Test
  @Order(1)
  public void deploymentFailsWithNegativeSupply() {
    FungibleToken krautTokenInstance = new FungibleToken(config, null);
    try {
      krautTokenInstance.deploy(
          "failed shit", BigInteger.valueOf(0), "FAIL", Optional.of(BigInteger.valueOf(-1)));
      Assertions.fail("deployment should have failed ...");
    } catch (Exception e) {
      log.info("deployment failed as expected, msg: {}", e.getMessage());
    }
  }

  @Test
  @Order(2)
  public void deploymentSucceeds() {
    FungibleToken krautTokenInstance = new FungibleToken(config, null);
    Pair<String, String> deployment =
        krautTokenInstance.deploy(
            "kryptokrauts", BigInteger.valueOf(18), "KRAUT", Optional.of(KRAUT_TOKEN_TOTAL_SUPPLY));
    String txHash = deployment.getValue0();
    contractId = deployment.getValue1();
    log.info("tx-hash of deployment: {}", txHash);
    log.info("contract id: {}", contractId);

    // verify total token supply
    Assertions.assertEquals(KRAUT_TOKEN_TOTAL_SUPPLY, krautTokenInstance.total_supply());

    // get and verify Meta_info
    FungibleToken.Meta_info krautTokenMetaInfo = krautTokenInstance.meta_info();
    Assertions.assertEquals(
        new Meta_info("kryptokrauts", "KRAUT", BigInteger.valueOf(18)), krautTokenMetaInfo);

    // verify that owner owns all tokens
    BigInteger ownerBalance =
        krautTokenInstance.balance(new FungibleToken.Address(baseKeyPair.getAddress())).get();
    Assertions.assertEquals(KRAUT_TOKEN_TOTAL_SUPPLY, ownerBalance);
  }

  @Test
  @Order(3)
  public void transferTokens() {
    // initialize instance with previously deployed contract
    FungibleToken krautTokenInstance = new FungibleToken(config, contractId);
    BigInteger tokensToSendAettos =
        unitConversionService18Decimals.toSmallestUnit(new BigDecimal("1.337"));

    // send 1.337 KRAUT to new account
    KeyPair recipientAccount = keyPairService.generateKeyPair();
    krautTokenInstance.transfer(
        new FungibleToken.Address(recipientAccount.getAddress()), tokensToSendAettos);

    // verify new balance of recipient
    BigInteger recipientBalance =
        krautTokenInstance.balance(new FungibleToken.Address(recipientAccount.getAddress())).get();
    Assertions.assertEquals(tokensToSendAettos, recipientBalance);

    log.info(krautTokenInstance.balances().toString());

    // verify new balance of owner
    BigInteger ownerBalance =
        krautTokenInstance.balance(new FungibleToken.Address(baseKeyPair.getAddress())).get();
    log.info(ownerBalance.toString());
    Assertions.assertEquals(KRAUT_TOKEN_TOTAL_SUPPLY.subtract(tokensToSendAettos), ownerBalance);

    // verify that a fresh account doesn't own a token
    Optional<BigInteger> noKRAUTlerBalance =
        krautTokenInstance.balance(
            new FungibleToken.Address(keyPairService.generateKeyPair().getAddress()));
    log.info(noKRAUTlerBalance.toString());
    Assertions.assertEquals(Optional.empty().toString(), noKRAUTlerBalance.toString());
  }

  @Test
  @Order(4)
  public void initializeUsingInterface() {
    log.info("initializing kryptokrauts FungibleToken with contractId: {}", contractId);
    FungibleTokenInterface fungibleTokenInterface = new FungibleTokenInterface(config, contractId);
    BigInteger totalSupply = fungibleTokenInterface.total_supply();
    Assertions.assertEquals(KRAUT_TOKEN_TOTAL_SUPPLY, totalSupply);
    FungibleTokenInterface.Meta_info metaInfo = fungibleTokenInterface.meta_info();
    Assertions.assertEquals(
        new FungibleTokenInterface.Meta_info("kryptokrauts", "KRAUT", BigInteger.valueOf(18)),
        metaInfo);
  }
}
