package com.kryptokrauts;

import com.kryptokrauts.aeternity.sdk.domain.secret.KeyPair;
import com.kryptokrauts.aeternity.sdk.service.ServiceConfiguration;
import com.kryptokrauts.aeternity.sdk.service.account.domain.AccountResult;
import com.kryptokrauts.aeternity.sdk.service.delegation.DelegationService;
import com.kryptokrauts.aeternity.sdk.service.delegation.DelegationServiceFactory;
import com.kryptokrauts.aeternity.sdk.service.oracle.domain.OracleQueryResult;
import com.kryptokrauts.aeternity.sdk.service.oracle.domain.RegisteredOracleResult;
import com.kryptokrauts.aeternity.sdk.service.transaction.domain.PostTransactionResult;
import com.kryptokrauts.aeternity.sdk.service.transaction.type.model.SpendTransactionModel;
import com.kryptokrauts.contraect.generated.OracleDelegation;
import com.kryptokrauts.contraect.generated.OracleDelegation.Address;
import com.kryptokrauts.contraect.generated.OracleDelegation.ChainTTL;
import com.kryptokrauts.contraect.generated.OracleDelegation.ChainTTL.ChainTTLType;
import com.kryptokrauts.contraect.generated.OracleDelegation.Oracle;
import com.kryptokrauts.contraect.generated.OracleDelegation.Oracle_query;
import com.kryptokrauts.contraect.generated.OracleDelegation.Signature;
import java.math.BigInteger;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.crypto.CryptoException;
import org.javatuples.Pair;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class OracleDelegationTest extends BaseTest {

  private static OracleDelegation oracleDelegationInstance;

  private static Oracle oracle;

  private static BigInteger oracleTtl;

  private static KeyPair oracleKeyPair;

  private static DelegationService delegationService;

  @BeforeAll
  public static void deployAndFund() {
    oracleDelegationInstance = new OracleDelegation(config, null);
    // deploy contract
    Pair<String, String> deployment = oracleDelegationInstance.deploy();
    String txHash = deployment.getValue0();
    log.info("tx-hash of deployment: {}", txHash);
    log.info("contract id: {}", deployment.getValue1());
    Assertions.assertNotNull(oracleDelegationInstance.getContractId());

    // fund oracle keypair
    oracleKeyPair = keyPairService.generateKeyPair();
    AccountResult testAccount = aeternityService.accounts.blockingGetAccount();
    SpendTransactionModel spendTransactionModel =
        SpendTransactionModel.builder()
            .amount(new BigInteger("100000000"))
            .nonce(testAccount.getNonce().add(BigInteger.ONE))
            .recipient(oracleKeyPair.getAddress())
            .sender(config.getKeyPair().getAddress())
            .build();
    PostTransactionResult spendTxResult =
        aeternityService.transactions.blockingPostTransaction(spendTransactionModel);
    log.info("SpendTx result: {}", spendTxResult);

    // initialize delegation service with correct config for signature creation
    delegationService =
        new DelegationServiceFactory()
            .getService(
                ServiceConfiguration.configure()
                    .network(config.getNetwork())
                    .keyPair(oracleKeyPair)
                    .compile());
  }

  @Test
  @Order(1)
  public void createOracle() throws CryptoException {
    String signature =
        delegationService.createOracleDelegationSignature(oracleDelegationInstance.getContractId());

    Pair<String, Oracle> oracleRegisterTx =
        oracleDelegationInstance.register_oracle(
            new Address(oracleKeyPair.getAddress()),
            new Signature(signature),
            BigInteger.ONE,
            new ChainTTL(new BigInteger("800"), ChainTTLType.RelativeTTL),
            BigInteger.ZERO);
    log.info("tx-hash of signedRegisterOracle: {}", oracleRegisterTx.getValue0());
    oracle = oracleRegisterTx.getValue1();
    log.info("{}", oracle);
    RegisteredOracleResult registeredOracleResult =
        aeternityService.oracles.blockingGetRegisteredOracle(oracle.getOracle());
    log.info("{}", registeredOracleResult);
    oracleTtl = registeredOracleResult.getTtl();
  }

  @Test
  @Order(2)
  public void extendOracle() {
    String signature =
        delegationService.createOracleDelegationSignature(oracleDelegationInstance.getContractId());
    String oracleExtendTxHash =
        oracleDelegationInstance.extend_oracle(
            oracle,
            new Signature(signature),
            new ChainTTL(new BigInteger("800"), ChainTTLType.RelativeTTL),
            BigInteger.ZERO);
    log.info("tx-hash of signedExtendOracle: {}", oracleExtendTxHash);
    RegisteredOracleResult registeredOracleResult =
        aeternityService.oracles.blockingGetRegisteredOracle(oracle.getOracle());
    log.info("initial ttl: {}", oracleTtl);
    log.info("new ttl after extending it: {}", registeredOracleResult.getTtl());
    Assertions.assertEquals(
        oracleTtl.add(BigInteger.valueOf(800)), registeredOracleResult.getTtl());
  }

  @Test
  @Order(3)
  public void createAndRespondToQuery() {
    BigInteger queryFee = oracleDelegationInstance.query_fee(oracle);
    log.info("query fee: {}", queryFee);
    Pair<String, Oracle_query> createQueryTx =
        oracleDelegationInstance.create_query(
            oracle,
            "how is the wheather over there?",
            new ChainTTL(new BigInteger("100"), ChainTTLType.RelativeTTL),
            new ChainTTL(new BigInteger("100"), ChainTTLType.RelativeTTL),
            queryFee);
    Oracle_query query = createQueryTx.getValue1();
    log.info("oracle query id: {}", query.getOracle_query());
    String signature =
        delegationService.createOracleDelegationSignature(
            oracleDelegationInstance.getContractId(), query.getOracle_query());
    String signedRespondTxHash =
        oracleDelegationInstance.respond(oracle, query, new Signature(signature), "sunny =)");
    log.info("tx-hash of signedRespond: {}", signedRespondTxHash);
    OracleQueryResult oracleQueryResult =
        aeternityService.oracles.blockingGetOracleQuery(
            oracleKeyPair.getOracleAddress(), query.getOracle_query());
    /** @TODO: figure out why encoding returns ! */
    Assertions.assertEquals("!sunny =)", oracleQueryResult.getResponse());
  }
}
