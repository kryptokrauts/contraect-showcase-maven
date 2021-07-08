package com.kryptokrauts;

import com.kryptokrauts.aeternity.sdk.constants.AENS;
import com.kryptokrauts.aeternity.sdk.constants.Network;
import com.kryptokrauts.aeternity.sdk.domain.secret.KeyPair;
import com.kryptokrauts.aeternity.sdk.service.ServiceConfiguration;
import com.kryptokrauts.aeternity.sdk.service.account.domain.AccountResult;
import com.kryptokrauts.aeternity.sdk.service.delegation.DelegationService;
import com.kryptokrauts.aeternity.sdk.service.delegation.DelegationServiceFactory;
import com.kryptokrauts.aeternity.sdk.service.name.domain.NameEntryResult;
import com.kryptokrauts.aeternity.sdk.service.transaction.domain.PostTransactionResult;
import com.kryptokrauts.aeternity.sdk.service.transaction.type.model.SpendTransactionModel;
import com.kryptokrauts.aeternity.sdk.service.unit.UnitConversionService;
import com.kryptokrauts.aeternity.sdk.service.unit.impl.DefaultUnitConversionServiceImpl;
import com.kryptokrauts.aeternity.sdk.util.CryptoUtils;
import com.kryptokrauts.contraect.generated.AensDelegation;
import com.kryptokrauts.contraect.generated.AensDelegation.Address;
import com.kryptokrauts.contraect.generated.AensDelegation.Hash;
import com.kryptokrauts.contraect.generated.AensDelegation.Signature;
import java.math.BigInteger;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import lombok.extern.slf4j.Slf4j;
import org.javatuples.Pair;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AensDelegationTest extends BaseTest {

  private static Random random = new Random();

  private static AensDelegation aensDelegationInstance;

  private static KeyPair nameOwnerKeyPair;

  private static DelegationService delegationService;

  private static UnitConversionService unitConversionService =
      new DefaultUnitConversionServiceImpl();

  private static String delegationTestName = "delegationTestName" + random.nextInt() + ".chain";

  private static BigInteger salt;

  private static Hash commitmentHash;

  private static Address nameOwnerAddress;

  private static Signature aensDelegationSignature;

  @BeforeAll
  public static void deployAndFund() {
    aensDelegationInstance = new AensDelegation(config, null);
    // deploy contract
    Pair<String, String> deployment = aensDelegationInstance.deploy();
    String txHash = deployment.getValue0();
    log.info("tx-hash of deployment: {}", txHash);
    log.info("contract id: {}", deployment.getValue1());
    Assertions.assertNotNull(aensDelegationInstance.getContractId());

    // fund name owner keypair
    nameOwnerKeyPair = keyPairService.generateKeyPair();
    // wrap into Address obj used for contract calls
    nameOwnerAddress = new Address(nameOwnerKeyPair.getAddress());
    log.info(nameOwnerKeyPair.toString());

    AccountResult testAccount = aeternityService.accounts.blockingGetAccount();
    SpendTransactionModel spendTransactionModel =
        SpendTransactionModel.builder()
            .amount(unitConversionService.toSmallestUnit("500")) // 500 AE
            .nonce(testAccount.getNonce().add(BigInteger.ONE))
            .recipient(nameOwnerKeyPair.getAddress())
            .sender(config.getKeyPair().getAddress())
            .build();
    PostTransactionResult spendTxResult =
        aeternityService.transactions.blockingPostTransaction(spendTransactionModel);
    log.info("SpendTx result: {}", spendTxResult);

    // initialize delegation service with correct config
    delegationService =
        new DelegationServiceFactory()
            .getService(
                ServiceConfiguration.configure()
                    .network(Network.DEVNET)
                    .keyPair(nameOwnerKeyPair)
                    .compile());
  }

  @Test
  @Order(1)
  public void preClaimName() {
    salt = CryptoUtils.generateNamespaceSalt();
    commitmentHash = new Hash(delegationService.getAensCommitmentHash(delegationTestName, salt));
    Signature preClaimSignature =
        new Signature(
            delegationService.createAensDelegationSignature(
                aensDelegationInstance.getContractId()));
    log.info("pre-claim delegation signature: {}", preClaimSignature.getSignature());
    log.info("pre-claim name {} with salt {}", delegationTestName, salt);
    String txHash =
        aensDelegationInstance.pre_claim(nameOwnerAddress, commitmentHash, preClaimSignature);
    log.info("pre-claim tx-hash: {}", txHash);
  }

  @Test
  @Order(2)
  public void claimName() {
    aensDelegationSignature =
        new Signature(
            delegationService.createAensDelegationSignature(
                aensDelegationInstance.getContractId(), delegationTestName));
    log.info("aens delegation signature: {}", aensDelegationSignature.getSignature());
    BigInteger nameFee = AENS.getInitialNameFee(delegationTestName);
    log.info("nameFee: {}", nameFee);
    log.info("claim name {} with salt {}", delegationTestName, salt);
    String txHash =
        aensDelegationInstance.claim(
            nameOwnerAddress, delegationTestName, salt, nameFee, aensDelegationSignature);
    log.info("claim tx-hash: {}", txHash);
  }

  @Test
  @Order(3)
  public void extendName() {
    NameEntryResult nameEntryResult = aeternityService.names.blockingGetNameId(delegationTestName);
    BigInteger oldTtl = nameEntryResult.getTtl();
    BigInteger currentHeight = aeternityService.info.blockingGetCurrentKeyBlock().getHeight();
    BigInteger newTtlHeight = currentHeight.add(BigInteger.valueOf(500));
    String txHash =
        aensDelegationInstance.extend(
            nameOwnerAddress,
            delegationTestName,
            aensDelegationSignature,
            Optional.of("FixedTTL(" + newTtlHeight.toString() + ")"));
    log.info("extend tx-hash: {}", txHash);
    nameEntryResult = aeternityService.names.blockingGetNameId(delegationTestName);
    BigInteger newTtl = nameEntryResult.getTtl();
    Assertions.assertNotEquals(oldTtl, newTtl);
    Assertions.assertEquals(newTtlHeight, newTtl);
  }

  @Disabled // currently not working =>
  // https://github.com/kryptokrauts/contraect-maven-plugin/issues/65
  @Test
  @Order(4)
  public void addAndGetPointers() {
    aensDelegationInstance.add_key(
        nameOwnerAddress,
        delegationTestName,
        AENS.POINTER_KEY_ACCOUNT,
        nameOwnerAddress.getAddress(),
        aensDelegationSignature);
    NameEntryResult nameEntryResult = aeternityService.names.blockingGetNameId(delegationTestName);
    Assertions.assertEquals(1, nameEntryResult.getPointers().size());
    Assertions.assertEquals(
        nameOwnerAddress.getAddress(), nameEntryResult.getAccountPointer().get());
    Map<String, Object> pointers = aensDelegationInstance.get_pointers(delegationTestName);
    Assertions.assertEquals(1, pointers.size());
    Assertions.assertEquals(nameOwnerAddress.getAddress(), pointers.get(AENS.POINTER_KEY_ACCOUNT));
  }

  @Test
  @Order(5)
  public void transferName() {
    NameEntryResult nameEntryResult = aeternityService.names.blockingGetNameId(delegationTestName);
    Assertions.assertEquals(nameOwnerKeyPair.getAddress(), nameEntryResult.getOwner());
    KeyPair newOwnerKeyPair = keyPairService.generateKeyPair();
    Address newOwnerAddress = new Address(newOwnerKeyPair.getAddress());
    String txHash =
        aensDelegationInstance.transfer(
            nameOwnerAddress, newOwnerAddress, delegationTestName, aensDelegationSignature);
    log.info("transfer tx-hash: {}", txHash);
    nameEntryResult = aeternityService.names.blockingGetNameId(delegationTestName);
    Assertions.assertEquals(newOwnerKeyPair.getAddress(), nameEntryResult.getOwner());
  }
}
