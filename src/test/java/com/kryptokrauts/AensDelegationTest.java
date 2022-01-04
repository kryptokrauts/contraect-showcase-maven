package com.kryptokrauts;

import com.kryptokrauts.aeternity.sdk.constants.AENS;
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
import com.kryptokrauts.contraect.generated.AensDelegation.ChainTTL;
import com.kryptokrauts.contraect.generated.AensDelegation.ChainTTL.ChainTTLType;
import com.kryptokrauts.contraect.generated.AensDelegation.Hash;
import com.kryptokrauts.contraect.generated.AensDelegation.Pointee;
import com.kryptokrauts.contraect.generated.AensDelegation.Pointee.PointeeType;
import com.kryptokrauts.contraect.generated.AensDelegation.Signature;
import java.math.BigInteger;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import lombok.extern.slf4j.Slf4j;
import org.javatuples.Pair;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AensDelegationTest extends BaseTest {

  private static Random random = new Random();

  private static AensDelegation aensDelegationInstance;

  private static DelegationService nameOwnerDelegationService;

  private static DelegationService newOwnerDelegationService;

  private static UnitConversionService unitConversionService =
      new DefaultUnitConversionServiceImpl();

  private static String delegationTestName = "delegationTestName" + random.nextInt() + ".chain";

  private static BigInteger salt;

  private static Hash commitmentHash;

  private static KeyPair nameOwnerKeyPair, newOwnerKeyPair;
  private static Address nameOwnerAddress, newOwnerAddress;

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

    nameOwnerKeyPair = keyPairService.generateKeyPair();
    nameOwnerAddress = new Address(nameOwnerKeyPair.getAddress());
    log.info(nameOwnerKeyPair.toString());
    newOwnerKeyPair = keyPairService.generateKeyPair();
    newOwnerAddress = new Address(newOwnerKeyPair.getAddress());
    log.info(newOwnerAddress.toString());

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

    // the new owner mustn't have zero balance for a successful revocation
    // see https://github.com/aeternity/aeternity/issues/3674
    spendTransactionModel =
        SpendTransactionModel.builder()
            .amount(unitConversionService.toSmallestUnit("1")) // 1 AE
            .nonce(testAccount.getNonce().add(BigInteger.valueOf(2)))
            .recipient(newOwnerKeyPair.getAddress())
            .sender(config.getKeyPair().getAddress())
            .build();
    spendTxResult = aeternityService.transactions.blockingPostTransaction(spendTransactionModel);
    log.info("SpendTx result: {}", spendTxResult);

    // initialize delegation service with correct config
    nameOwnerDelegationService =
        new DelegationServiceFactory()
            .getService(
                ServiceConfiguration.configure()
                    .network(config.getNetwork())
                    .keyPair(nameOwnerKeyPair)
                    .compile());

    newOwnerDelegationService =
        new DelegationServiceFactory()
            .getService(
                ServiceConfiguration.configure()
                    .network(config.getNetwork())
                    .keyPair(newOwnerKeyPair)
                    .compile());
  }

  @Test
  @Order(1)
  public void preClaimName() {
    salt = CryptoUtils.generateNamespaceSalt();
    commitmentHash =
        new Hash(nameOwnerDelegationService.getAensCommitmentHash(delegationTestName, salt));
    Signature preClaimSignature =
        new Signature(
            nameOwnerDelegationService.createAensDelegationSignature(
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
            nameOwnerDelegationService.createAensDelegationSignature(
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
            Optional.of(
                new ChainTTL(new BigInteger(newTtlHeight.toString()), ChainTTLType.FixedTTL)));
    log.info("extend tx-hash: {}", txHash);
    nameEntryResult = aeternityService.names.blockingGetNameId(delegationTestName);
    BigInteger newTtl = nameEntryResult.getTtl();
    Assertions.assertNotEquals(oldTtl, newTtl);
    Assertions.assertEquals(newTtlHeight, newTtl);
  }

  @Test
  @Order(4)
  public void addAndGetPointers() {
    aensDelegationInstance.add_key(
        nameOwnerAddress,
        delegationTestName,
        AENS.POINTER_KEY_ACCOUNT,
        new Pointee(nameOwnerAddress, PointeeType.AccountPt),
        aensDelegationSignature);
    NameEntryResult nameEntryResult = aeternityService.names.blockingGetNameId(delegationTestName);
    Assertions.assertEquals(1, nameEntryResult.getPointers().size());
    Assertions.assertEquals(
        nameOwnerAddress.getAddress(), nameEntryResult.getAccountPointer().get());
    aensDelegationInstance.get_name(delegationTestName);
    Map<String, Pointee> pointers = aensDelegationInstance.get_pointers(delegationTestName);
    Assertions.assertEquals(1, pointers.size());
    Assertions.assertEquals(nameOwnerAddress, pointers.get(AENS.POINTER_KEY_ACCOUNT).getAddress());
  }

  @Test
  @Order(5)
  public void transferName() {
    NameEntryResult nameEntryResult = aeternityService.names.blockingGetNameId(delegationTestName);
    Assertions.assertEquals(nameOwnerKeyPair.getAddress(), nameEntryResult.getOwner());
    String txHash =
        aensDelegationInstance.transfer(
            nameOwnerAddress, newOwnerAddress, delegationTestName, aensDelegationSignature);
    log.info("transfer tx-hash: {}", txHash);
    nameEntryResult = aeternityService.names.blockingGetNameId(delegationTestName);
    Assertions.assertEquals(newOwnerKeyPair.getAddress(), nameEntryResult.getOwner());
  }

  @Test
  @Order(6)
  public void revokeName() {
    Signature newOwnerDelegationSignature =
        new Signature(
            newOwnerDelegationService.createAensDelegationSignature(
                aensDelegationInstance.getContractId(), delegationTestName));
    String txHash =
        aensDelegationInstance.revoke(
            newOwnerAddress, delegationTestName, newOwnerDelegationSignature);
    log.info("revoke tx-hash: {}", txHash);
    NameEntryResult nameEntryResult = aeternityService.names.blockingGetNameId(delegationTestName);
    Assertions.assertTrue(nameEntryResult.getRootErrorMessage().contains("Name revoked"));
  }
}
